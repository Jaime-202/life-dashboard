package com.lifedashboard.data.remote.food

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.lifedashboard.BuildConfig
import com.lifedashboard.data.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FoodAiService"

data class DetectedFoodItem(
    val name: String,
    val grams: Float,
    val calories: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val confidenceNotes: String = ""
)

sealed class FoodAiResult {
    data class Success(val items: List<DetectedFoodItem>, val explanation: String) : FoodAiResult()
    data class Error(val message: String) : FoodAiResult()
}

@Singleton
class FoodAiService @Inject constructor(
    private val userPreferences: UserPreferencesRepository
) {

    /**
     * Analiza una fotografía de comida junto a una descripción opcional del usuario
     * para identificar platos, estimar gramos y desglosar macronutrientes.
     */
    suspend fun analyzeFoodImage(
        bitmap: Bitmap,
        userDescription: String
    ): FoodAiResult = withContext(Dispatchers.IO) {
        val userGoals = userPreferences.userGoals.first()
        val apiKey = userGoals.geminiApiKey.ifBlank {
            BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() && !it.contains("PEGA_AQUI") } ?: ""
        }

        // Si no hay API key pero hay descripción, usamos el motor NLP local avanzado
        if (apiKey.isBlank()) {
            if (userDescription.isNotBlank()) {
                val fallbackItems = parseFoodsFromTextLocally(userDescription)
                return@withContext FoodAiResult.Success(
                    items = fallbackItems,
                    explanation = "Análisis generado a partir de tu descripción: \"$userDescription\"."
                )
            }
            return@withContext FoodAiResult.Error(
                "Para escanear platos por foto con IA visual, añade tu clave gratuita de Google Gemini en Ajustes > API Key. También puedes usar el botón 'Texto IA' para escribir lo que comiste."
            )
        }

        try {
            // Escalar a 768px máx para envío rápido y optimización de memoria
            val scaledBitmap = scaleBitmapIfNeeded(bitmap, maxDimension = 768)
            val prompt = buildVisionPrompt(userDescription)

            // Estrategia de modelos: Gemini 2.0 Flash con fallback a Gemini 1.5 Flash
            val modelNames = listOf("gemini-2.0-flash", "gemini-1.5-flash")
            var responseText = ""

            for (modelName in modelNames) {
                try {
                    val model = GenerativeModel(
                        modelName = modelName,
                        apiKey = apiKey,
                        generationConfig = generationConfig {
                            temperature = 0.15f
                            maxOutputTokens = 1000
                            responseMimeType = "application/json"
                        }
                    )
                    val inputContent = content {
                        image(scaledBitmap)
                        text(prompt)
                    }
                    val response = model.generateContent(inputContent)
                    val text = response.text?.trim() ?: ""
                    if (text.isNotBlank()) {
                        responseText = text
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Fallo con modelo $modelName: ${e.message}")
                }
            }

            if (responseText.isNotBlank()) {
                val items = parseJsonItems(responseText)
                if (items.isNotEmpty()) {
                    return@withContext FoodAiResult.Success(
                        items = items,
                        explanation = "Alimentos y macros detectados por Gemini Vision."
                    )
                }
            }

            // Fallback a análisis local si la IA devolvió respuesta vacía o formato inválido
            if (userDescription.isNotBlank()) {
                return@withContext FoodAiResult.Success(
                    items = parseFoodsFromTextLocally(userDescription),
                    explanation = "Se utilizó tu descripción para calcular los alimentos del plato."
                )
            }

            FoodAiResult.Error("No se pudieron identificar con certeza los alimentos en la foto. Prueba a añadir una breve descripción (ej: 'arroz con pollo y ensalada').")
        } catch (e: Exception) {
            Log.e(TAG, "Error analizando comida con Gemini", e)
            if (userDescription.isNotBlank()) {
                FoodAiResult.Success(
                    items = parseFoodsFromTextLocally(userDescription),
                    explanation = "Calculado a partir de tu descripción: \"$userDescription\"."
                )
            } else {
                FoodAiResult.Error("Error de conexión con la IA: ${e.localizedMessage ?: "Verifica tu conexión y clave de API"}")
            }
        }
    }

    private fun buildVisionPrompt(userDescription: String): String {
        val extra = if (userDescription.isNotBlank()) {
            "\nINFORMACIÓN ADICIONAL DEL USUARIO: \"$userDescription\". Prioriza estos ingredientes y guíate por ellos para calcular los gramos exactos.\n"
        } else ""

        return """
            Eres un nutricionista experto y especialista en reconocimiento visual de platos de comida española y mediterránea.
            Analiza cuidadosamente la imagen adjunta.$extra
            
            Tu objetivo:
            1. Desglosar individualmente cada componente o alimento presente en el plato.
            2. Estimar con precisión el peso en gramos (o ml) de cada ingrediente.
            3. Calcular con rigor: calorías (kcal), proteínas (g), carbohidratos (g) y grasas (g) reales de cada uno.
            
            Responde ÚNICAMENTE con un array JSON válido con la siguiente estructura:
            [
              {
                "name": "Pechuga de pollo a la plancha",
                "grams": 160.0,
                "calories": 264.0,
                "protein": 49.6,
                "carbs": 0.0,
                "fat": 5.8
              }
            ]
        """.trimIndent()
    }

    /**
     * Analiza una descripción en texto libre (sin foto) de lo que comió el usuario
     * y genera automáticamente los alimentos, porciones y macronutrientes.
     */
    suspend fun analyzeFoodText(userDescription: String): FoodAiResult = withContext(Dispatchers.IO) {
        val trimmed = userDescription.trim()
        if (trimmed.isBlank()) {
            return@withContext FoodAiResult.Error("Por favor, escribe lo que has comido.")
        }

        val userGoals = userPreferences.userGoals.first()
        val apiKey = userGoals.geminiApiKey.ifBlank {
            BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() && !it.contains("PEGA_AQUI") } ?: ""
        }

        // Si no hay API key, usamos directamente nuestro motor local inteligente
        if (apiKey.isBlank()) {
            val localItems = parseFoodsFromTextLocally(trimmed)
            return@withContext FoodAiResult.Success(
                items = localItems,
                explanation = "Alimentos y porciones calculados a partir de tu texto."
            )
        }

        try {
            val prompt = """
                Eres un nutricionista experto en gastronomía española y cálculo de macronutrientes.
                El usuario ha registrado en lenguaje natural lo que ha comido: "$trimmed"
                
                Tu objetivo:
                1. Extraer e identificar individualmente cada alimento, ingrediente o bebida mencionado.
                2. Si el usuario indicó cantidades (ej: "2 huevos", "100g de pan", "un vaso de leche", "media pizza"), calcula el peso exacto.
                3. Si no indicó cantidad, asume la ración estándar española habitual para adultos.
                4. Calcula con precisión médica calorías (kcal), proteínas (g), carbohidratos (g) y grasas (g).
                
                Responde ÚNICAMENTE con un array JSON válido:
                [
                  {
                    "name": "Tortilla de patatas",
                    "grams": 180.0,
                    "calories": 277.0,
                    "protein": 9.9,
                    "carbs": 24.3,
                    "fat": 15.8
                  }
                ]
            """.trimIndent()

            val modelNames = listOf("gemini-2.0-flash", "gemini-1.5-flash")
            var responseText = ""

            for (modelName in modelNames) {
                try {
                    val model = GenerativeModel(
                        modelName = modelName,
                        apiKey = apiKey,
                        generationConfig = generationConfig {
                            temperature = 0.15f
                            maxOutputTokens = 1000
                            responseMimeType = "application/json"
                        }
                    )
                    val response = model.generateContent(prompt)
                    val text = response.text?.trim() ?: ""
                    if (text.isNotBlank()) {
                        responseText = text
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error en analyzeFoodText con $modelName: ${e.message}")
                }
            }

            if (responseText.isNotBlank()) {
                val items = parseJsonItems(responseText)
                if (items.isNotEmpty()) {
                    return@withContext FoodAiResult.Success(
                        items = items,
                        explanation = "Alimentos y macros calculados por Gemini AI."
                    )
                }
            }

            // Fallback a motor local
            FoodAiResult.Success(
                items = parseFoodsFromTextLocally(trimmed),
                explanation = "Alimentos calculados a partir de tu descripción: \"$trimmed\"."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error analizando texto con Gemini", e)
            FoodAiResult.Success(
                items = parseFoodsFromTextLocally(trimmed),
                explanation = "Calculado a partir de tu texto."
            )
        }
    }

    /**
     * Estima e infiere con IA (Gemini o motor local) los valores nutricionales por 100g y porción
     * de cualquier alimento o producto de supermercado buscado por el usuario.
     */
    suspend fun estimateFoodCatalogItem(query: String, category: FoodSourceCategory): FoodCatalogItem? = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext null

        val brandName = when (category) {
            FoodSourceCategory.MERCADONA -> "Mercadona / Hacendado"
            FoodSourceCategory.CARREFOUR -> "Carrefour"
            FoodSourceCategory.DIA -> "Día"
            FoodSourceCategory.ALCAMPO -> "Alcampo"
            FoodSourceCategory.SUPERCOR -> "El Corte Inglés"
            FoodSourceCategory.BASICOS -> "Básico"
            FoodSourceCategory.TODOS -> "Supermercado"
        }

        val userGoals = userPreferences.userGoals.first()
        val apiKey = userGoals.geminiApiKey.ifBlank {
            BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() && !it.contains("PEGA_AQUI") } ?: ""
        }

        if (apiKey.isNotBlank()) {
            try {
                val prompt = """
                    Eres una base de datos nutricional de alimentos españoles y supermercados ($brandName).
                    El usuario busca el alimento o producto: "$trimmed".
                    Devuelve ÚNICAMENTE un objeto JSON con los valores nutricionales exactos por 100 gramos y la ración recomendada:
                    {
                      "name": "Nombre claro y formal del alimento en español",
                      "brand": "$brandName",
                      "caloriesPer100g": 150.0,
                      "proteinPer100g": 12.0,
                      "carbsPer100g": 18.0,
                      "fatPer100g": 4.5,
                      "servingSizeGrams": 150.0,
                      "servingDescription": "1 porción (~150g)"
                    }
                """.trimIndent()

                val model = GenerativeModel(
                    modelName = "gemini-2.0-flash",
                    apiKey = apiKey,
                    generationConfig = generationConfig {
                        temperature = 0.1f
                        maxOutputTokens = 300
                        responseMimeType = "application/json"
                    }
                )
                val resp = model.generateContent(prompt)
                val txt = resp.text?.trim() ?: ""
                if (txt.isNotBlank()) {
                    var cleaned = txt.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    val sIdx = cleaned.indexOf('{')
                    val eIdx = cleaned.lastIndexOf('}')
                    if (sIdx != -1 && eIdx != -1 && eIdx > sIdx) {
                        cleaned = cleaned.substring(sIdx, eIdx + 1)
                        val obj = JSONObject(cleaned)
                        val name = obj.optString("name", trimmed).trim()
                        val kcal = obj.optDouble("caloriesPer100g", 120.0).toFloat()
                        val prot = obj.optDouble("proteinPer100g", 5.0).toFloat()
                        val carb = obj.optDouble("carbsPer100g", 15.0).toFloat()
                        val fat = obj.optDouble("fatPer100g", 3.0).toFloat()
                        val serving = obj.optDouble("servingSizeGrams", 100.0).toFloat()
                        val servDesc = obj.optString("servingDescription", "${serving.toInt()}g")

                        val imgUrl = RealFoodImageResolver.resolve(name, brandName)
                        return@withContext FoodCatalogItem(
                            id = "ai_${name.hashCode()}",
                            name = name,
                            brand = brandName,
                            caloriesPer100g = kcal,
                            proteinPer100g = prot,
                            carbsPer100g = carb,
                            fatPer100g = fat,
                            servingSizeGrams = serving,
                            servingDescription = servDesc,
                            imageUrl = imgUrl,
                            isGeneric = category == FoodSourceCategory.BASICOS
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fallo al estimar alimento con Gemini: ${e.message}")
            }
        }

        // Fallback a motor local heurístico
        val localList = parseFoodsFromTextLocally(trimmed)
        val first = localList.firstOrNull()
        if (first != null) {
            val factor = if (first.grams > 0) 100f / first.grams else 1f
            val imgUrl = RealFoodImageResolver.resolve(first.name, brandName)
            return@withContext FoodCatalogItem(
                id = "ai_local_${trimmed.hashCode()}",
                name = first.name,
                brand = brandName,
                caloriesPer100g = first.calories * factor,
                proteinPer100g = first.protein * factor,
                carbsPer100g = first.carbs * factor,
                fatPer100g = first.fat * factor,
                servingSizeGrams = first.grams,
                servingDescription = "1 ración (~${first.grams.toInt()}g)",
                imageUrl = imgUrl,
                isGeneric = category == FoodSourceCategory.BASICOS
            )
        }

        return@withContext null
    }

    /**
     * Limpia y parsea el JSON recibido de Gemini.
     */
    private fun parseJsonItems(rawText: String): List<DetectedFoodItem> {
        val items = mutableListOf<DetectedFoodItem>()
        try {
            var cleaned = rawText.trim()
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.removePrefix("```json")
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.removePrefix("```")
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.removeSuffix("```")
            }
            cleaned = cleaned.trim()

            val startIndex = cleaned.indexOf('[')
            val endIndex = cleaned.lastIndexOf(']')
            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                cleaned = cleaned.substring(startIndex, endIndex + 1)
            }

            val jsonArray = JSONArray(cleaned)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val name = obj.optString("name").trim()
                if (name.isBlank()) continue

                val grams = obj.optDouble("grams", 100.0).toFloat()
                val calories = obj.optDouble("calories", 0.0).toFloat()
                val protein = obj.optDouble("protein", 0.0).toFloat()
                val carbs = obj.optDouble("carbs", 0.0).toFloat()
                val fat = obj.optDouble("fat", 0.0).toFloat()

                items.add(
                    DetectedFoodItem(
                        name = name.replaceFirstChar { it.uppercase() },
                        grams = if (grams > 0) grams else 100f,
                        calories = calories,
                        protein = protein,
                        carbs = carbs,
                        fat = fat
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parseando JSON de Gemini: ${e.message}")
        }
        return items
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Motor NLP local avanzado para reconocer alimentos en español, cantidades y macros de forma offline.
     */
    fun parseFoodsFromTextLocally(text: String): List<DetectedFoodItem> {
        val lower = text.lowercase(Locale.ROOT)
        val items = mutableListOf<DetectedFoodItem>()

        // 1. Extraer multiplicadores de cantidad (ej: "2 huevos", "150g de pollo", "un plato de lentejas")
        fun findGramsOrMultiplier(segment: String, defaultGrams: Float, unitMultiplier: Float = 1f): Float {
            // Buscar gramos explícitos: ej "150g" o "150 gramos"
            val gramMatch = Pattern.compile("(\\d+)\\s*(?:g|gr|gramos|ml)", Pattern.CASE_INSENSITIVE).matcher(segment)
            if (gramMatch.find()) {
                val g = gramMatch.group(1)?.toFloatOrNull()
                if (g != null && g > 0) return g
            }

            // Buscar unidades: ej "2", "3", "dos", "tres"
            val countMatch = Pattern.compile("\\b(\\d+)\\b").matcher(segment)
            if (countMatch.find()) {
                val count = countMatch.group(1)?.toFloatOrNull()
                if (count != null && count in 1f..10f) return count * defaultGrams
            }

            if (segment.contains("dos") || segment.contains("par de")) return defaultGrams * 2f
            if (segment.contains("tres")) return defaultGrams * 3f
            if (segment.contains("cuatro")) return defaultGrams * 4f
            if (segment.contains("medio") || segment.contains("media")) return defaultGrams * 0.5f

            return defaultGrams * unitMultiplier
        }

        // Dividir la descripción por comas, "con", "y", "+", puntos
        val rawClauses = lower.split(Regex("[,;+y\\n]+|\\bcon\\b|\\bmás\\b")).map { it.trim() }.filter { it.isNotBlank() }

        for (clause in rawClauses) {
            when {
                // Tortilla de patatas
                clause.contains("tortilla de patata") || clause.contains("tortilla espanola") || clause.contains("tortilla española") -> {
                    val grams = findGramsOrMultiplier(clause, 180f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Tortilla de patatas casera", grams, 154f * factor, 5.5f * factor, 13.5f * factor, 8.8f * factor))
                }
                // Tortilla francesa
                clause.contains("tortilla francesa") || clause.contains("tortilla de claras") || clause.contains("tortilla simple") -> {
                    val grams = findGramsOrMultiplier(clause, 110f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Tortilla francesa", grams, 155f * factor, 12f * factor, 1f * factor, 11.5f * factor))
                }
                // Huevos
                clause.contains("huevo") -> {
                    val isFrito = clause.contains("frito")
                    val grams = findGramsOrMultiplier(clause, 60f)
                    val factor = grams / 100f
                    if (isFrito) {
                        items.add(DetectedFoodItem("Huevo frito en aceite de oliva", grams, 196f * factor, 13.5f * factor, 0.5f * factor, 15f * factor))
                    } else {
                        items.add(DetectedFoodItem("Huevo cocido o a la plancha", grams, 143f * factor, 12.6f * factor, 0.7f * factor, 9.5f * factor))
                    }
                }
                // Pollo
                clause.contains("pollo") -> {
                    val grams = findGramsOrMultiplier(clause, 160f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Pechuga de pollo a la plancha", grams, 165f * factor, 31f * factor, 0f, 3.6f * factor))
                }
                // Pavo
                clause.contains("pavo") -> {
                    val isFiambre = clause.contains("loncha") || clause.contains("fiambre") || clause.contains("embutido")
                    val defaultG = if (isFiambre) 60f else 160f
                    val grams = findGramsOrMultiplier(clause, defaultG)
                    val factor = grams / 100f
                    if (isFiambre) {
                        items.add(DetectedFoodItem("Pechuga de pavo en lonchas", grams, 94f * factor, 18.5f * factor, 1.2f * factor, 1.6f * factor))
                    } else {
                        items.add(DetectedFoodItem("Pechuga de pavo a la plancha", grams, 135f * factor, 29f * factor, 0f, 1.8f * factor))
                    }
                }
                // Ternera / Carne picada / Hamburguesa
                clause.contains("hamburguesa") || clause.contains("burger") -> {
                    val grams = findGramsOrMultiplier(clause, 150f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Hamburguesa de ternera", grams, 245f * factor, 21f * factor, 0.8f * factor, 17.5f * factor))
                }
                clause.contains("ternera") || clause.contains("filete") || clause.contains("entrecot") -> {
                    val grams = findGramsOrMultiplier(clause, 160f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Filete de ternera magra", grams, 175f * factor, 26f * factor, 0f, 7.5f * factor))
                }
                clause.contains("carne picada") -> {
                    val grams = findGramsOrMultiplier(clause, 150f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Carne picada mixta", grams, 215f * factor, 19f * factor, 0.5f * factor, 15f * factor))
                }
                clause.contains("lomo") || clause.contains("cerdo") -> {
                    val grams = findGramsOrMultiplier(clause, 140f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Cinta de lomo de cerdo", grams, 160f * factor, 27f * factor, 0f, 5.5f * factor))
                }
                // Jamón serrano / ibérico
                clause.contains("jamon") || clause.contains("jamón") -> {
                    val grams = findGramsOrMultiplier(clause, 45f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Jamón serrano / ibérico", grams, 240f * factor, 31f * factor, 0.5f * factor, 13f * factor))
                }
                // Pescados: Salmón, Atún, Merluza
                clause.contains("salmon") || clause.contains("salmón") -> {
                    val isAhumado = clause.contains("ahumado")
                    val defaultG = if (isAhumado) 60f else 150f
                    val grams = findGramsOrMultiplier(clause, defaultG)
                    val factor = grams / 100f
                    if (isAhumado) {
                        items.add(DetectedFoodItem("Salmón ahumado en lonchas", grams, 192f * factor, 22f * factor, 0.5f * factor, 11.5f * factor))
                    } else {
                        items.add(DetectedFoodItem("Salmón a la plancha", grams, 208f * factor, 20f * factor, 0f, 13f * factor))
                    }
                }
                clause.contains("atun") || clause.contains("atún") -> {
                    val grams = findGramsOrMultiplier(clause, 60f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Atún claro al natural", grams, 101f * factor, 24f * factor, 0f, 0.6f * factor))
                }
                clause.contains("merluza") || clause.contains("pescado") || clause.contains("dorada") || clause.contains("lubina") || clause.contains("bacalao") -> {
                    val grams = findGramsOrMultiplier(clause, 160f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Pescado blanco (merluza/dorada)", grams, 95f * factor, 18f * factor, 0f, 2f * factor))
                }
                clause.contains("gamba") || clause.contains("langostino") || clause.contains("marisco") || clause.contains("calamar") || clause.contains("pulpo") -> {
                    val grams = findGramsOrMultiplier(clause, 150f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Marisco / Calamar a la plancha", grams, 105f * factor, 20f * factor, 1f * factor, 1.8f * factor))
                }
                // Arroz / Paella
                clause.contains("paella") -> {
                    val grams = findGramsOrMultiplier(clause, 250f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Paella tradicional", grams, 165f * factor, 8.5f * factor, 21f * factor, 5.2f * factor))
                }
                clause.contains("arroz") -> {
                    val grams = findGramsOrMultiplier(clause, 160f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Arroz blanco cocido", grams, 130f * factor, 2.7f * factor, 28f * factor, 0.3f * factor))
                }
                // Pasta / Pizza / Lasaña
                clause.contains("pizza") -> {
                    val grams = findGramsOrMultiplier(clause, 200f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Pizza de jamón y queso", grams, 265f * factor, 11f * factor, 30f * factor, 10.5f * factor))
                }
                clause.contains("pasta") || clause.contains("espagueti") || clause.contains("macarron") || clause.contains("macarrón") || clause.contains("lasaña") -> {
                    val grams = findGramsOrMultiplier(clause, 200f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Pasta cocida con tomate", grams, 150f * factor, 6.5f * factor, 28f * factor, 1.5f * factor))
                }
                // Legumbres: Lentejas, Garbanzos, Hummus
                clause.contains("lenteja") || clause.contains("lentejas") -> {
                    val grams = findGramsOrMultiplier(clause, 250f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Lentejas estofadas con verduras", grams, 116f * factor, 9f * factor, 20f * factor, 0.4f * factor))
                }
                clause.contains("garbanzo") || clause.contains("garbanzos") -> {
                    val grams = findGramsOrMultiplier(clause, 250f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Garbanzos cocidos", grams, 128f * factor, 7.8f * factor, 18f * factor, 2.6f * factor))
                }
                clause.contains("hummus") -> {
                    val grams = findGramsOrMultiplier(clause, 50f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Hummus de garbanzos", grams, 278f * factor, 6.4f * factor, 17.5f * factor, 19.8f * factor))
                }
                // Sopas y Gazpacho
                clause.contains("gazpacho") || clause.contains("salmorejo") -> {
                    val isSalmorejo = clause.contains("salmorejo")
                    val grams = findGramsOrMultiplier(clause, 220f)
                    val factor = grams / 100f
                    if (isSalmorejo) {
                        items.add(DetectedFoodItem("Salmorejo cordobés", grams, 85f * factor, 2.1f * factor, 7.5f * factor, 5.2f * factor))
                    } else {
                        items.add(DetectedFoodItem("Gazpacho andaluz tradicional", grams, 45f * factor, 0.8f * factor, 4.2f * factor, 2.6f * factor))
                    }
                }
                // Panes y Tostadas
                clause.contains("pan") || clause.contains("tostada") || clause.contains("bocadillo") || clause.contains("rebanada") -> {
                    val grams = findGramsOrMultiplier(clause, 50f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Pan integral / rústico", grams, 240f * factor, 9f * factor, 45f * factor, 2f * factor))
                }
                // Patatas
                clause.contains("patatas fritas") || clause.contains("papas fritas") -> {
                    val grams = findGramsOrMultiplier(clause, 120f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Patatas fritas", grams, 285f * factor, 3.4f * factor, 37f * factor, 14f * factor))
                }
                clause.contains("patata") || clause.contains("papa") || clause.contains("pure") || clause.contains("puré") -> {
                    val grams = findGramsOrMultiplier(clause, 180f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Patata cocida o asada", grams, 86f * factor, 2f * factor, 18.5f * factor, 0.1f * factor))
                }
                // Ensalada y verduras
                clause.contains("ensalada") -> {
                    val grams = findGramsOrMultiplier(clause, 180f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Ensalada mixta fresca", grams, 65f * factor, 3f * factor, 4f * factor, 3.5f * factor))
                }
                clause.contains("brocoli") || clause.contains("brócoli") -> {
                    val grams = findGramsOrMultiplier(clause, 150f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Brócoli al vapor", grams, 35f * factor, 2.4f * factor, 7.2f * factor, 0.4f * factor))
                }
                clause.contains("tomate") -> {
                    val grams = findGramsOrMultiplier(clause, 120f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Tomate fresco", grams, 22f * factor, 1f * factor, 4f * factor, 0.2f * factor))
                }
                clause.contains("aguacate") || clause.contains("guacamole") -> {
                    val grams = findGramsOrMultiplier(clause, 80f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Aguacate fresco", grams, 160f * factor, 2f * factor, 8.5f * factor, 14.7f * factor))
                }
                // Frutas
                clause.contains("platano") || clause.contains("plátano") -> {
                    val grams = findGramsOrMultiplier(clause, 120f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Plátano de Canarias", grams, 91f * factor, 1.1f * factor, 21.5f * factor, 0.3f * factor))
                }
                clause.contains("manzana") -> {
                    val grams = findGramsOrMultiplier(clause, 160f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Manzana fresca", grams, 52f * factor, 0.3f * factor, 13.8f * factor, 0.2f * factor))
                }
                clause.contains("naranja") || clause.contains("mandarina") -> {
                    val grams = findGramsOrMultiplier(clause, 170f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Naranja de mesa", grams, 47f * factor, 0.9f * factor, 11.8f * factor, 0.1f * factor))
                }
                clause.contains("fresa") || clause.contains("fresas") -> {
                    val grams = findGramsOrMultiplier(clause, 150f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Fresas frescas", grams, 33f * factor, 0.7f * factor, 7.7f * factor, 0.3f * factor))
                }
                // Lácteos & Yogures
                clause.contains("queso fresco batido") || clause.contains("queso batido") -> {
                    val grams = findGramsOrMultiplier(clause, 200f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Queso fresco batido 0%", grams, 46f * factor, 8.5f * factor, 3.5f * factor, 0.1f * factor))
                }
                clause.contains("yogur") -> {
                    val grams = findGramsOrMultiplier(clause, 125f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Yogur natural", grams, 65f * factor, 4.5f * factor, 4.8f * factor, 3.2f * factor))
                }
                clause.contains("queso") -> {
                    val grams = findGramsOrMultiplier(clause, 50f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Queso tierno / semicurado", grams, 350f * factor, 23f * factor, 1.5f * factor, 28f * factor))
                }
                clause.contains("leche") -> {
                    val grams = findGramsOrMultiplier(clause, 200f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Leche semidesnatada", grams, 46f * factor, 3.2f * factor, 4.8f * factor, 1.6f * factor))
                }
                // Desayuno: Avena, Café, Aceite
                clause.contains("avena") -> {
                    val grams = findGramsOrMultiplier(clause, 50f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Copos de avena integral", grams, 375f * factor, 13.5f * factor, 59f * factor, 7f * factor))
                }
                clause.contains("cafe") || clause.contains("café") -> {
                    val hasMilk = clause.contains("leche") || clause.contains("cortado")
                    if (hasMilk) {
                        items.add(DetectedFoodItem("Café con leche", 150f, 55f, 3.5f, 5f, 2.5f))
                    } else {
                        items.add(DetectedFoodItem("Café solo expreso", 60f, 2f, 0.2f, 0.3f, 0f))
                    }
                }
                clause.contains("aceite") || clause.contains("oliva") -> {
                    val grams = findGramsOrMultiplier(clause, 10f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Aceite de oliva virgen extra", grams, 900f * factor, 0f, 0f, 100f * factor))
                }
                clause.contains("nuez") || clause.contains("nueces") || clause.contains("almendra") -> {
                    val grams = findGramsOrMultiplier(clause, 25f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Frutos secos al natural", grams, 620f * factor, 18f * factor, 15f * factor, 55f * factor))
                }
                clause.contains("chocolate") -> {
                    val grams = findGramsOrMultiplier(clause, 20f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Chocolate negro", grams, 570f * factor, 8.5f * factor, 25f * factor, 45f * factor))
                }
            }
        }

        // Si no reconoció alimentos específicos pero el usuario escribió texto
        if (items.isEmpty()) {
            val fallbackG = findGramsOrMultiplier(lower, 150f)
            items.add(
                DetectedFoodItem(
                    name = text.trim().take(35).replaceFirstChar { it.uppercase() },
                    grams = fallbackG,
                    calories = 180f * (fallbackG / 100f),
                    protein = 12f * (fallbackG / 100f),
                    carbs = 18f * (fallbackG / 100f),
                    fat = 6f * (fallbackG / 100f)
                )
            )
        }

        return items.distinctBy { it.name.lowercase() }
    }

    /**
     * Asesor / Coach Nutricional con IA: indica si el usuario necesita comer más, menos o qué comer.
     */
    suspend fun generateNutritionAdvice(
        consumedCalories: Float,
        targetCalories: Float,
        burnedCalories: Float,
        protein: Float,
        targetProtein: Float,
        carbs: Float,
        targetCarbs: Float,
        fat: Float,
        targetFat: Float,
        goal: String
    ): String = withContext(Dispatchers.IO) {
        val userGoals = userPreferences.userGoals.first()
        val apiKey = userGoals.geminiApiKey.ifBlank {
            BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() && !it.contains("PEGA_AQUI") } ?: ""
        }

        val remainingKcal = targetCalories - consumedCalories
        val proteinDebt = (targetProtein - protein).coerceAtLeast(0f)

        if (apiKey.isNotBlank()) {
            val modelNames = listOf("gemini-2.0-flash", "gemini-1.5-flash")
            for (modelName in modelNames) {
                try {
                    val model = GenerativeModel(
                        modelName = modelName,
                        apiKey = apiKey,
                        generationConfig = generationConfig {
                            temperature = 0.3f
                            maxOutputTokens = 350
                        }
                    )

                    val prompt = """
                        Eres el coach nutricional personal en la app Life Dashboard.
                        Estado actual del usuario hoy:
                        - Objetivo físico: $goal
                        - Meta calórica diaria: ${targetCalories.toInt()} kcal
                        - Calorías consumidas: ${consumedCalories.toInt()} kcal
                        - Calorías restantes: ${remainingKcal.toInt()} kcal
                        - Calorías quemadas en actividad: ${burnedCalories.toInt()} kcal
                        - Proteínas: ${protein.toInt()}g de ${targetProtein.toInt()}g
                        - Carbohidratos: ${carbs.toInt()}g de ${targetCarbs.toInt()}g
                        - Grasas: ${fat.toInt()}g de ${targetFat.toInt()}g
                        
                        Instrucciones:
                        1. Sé directo, empático y ultra-conciso (máximo 2 párrafos cortos en español).
                        2. Responde directamente si hoy necesita comer más, mantenerse o frenar.
                        3. Recomienda 2 o 3 opciones reales muy sencillas (ej: pechuga de pavo, atún al natural, queso batido 0%, ensalada, fruta) para cuadrar sus macros.
                    """.trimIndent()

                    val response = model.generateContent(prompt)
                    val advice = response.text?.trim()
                    if (!advice.isNullOrBlank()) {
                        return@withContext advice
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Fallo al generar consejo con $modelName: ${e.message}")
                }
            }
        }

        // Motor heurístico offline si no hay API key o no hay conexión
        buildHeuristicAdvice(consumedCalories, targetCalories, proteinDebt, goal)
    }

    private fun buildHeuristicAdvice(
        consumedCalories: Float,
        targetCalories: Float,
        proteinDebt: Float,
        goal: String
    ): String {
        val remaining = (targetCalories - consumedCalories).toInt()
        val sb = StringBuilder()

        if (remaining > 500) {
            sb.append("Aún necesitas comer más hoy. Tienes un margen de $remaining kcal para nutrirte adecuadamente sin pasar hambre.\n\n")
            if (proteinDebt > 25f) {
                sb.append("💡 Te faltan ${proteinDebt.toInt()}g de proteína. Te sugerimos: 160g de pechuga de pollo/pavo, 2 latas de atún al natural o un tazón de 200g de queso fresco batido 0% con canela o fruta.")
            } else {
                sb.append("💡 Prioriza alimentos densos en nutrientes: arroz integral o patata cocida con una fuente magra de proteína y una cucharada de aceite de oliva.")
            }
        } else if (remaining in -150..500) {
            sb.append("¡Vas a un ritmo perfecto! Estás muy cerca de tu objetivo calórico de hoy ($remaining kcal restantes).\n\n")
            if (proteinDebt > 15f) {
                sb.append("💡 Para tu última comida o snack, opta por proteína pura baja en calorías: claras de huevo revueltas, yogur griego o pechuga de pavo.")
            } else {
                sb.append("💡 Mantén una buena hidratación para la noche y una cena ligera a base de verduras salteadas o ensalada fresca.")
            }
        } else {
            val superado = -remaining
            sb.append("Has superado tu meta diaria en $superado kcal. La consistencia a lo largo de la semana es lo que realmente importa.\n\n")
            sb.append("💡 Para el resto del día: modera la ingesta. Si tienes apetito, opta por infusiones calientes, caldo vegetal, pepino o gelatina 0%.")
        }
        return sb.toString()
    }
}
