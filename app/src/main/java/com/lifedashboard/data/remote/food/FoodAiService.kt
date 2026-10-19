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

        if (apiKey.isBlank()) {
            // Si el usuario no ha puesto API key pero proporcionó descripción, podemos hacer una estimación inteligente
            if (userDescription.isNotBlank()) {
                val fallbackItems = estimateFromDescription(userDescription)
                return@withContext FoodAiResult.Success(
                    items = fallbackItems,
                    explanation = "Estimación generada a partir de tu descripción: \"$userDescription\" (Para análisis visual con IA, añade tu clave gratuita en Ajustes)."
                )
            }
            return@withContext FoodAiResult.Error(
                "No hay clave de Google Gemini API configurada. Puedes obtener una 100% gratis en Google AI Studio y pegarla en Configuración."
            )
        }

        try {
            // Escalar el bitmap para optimizar la carga (máximo 1024x1024 para velocidad)
            val scaledBitmap = scaleBitmapIfNeeded(bitmap, maxDimension = 1024)

            val model = GenerativeModel(
                modelName = "gemini-2.0-flash",
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.2f
                    maxOutputTokens = 800
                }
            )

            val prompt = buildPrompt(userDescription)
            val inputContent = content {
                image(scaledBitmap)
                text(prompt)
            }

            Log.d(TAG, "Enviando imagen a Gemini 2.0 Flash con descripción: '$userDescription'")
            val response = model.generateContent(inputContent)
            val text = response.text?.trim() ?: ""

            if (text.isBlank()) {
                return@withContext FoodAiResult.Error("La IA no devolvió respuesta para la imagen enviada.")
            }

            val items = parseJsonItems(text)
            if (items.isEmpty()) {
                // Si el JSON vino incompleto pero hay texto, intentamos fallback
                if (userDescription.isNotBlank()) {
                    return@withContext FoodAiResult.Success(
                        items = estimateFromDescription(userDescription),
                        explanation = "Se usó tu descripción para estimar el plato."
                    )
                }
                return@withContext FoodAiResult.Error("No se pudieron detectar con claridad los alimentos en la fotografía. Intenta añadir una breve descripción.")
            }

            FoodAiResult.Success(
                items = items,
                explanation = "Alimentos detectados y calculados con éxito por Gemini Flash."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error analizando comida con Gemini", e)
            if (userDescription.isNotBlank()) {
                FoodAiResult.Success(
                    items = estimateFromDescription(userDescription),
                    explanation = "Hubo un error de conexión con la IA visual, pero se estimó a partir de tu descripción: \"$userDescription\"."
                )
            } else {
                FoodAiResult.Error("Error al conectar con la IA: ${e.localizedMessage ?: "Comprueba tu conexión o clave de API"}")
            }
        }
    }

    private fun buildPrompt(userDescription: String): String {
        val extraContext = if (userDescription.isNotBlank()) {
            "\nINFORMACIÓN APORTADA POR EL USUARIO: \"$userDescription\". Utiliza esta descripción como guía prioritaria para saber qué ingredientes lleva el plato, qué aceites o salsas se usaron y el tamaño de las raciones.\n"
        } else ""

        return """
            Eres un nutricionista experto y especialista en reconocimiento visual de alimentos.
            Analiza cuidadosamente la imagen del plato de comida adjunta.$extraContext
            
            Tu objetivo:
            1. Identificar cada uno de los alimentos o preparaciones presentes en el plato.
            2. Estimar el peso en gramos de cada componente de forma realista.
            3. Calcular las calorías (kcal), proteínas (g), carbohidratos (g) y grasas (g) de cada elemento.
            
            Debes responder ÚNICAMENTE con un array JSON válido, sin bloques de código markdown (sin ```json ni ```), sin texto antes ni después.
            
            Formato exacto:
            [
              {
                "name": "Pechuga de pollo a la plancha",
                "grams": 180.0,
                "calories": 297.0,
                "protein": 55.8,
                "carbs": 0.0,
                "fat": 6.5
              },
              {
                "name": "Arroz blanco",
                "grams": 150.0,
                "calories": 195.0,
                "protein": 4.1,
                "carbs": 42.0,
                "fat": 0.5
              }
            ]
        """.trimIndent()
    }

    private fun parseJsonItems(rawText: String): List<DetectedFoodItem> {
        val items = mutableListOf<DetectedFoodItem>()
        try {
            // Limpiar posibles bloques markdown ```json ... ```
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

            // Buscar el primer '[' y el último ']'
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
            Log.w(TAG, "Error parseando JSON de Gemini: ${e.message}\nTexto recibido:\n$rawText")
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
     * Analiza una descripción en texto libre (sin foto) de lo que comió el usuario
     * y genera automáticamente los alimentos, porciones y macronutrientes.
     */
    suspend fun analyzeFoodText(userDescription: String): FoodAiResult = withContext(Dispatchers.IO) {
        val trimmed = userDescription.trim()
        if (trimmed.isBlank()) {
            return@withContext FoodAiResult.Error("Escribe o describe lo que has comido.")
        }

        val userGoals = userPreferences.userGoals.first()
        val apiKey = userGoals.geminiApiKey.ifBlank {
            BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() && !it.contains("PEGA_AQUI") } ?: ""
        }

        if (apiKey.isBlank()) {
            val fallbackItems = estimateFromDescription(trimmed)
            return@withContext FoodAiResult.Success(
                items = fallbackItems,
                explanation = "Estimación calculada a partir de tu texto. (Para análisis avanzado con IA, añade tu clave gratuita de Google Gemini en Configuración)."
            )
        }

        try {
            val model = GenerativeModel(
                modelName = "gemini-2.0-flash",
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.2f
                    maxOutputTokens = 800
                }
            )

            val prompt = """
                Eres un nutricionista experto.
                El usuario ha registrado en texto lo que ha comido: "$trimmed"
                
                Tu objetivo:
                1. Extraer e identificar individualmente cada alimento o bebida mencionada.
                2. Estimar de forma realista el peso en gramos (o ml) de cada uno según las cantidades habituales si no se especifican.
                3. Calcular con precisión calorías (kcal), proteínas (g), carbohidratos (g) y grasas (g) para cada alimento.
                
                Debes responder ÚNICAMENTE con un array JSON válido, sin bloques markdown (sin ```json ni ```), sin texto antes ni después.
                Formato exacto:
                [
                  {
                    "name": "Nombre del alimento",
                    "grams": 150.0,
                    "calories": 250.0,
                    "protein": 20.0,
                    "carbs": 30.0,
                    "fat": 5.0
                  }
                ]
            """.trimIndent()

            val response = model.generateContent(prompt)
            val text = response.text?.trim() ?: ""

            if (text.isBlank()) {
                return@withContext FoodAiResult.Success(
                    items = estimateFromDescription(trimmed),
                    explanation = "Estimación local por texto."
                )
            }

            val items = parseJsonItems(text)
            if (items.isEmpty()) {
                return@withContext FoodAiResult.Success(
                    items = estimateFromDescription(trimmed),
                    explanation = "Estimación calculada con éxito."
                )
            }

            FoodAiResult.Success(
                items = items,
                explanation = "Alimentos y macros calculados por Gemini 2.0 Flash a partir de tu descripción."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error analizando texto con Gemini", e)
            FoodAiResult.Success(
                items = estimateFromDescription(trimmed),
                explanation = "Estimación generada localmente para: \"$trimmed\"."
            )
        }
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
            try {
                val model = GenerativeModel(
                    modelName = "gemini-2.0-flash",
                    apiKey = apiKey,
                    generationConfig = generationConfig {
                        temperature = 0.4f
                        maxOutputTokens = 350
                    }
                )

                val prompt = """
                    Eres el coach nutricional personal del usuario dentro de la app Vital.
                    Datos del usuario hoy:
                    - Objetivo principal: $goal (ej: pérdida de peso / déficit calórico, mantenimiento, volumen)
                    - Meta calórica diaria: ${targetCalories.toInt()} kcal
                    - Calorías consumidas hasta ahora: ${consumedCalories.toInt()} kcal
                    - Calorías restantes: ${remainingKcal.toInt()} kcal
                    - Calorías quemadas en actividad: ${burnedCalories.toInt()} kcal
                    - Proteínas: ${protein.toInt()}g de ${targetProtein.toInt()}g
                    - Carbohidratos: ${carbs.toInt()}g de ${targetCarbs.toInt()}g
                    - Grasas: ${fat.toInt()}g de ${targetFat.toInt()}g
                    
                    Tu tarea:
                    1. Responde de forma muy directa, motivadora y concisa (en 2 o 3 párrafos cortos máximo en español).
                    2. Responde claramente a la pregunta: ¿Necesita comer más o comer menos hoy?
                    3. Sugiere 2 o 3 alimentos o platos concretos muy accesibles (ej: pechuga, atún, queso fresco batido, frutos secos, verduras) adecuados para completar los macros que le falten o frenar si ya superó su meta.
                """.trimIndent()

                val response = model.generateContent(prompt)
                val advice = response.text?.trim()
                if (!advice.isNullOrBlank()) {
                    return@withContext advice
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error generando consejo IA: ${e.message}")
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
            sb.append("Aún necesitas comer más hoy. Tienes un margen de $remaining kcal para alcanzar tu requerimiento diario sin pasar hambre.\n\n")
            if (proteinDebt > 25f) {
                sb.append("💡 Te faltan ${proteinDebt.toInt()}g de proteína. Te sugerimos: 150g de pechuga de pollo/pavo, 2 latas de atún al natural o un tazón de 200g de queso fresco batido 0% con canela o fruta.")
            } else {
                sb.append("💡 Prioriza alimentos densos en nutrientes: arroz integral o patata cocida con una fuente magra de proteína y una cucharada de aceite de oliva.")
            }
        } else if (remaining in -150..500) {
            sb.append("¡Vas a un ritmo perfecto! Estás muy cerca de tu objetivo calórico de hoy ($remaining kcal restantes).\n\n")
            if (proteinDebt > 15f) {
                sb.append("💡 Si vas a hacer una última comida o snack, opta por proteína pura baja en calorías: claras de huevo revueltas, yogur griego desnatado o jamón cocido.")
            } else {
                sb.append("💡 Mantén una buena hidratación para la noche y una cena ligera a base de verduras salteadas o ensalada fresca.")
            }
        } else {
            val superado = -remaining
            sb.append("Has superado tu meta diaria en $superado kcal. No te preocupes, la consistencia semanal es lo que cuenta.\n\n")
            sb.append("💡 Para el resto del día: come menos o frena la ingesta calórica. Si tienes apetito, opta por infusiones calientes, pepino, caldo de verduras o gelatina 0%.")
        }
        return sb.toString()
    }

    private fun estimateFromDescription(description: String): List<DetectedFoodItem> {
        val lower = description.lowercase()
        val items = mutableListOf<DetectedFoodItem>()

        if (lower.contains("pollo") || lower.contains("pavo")) {
            items.add(DetectedFoodItem("Pechuga de pavo/pollo", 150f, 220f, 42f, 0f, 4.5f))
        }
        if (lower.contains("arroz")) {
            items.add(DetectedFoodItem("Arroz blanco cocido", 150f, 195f, 4.1f, 42f, 0.5f))
        }
        if (lower.contains("huevo")) {
            val count = if (lower.contains("2") || lower.contains("dos")) 2 else 1
            items.add(DetectedFoodItem("Huevos medianos", 60f * count, 85f * count, 7.5f * count, 0.4f * count, 5.8f * count))
        }
        if (lower.contains("pan") || lower.contains("tostad")) {
            val count = if (lower.contains("2") || lower.contains("dos")) 2 else 1
            items.add(DetectedFoodItem("Pan integral", 40f * count, 98f * count, 4f * count, 18f * count, 1f * count))
        }
        if (lower.contains("pasta") || lower.contains("macarr") || lower.contains("espaguet")) {
            items.add(DetectedFoodItem("Pasta cocida", 180f, 284f, 10.4f, 55.8f, 1.6f))
        }
        if (lower.contains("patata") || lower.contains("papa")) {
            items.add(DetectedFoodItem("Patatas cocidas", 200f, 172f, 4f, 37f, 0.2f))
        }
        if (lower.contains("aceite")) {
            items.add(DetectedFoodItem("Aceite de oliva virgen", 10f, 88f, 0f, 0f, 10f))
        }
        if (lower.contains("ensalada") || lower.contains("lechuga") || lower.contains("tomate")) {
            items.add(DetectedFoodItem("Ensalada fresca", 150f, 35f, 1.5f, 6f, 0.4f))
        }
        if (lower.contains("atun") || lower.contains("atún")) {
            items.add(DetectedFoodItem("Atún al natural", 60f, 60f, 14f, 0f, 0.5f))
        }
        if (lower.contains("leche")) {
            items.add(DetectedFoodItem("Leche semidesnatada", 200f, 92f, 6.4f, 9.6f, 3.2f))
        }
        if (lower.contains("café") || lower.contains("cafe")) {
            items.add(DetectedFoodItem("Café solo", 100f, 2f, 0.2f, 0.3f, 0f))
        }
        if (lower.contains("platano") || lower.contains("plátano")) {
            items.add(DetectedFoodItem("Plátano mediano", 110f, 98f, 1.2f, 25f, 0.3f))
        }
        if (lower.contains("manzana")) {
            items.add(DetectedFoodItem("Manzana fresca", 150f, 78f, 0.5f, 20f, 0.3f))
        }
        if (lower.contains("yogur")) {
            items.add(DetectedFoodItem("Yogur natural", 125f, 76f, 4.4f, 5.9f, 4.1f))
        }
        if (lower.contains("jamon") || lower.contains("jamón")) {
            items.add(DetectedFoodItem("Jamón cocido/serrano", 40f, 80f, 10f, 0.5f, 4f))
        }
        if (lower.contains("queso")) {
            items.add(DetectedFoodItem("Queso fresco", 80f, 120f, 11f, 3f, 6f))
        }

        if (items.isEmpty()) {
            items.add(DetectedFoodItem(description.take(40), 150f, 220f, 12f, 25f, 7f))
        }
        return items
    }
}
