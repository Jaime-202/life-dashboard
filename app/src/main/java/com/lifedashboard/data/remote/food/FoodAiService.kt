package com.lifedashboard.data.remote.food

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.lifedashboard.BuildConfig
import com.lifedashboard.data.remote.gemini.GeminiModelManager
import com.lifedashboard.data.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
    val brand: String = "Genérico",
    val isGeneric: Boolean = true,
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
     * Prueba la validez de una clave API de Google Gemini de forma ultra-rápida (<400ms)
     * consultando directamente el endpoint oficial de autenticación de Google AI.
     */
    suspend fun testApiKeyConnection(key: String, preferredModel: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("La clave API no puede estar vacía."))
        }
        if (trimmed.length < 15) {
            return@withContext Result.failure(IllegalArgumentException("La clave parece incompleta o demasiado corta."))
        }

        try {
            // Comprobación directa y ultra-rápida contra el gateway de Google Gemini (<400ms)
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=5")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("Accept", "application/json")
                // En cabecera y no en la URL, que puede acabar en registros y trazas de error
                setRequestProperty("x-goog-api-key", trimmed)
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                conn.disconnect()
                val modelName = preferredModel?.takeIf { it.isNotBlank() && it != "auto" } ?: "Gemini"
                return@withContext Result.success("Conexión confirmada con $modelName")
            }

            val errorStream = conn.errorStream ?: conn.inputStream
            val errResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()

            val cleanError = GeminiModelManager.extractCleanErrorMessage(errResponse)
            return@withContext Result.failure(Exception(cleanError))
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: e.message ?: "Error de red"
            val clean = GeminiModelManager.extractCleanErrorMessage(errMsg)
            return@withContext Result.failure(Exception(clean))
        }
    }

    /**
     * Analiza una fotografía de comida junto a una descripción opcional del usuario
     * para identificar platos, estimar gramos y desglosar macronutrientes.
     */
    suspend fun analyzeFoodImage(
        bitmap: Bitmap,
        userDescription: String,
        mealType: String = "",
        existingItemsContext: List<String> = emptyList()
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
            val prompt = buildVisionPrompt(userDescription, mealType, existingItemsContext)

            // Estrategia de modelos: selección inteligente (Gemini 3.8 Flash por defecto con fallbacks automáticos)
            val candidateModels = GeminiModelManager.getCandidateModels(apiKey, userGoals.geminiModel)
            var responseText = ""

            for (modelName in candidateModels) {
                try {
                    val model = GenerativeModel(
                        modelName = modelName,
                        apiKey = apiKey,
                        generationConfig = generationConfig {
                            temperature = 0.15f
                            maxOutputTokens = 2048
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
                    val errMsg = e.message ?: ""
                    Log.w(TAG, "Fallo con modelo $modelName: $errMsg")
                    if (errMsg.contains("quota", ignoreCase = true) ||
                        errMsg.contains("503") ||
                        errMsg.contains("high demand", ignoreCase = true)
                    ) {
                        break
                    }
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

    private fun formatMealContext(
        mealType: String,
        existingItemsContext: List<String>
    ): String {
        val sb = StringBuilder()
        if (mealType.isNotBlank()) {
            val friendlyMeal = when (mealType.uppercase(Locale.ROOT)) {
                "DESAYUNO" -> "Desayuno (primera comida del día)"
                "COMIDA", "ALMUERZO" -> "Comida / Almuerzo principal del mediodía"
                "MERIENDA" -> "Merienda de la tarde"
                "CENA" -> "Cena de la noche"
                "SNACK", "TENTEMPIÉ" -> "Snack / Tentempié entre horas"
                else -> mealType
            }
            sb.append("\nCONTEXTO DE LA TOMA: El usuario está registrando su $friendlyMeal.")
        }
        if (existingItemsContext.isNotEmpty()) {
            sb.append("\nPLATOS YA REGISTRADOS EN ESTA TOMA HOY: ")
            sb.append(existingItemsContext.joinToString(", "))
            sb.append(". Úsalo como referencia de lo que ya lleva consumido en esta comida (ej: para entender si añade un segundo plato, bebida o postre) y NO dupliques los que ya estén registrados salvo que el usuario lo pida expresamente.")
        }
        return sb.toString()
    }

    private fun buildVisionPrompt(
        userDescription: String,
        mealType: String = "",
        existingItemsContext: List<String> = emptyList()
    ): String {
        val extra = if (userDescription.isNotBlank()) {
            "\nINFORMACIÓN ADICIONAL DEL USUARIO: \"$userDescription\". Prioriza estos ingredientes y marcas indicadas."
        } else ""
        val contextInfo = formatMealContext(mealType, existingItemsContext)

        return """
            Eres un nutricionista experto y especialista en reconocimiento visual de platos de comida española, supermercados (Mercadona/Hacendado, Carrefour, Día, Alcampo, Lidl, etc.) y productos comerciales.
            Analiza cuidadosamente la imagen adjunta.$extra$contextInfo
            
            Tu objetivo:
            1. PLATOS COMBINADOS Y GUARNICIONES ("X CON Y") Y RACIONES NORMALES:
               - Si en la foto o en la descripción hay un plato combinado o plato con guarnición (ej: pechuga de pollo con patatas, entrecot con ensalada, pescado con verduras, salmón con arroz, tortilla con ensalada):
                 * Desglosa de forma clara y separada cada componente en una entrada INDEPENDIENTE del array JSON:
                   - Alimento principal (pechuga, ternera, salmón, etc.) con su ración normal (~150-180g).
                   - Cada guarnición (patatas al horno/fritas, ensalada mixta, arroz blanco, verduras) con su ración normal (~150-180g).
                   - Bebida, pan o postre con sus raciones normales.
               - Si es un plato tradicional ya cocinado todo junto (tortilla de patatas, paella, lentejas estofadas, cocido), regístralo como un plato completo preparado (1 ración estándar) y no lo separes en patatas o cebolla crudas.
            2. AUDITORÍA EXHAUSTIVA DE ELEMENTOS (NUNCA OMITIR ALIMENTOS NI BEBIDAS):
               - Desglosa individualmente CADA componente, plato, bebida, pan, salsa o postre presente en la foto o mencionado en la descripción.
               - Si en la foto o en la descripción hay un plato principal, una bebida, pan o postre, DEBES crear una entrada INDEPENDIENTE en el array JSON para CADA UNO de ellos.
               - CADA BEBIDA (agua, cerveza, café, refresco, vino, zumo...) DEBE ser un elemento independiente.
               - CADA POSTRE O FRUTA (manzana, yogur, plátano, fruta...) DEBE ser un elemento independiente.
               - ACOMPAÑAMIENTOS Y SALSAS notables (pan, patatas fritas, ensalada lateral, mayonesa, alioli...) DEBEN ser elementos independientes en el JSON para permitir al usuario editarlos o eliminarlos por separado.
            3. REGLA ESTRICTA SOBRE EL CAFÉ:
               - NUNCA supongas si un café lleva más o menos leche según la hora o la comida (ni en el desayuno ni después de comer).
               - Si se detecta o pide café solo sin especificar leche, trátalo como café solo estándar (~2 a 4 kcal).
               - Si se indica o detecta café con leche, cortado, manchado o capuchino, calcula los gramos y calorías de café + leche correspondientes a esa bebida exacta sin alterar las proporciones según la hora.
            3. PLATOS COMPUESTOS TRADICIONALES:
               - Platos típicos españoles (ej: tortilla de patatas, paella, lentejas estofadas con chorizo, cocido madrileño, lasaña, etc.): regístralos como el plato preparado completo (una ración con sus calorías y macronutrientes totales). NO los desgloses en ingredientes crudos aislados (no pongas cebolla cruda, patata cruda, etc.).
            4. CÁLCULO Y COMPRENSIÓN AVANZADA DE UNIDADES Y PRODUCTOS DE SUPERMERCADO:
               - Cuando en la descripción o en la imagen se identifiquen alimentos por UNIDAD, PIEZA, LATA, REBANADA, TARRINA, TAZA, VASO, PAQUETE, etc., DEBES interpretar y convertir esa unidad a su peso neto real en gramos, e indicar la unidad en el nombre del alimento (ej: "Huevos fritos (2 unidades)", "Atún claro al natural Hacendado (1 lata)", "Pan de molde integral Carrefour (2 rebanadas)", "Yogur de proteínas fresa Hacendado (1 tarrina)").
               - TABLA DE EQUIVALENCIAS DE UNIDADES (GENÉRICO Y SUPERMERCADO ESPAÑOL):
                 * Huevos: 1 ud = ~60g; 2 uds = ~120g; 3 uds = ~180g.
                 * Latas de conserva de supermercado (atún Hacendado/Carrefour/Día, mejillones, berberechos, maíz): 1 lata individual = ~60g escurrido (~80g con líquido).
                 * Pan de molde (Hacendado, Carrefour, Bimbo): 1 rebanada = 28-30g; 2 rebanadas = 56-60g.
                 * Tostadas de pan / biscotes: 1 tostada = 12-15g.
                 * Yogures y postres lácteos (Hacendado, Danone, Milbona): 1 tarrina/vasito = 120-125g; copa de proteínas = 200g; tarrina queso fresco batido individual = 120g (tarrina grande = 500g).
                 * Leche y bebidas vegetales: 1 vaso = 200-250 ml; 1 tazón = 300 ml; 1 brick = 1000 ml.
                 * Frutas por pieza: 1 manzana, pera o naranja = 150-180g; 1 plátano = 120g; 1 kiwi o mandarina = 75g; medio aguacate = 80-90g.
                 * Lonchas de embutido / pavo / jamón / queso (Hacendado, etc.): 1 loncha = 20-25g; paquete lonchas completo = 100-120g.
                 * Cerveza y refrescos: 1 lata = 330 ml; 1 tercio/botellín = 330 ml; 1 quinto/botellita = 200 ml; 1 caña = 200 ml; 1 doble = 350-400 ml.
                 * Cucharadas soperas (aceite de oliva, etc.): 1 cucharada = 12-15 ml (~110-130 kcal para aceite); 1 cucharadita = 5 ml.
                 * Tortitas de arroz o maíz (Hacendado, etc.): 1 tortita = 8-9g; 2 tortitas = 16-18g.
                 * Onzas de chocolate: 1 onza/pastilla = 10-12g; 1 tableta = 100g.
                 * Filetes de pechuga / ternera / pescado: 1 filete = 120-150g; 1 hamburguesa = 120-150g.
                 * Bricks de caldo o gazpacho: 1 vaso = 200-250 ml; brick completo = 1000 ml.
                 * Puñado (frutos secos): ~25-30g.
                 * Platos preparados típicos: 1 ración/plato = 250-350g; media ración = 150-180g.
            5. REGLA ESTRICTA DE MARCA VS GENÉRICO:
               - Si en la foto se aprecia un producto de marca o envase comercial, o el usuario indica en su descripción que proviene de una marca/supermercado concreto (ej: "Hacendado", "Mercadona", "Carrefour", "Día", "Alcampo", "Lidl", "Danone", "Asturiana", etc.), asigna exactamente esa marca en "brand" (ej: "Hacendado", "Carrefour", "Día", "Lidl") y pon "isGeneric": false.
               - Si el alimento es comida casera, no empaquetada, o NO se especifica marca alguna, asígnalo como genérico: pon "brand": "Genérico" y "isGeneric": true.
            6. REGLA DE CALORÍAS REALES (NINGÚN ALIMENTO TIENE 0 KCAL EXCEPTO AGUA):
               - Café solo (expreso o americano): ~2 a 4 kcal por taza. NUNCA pongas 0 calorías.
               - Cerveza con alcohol (lata/botellín 330ml): ~140-165 kcal (~43-48 kcal/100ml). NUNCA pongas 0 calorías.
               - Cerveza 0,0% sin alcohol (330ml): ~75-85 kcal (~24 kcal/100ml). NUNCA pongas 0 calorías.
               - Leche semidesnatada (vaso 200ml): ~92 kcal (~46 kcal/100ml, P: 6.4g, C: 9.6g, G: 3.2g).
               - Salsas (mayonesa, alioli, barbacoa, césar, etc.): tienen entre 60 y 650 kcal/100g. NUNCA pongas 0 calorías.
               - Calcular con rigor: calorías (kcal), proteínas (g), carbohidratos (g) y grasas (g) reales de cada uno.
            
            Responde ÚNICAMENTE con un array JSON válido con la siguiente estructura:
            [
              {
                "name": "Pechuga de pollo a la plancha",
                "brand": "Genérico",
                "isGeneric": true,
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
     * y genera automáticamente los alimentos, porciones y macronutrientes con contexto.
     */
    suspend fun analyzeFoodText(
        userDescription: String,
        mealType: String = "",
        existingItemsContext: List<String> = emptyList()
    ): FoodAiResult = withContext(Dispatchers.IO) {
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
            val contextInfo = formatMealContext(mealType, existingItemsContext)
            val prompt = """
                Eres un nutricionista experto en gastronomía española, supermercados (Mercadona/Hacendado, Carrefour, Día, Alcampo, Lidl, etc.) y cálculo exacto de macronutrientes.
                El usuario ha registrado en lenguaje natural lo que ha comido: "$trimmed"$contextInfo
                
                Tu objetivo primordial:
                1. PLATOS COMBINADOS Y GUARNICIONES ("X CON Y"):
                   - Si el usuario dice "X con Y" (ej: "pechuga de pollo con patatas", "entrecot con ensalada", "arroz con atún y huevo frito", "salmón con verduras salteadas", "merluza con patatas panaderas", "tortilla de patatas con ensalada mixta", "lentejas con arroz"):
                     * Desglosa de forma clara y separada cada alimento en el array JSON:
                       - 1 entrada para el alimento principal (ej. Pechuga de pollo a la plancha, Entrecot, Salmón a la plancha, etc.).
                       - 1 entrada para cada guarnición o acompañamiento (ej. Patatas al horno, Ensalada mixta, Arroz blanco, Verduras salteadas).
                       - 1 entrada para pan, bebidas, salsas o postres si se mencionan.
                   - Si es un plato tradicional de cocción conjunta (ej: "Tortilla de patatas", "Paella valenciana", "Lentejas estofadas con chorizo", "Cocido madrileño", "Lasaña de carne", "Gazpacho andaluz"): regístralo como un plato completo preparado (1 ración estándar) y NO lo dividas en patatas crudas, cebolla cruda o huevo crudo.
                
                2. CÁLCULO DE LA RACIÓN NORMAL (SI NO SE INDICAN GRAMOS EXACTOS):
                   - Si el usuario no indica gramos numéricos exactos, DEBES calcular y asignar la RACIÓN NORMAL habitual de consumo para un adulto en España:
                     * Pechuga de pollo o pavo: ración normal ~150-160g (~250-264 kcal, ~48-50g P)
                     * Filete de ternera / entrecot: ración normal ~160-180g (~300-375 kcal, ~36-40g P)
                     * Pescado blanco o azul (merluza, salmón, dorada, lubina): ración normal ~150-170g (~150-240 kcal)
                     * Patatas de guarnición (al horno, cocidas o fritas): ración normal ~150-180g (~130-280 kcal)
                     * Arroz blanco o pasta (guarnición cocida): ración normal ~150-180g (como plato principal ~200-250g)
                     * Ensalada mixta (lechuga, tomate, cebolla, aceite): ración normal ~150g (~90-120 kcal)
                     * Verduras salteadas o al vapor (brócoli, judías, calabacín, setas): ración normal ~150-180g (~60-90 kcal)
                     * Pan común de barra: ración normal ~40-50g (~110-130 kcal) o 2 rebanadas molde (~56-60g)
                     * Legumbres preparadas (lentejas, garbanzos): ración normal ~300-350g (~350-420 kcal)
                     * Tortilla de patatas: ración normal ~180g (~280-320 kcal)
                
                3. AUDITORÍA EXHAUSTIVA DE ELEMENTOS (NUNCA OMITIR ALIMENTOS NI BEBIDAS):
                   - Escanea minuciosamente toda la descripción del usuario.
                   - Si el usuario menciona múltiples platos, guarniciones, panes, bebidas, postres o salsas (ej: "arroz con pollo, ensalada mixta, dos rebanadas de pan, una cerveza y de postre una manzana"), DEBES devolver una entrada INDEPENDIENTE en el array JSON para CADA UNO de ellos.
                   - CADA BEBIDA (agua, cerveza, refresco, café, leche, vino, zumo...) DEBE ser un elemento independiente.
                   - CADA POSTRE O FRUTA (manzana, yogur, plátano, fruta...) DEBE ser un elemento independiente.
                   - CADA ACOMPAÑAMIENTO O PAN (pan blanco, integral, patatas fritas, ensalada lateral...) DEBE ser un elemento independiente.
                   - SALSAS O EXTRAS NOTORIOS (mayonesa, alioli, kétchup, queso rallado, aceite extra...) DEBEN ser elementos independientes.
                
                4. REGLA ESTRICTA SOBRE EL CAFÉ:
                   - Si el usuario dice "café" a secas sin especificar leche, trátalo como café solo estándar (~2 a 4 kcal).
                   - Si dice "café con leche", "cortado", "manchado" o "capuchino", calcula los gramos y calorías de café + leche correspondientes a esa bebida exacta sin alterar las proporciones según la hora.
                
                5. CÁLCULO Y COMPRENSIÓN AVANZADA DE UNIDADES Y PRODUCTOS DE SUPERMERCADO:
                   - Cuando el usuario indique alimentos por UNIDAD, PIEZA, LATA, REBANADA, TARRINA, TAZA, VASO, PAQUETE, etc., DEBES interpretar y convertir esa unidad a su peso neto real en gramos, e indicar la unidad en el nombre del alimento (ej: "Huevos fritos (2 unidades)", "Atún claro al natural Hacendado (1 lata)", "Pan de molde integral Carrefour (2 rebanadas)", "Yogur de proteínas fresa Hacendado (1 tarrina)").
                   - TABLA DE EQUIVALENCIAS DE UNIDADES (GENÉRICO Y SUPERMERCADO ESPAÑOL):
                     * Huevos: 1 ud = ~60g; 2 uds = ~120g; 3 uds = ~180g.
                     * Latas de conserva de supermercado (atún Hacendado/Carrefour/Día, mejillones, berberechos, maíz): 1 lata individual = ~60g escurrido (~80g con líquido).
                     * Pan de molde (Hacendado, Carrefour, Bimbo): 1 rebanada = 28-30g; 2 rebanadas = 56-60g.
                     * Tostadas de pan / biscotes: 1 tostada = 12-15g.
                     * Yogures y postres lácteos (Hacendado, Danone, Milbona): 1 tarrina/vasito = 120-125g; copa de proteínas = 200g; tarrina queso fresco batido individual = 120g (tarrina grande = 500g).
                     * Leche y bebidas vegetales: 1 vaso = 200-250 ml; 1 tazón = 300 ml; 1 brick = 1000 ml.
                     * Frutas por pieza: 1 manzana, pera o naranja = 150-180g; 1 plátano = 120g; 1 kiwi o mandarina = 75g; medio aguacate = 80-90g.
                     * Lonchas de embutido / pavo / jamón / queso (Hacendado, etc.): 1 loncha = 20-25g; paquete lonchas completo = 100-120g.
                     * Cerveza y refrescos: 1 lata = 330 ml; 1 tercio/botellín = 330 ml; 1 quinto/botellita = 200 ml; 1 caña = 200 ml; 1 doble = 350-400 ml.
                     * Cucharadas soperas (aceite de oliva, etc.): 1 cucharada = 12-15 ml (~110-130 kcal para aceite); 1 cucharadita = 5 ml.
                     * Tortitas de arroz o maíz (Hacendado, etc.): 1 tortita = 8-9g; 2 tortitas = 16-18g.
                     * Onzas de chocolate: 1 onza/pastilla = 10-12g; 1 tableta = 100g.
                     * Filetes de pechuga / ternera / pescado: 1 filete = 120-150g; 1 hamburguesa = 120-150g.
                     * Bricks de caldo o gazpacho: 1 vaso = 200-250 ml; brick completo = 1000 ml.
                     * Puñado (frutos secos): ~25-30g.
                     * Platos preparados típicos: 1 ración/plato = 250-350g; media ración = 150-180g.
                
                6. REGLA ESTRICTA DE MARCA VS GENÉRICO:
                   - Si el usuario indica o menciona que un producto viene de una marca o supermercado específico (por ejemplo: "hacendado", "mercadona", "carrefour", "día", "dia", "alcampo", "lidl", "danone", "central lechera asturiana", etc.), asigna exactamente esa marca en el campo "brand" (ej: "Hacendado", "Carrefour", "Día", "Lidl") y pon "isGeneric": false.
                   - Si el usuario NO menciona ninguna marca o supermercado para ese producto (ej: "arroz blanco", "2 huevos fritos", "pechuga de pollo"), asígnalo como genérico: pon "brand": "Genérico" y "isGeneric": true.
                
                7. REGLA DE CALORÍAS REALES (NINGÚN ALIMENTO TIENE 0 KCAL EXCEPTO AGUA):
                   - Café solo (expreso o americano): ~2 a 4 kcal por taza (~2 kcal/100ml). NUNCA pongas 0 calorías.
                   - Cerveza con alcohol (lata/botellín 330ml): ~140-165 kcal (~43-48 kcal/100ml). NUNCA pongas 0 calorías.
                   - Cerveza 0,0% sin alcohol (330ml): ~75-85 kcal (~24 kcal/100ml). NUNCA pongas 0 calorías.
                   - Leche semidesnatada (vaso 200ml): ~92 kcal (~46 kcal/100ml, P: 6.4g, C: 9.6g, G: 3.2g).
                   - Salsas (mayonesa, alioli, barbacoa, césar, etc.): tienen entre 60 y 650 kcal/100g. NUNCA pongas 0 calorías.
                   - Calcula con precisión médica calorías (kcal), proteínas (g), carbohidratos (g) y grasas (g).
                
                Responde ÚNICAMENTE con un array JSON válido:
                [
                  {
                    "name": "Pechuga de pollo a la plancha",
                    "brand": "Genérico",
                    "isGeneric": true,
                    "grams": 160.0,
                    "calories": 264.0,
                    "protein": 49.6,
                    "carbs": 0.0,
                    "fat": 5.8
                  }
                ]
            """.trimIndent()

            val candidateModels = GeminiModelManager.getCandidateModels(apiKey, userGoals.geminiModel)
            var responseText = ""

            for (modelName in candidateModels) {
                try {
                    val model = GenerativeModel(
                        modelName = modelName,
                        apiKey = apiKey,
                        generationConfig = generationConfig {
                            temperature = 0.15f
                            maxOutputTokens = 2048
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
                    val errMsg = e.message ?: ""
                    Log.w(TAG, "Error en analyzeFoodText con $modelName: $errMsg")
                    if (errMsg.contains("quota", ignoreCase = true) ||
                        errMsg.contains("503") ||
                        errMsg.contains("high demand", ignoreCase = true)
                    ) {
                        break
                    }
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
     * Estima e infiere con motor local los valores nutricionales por 100g y porción
     * de cualquier alimento buscado en el catálogo sin consumir cuota de Gemini.
     */
    suspend fun estimateFoodCatalogItem(query: String, category: FoodSourceCategory): FoodCatalogItem? = withContext(Dispatchers.Default) {
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

        // Estimación directa con el motor local sin gastar cuota de API
        val localList = parseFoodsFromTextLocally(trimmed)
        val first = localList.firstOrNull()
        if (first != null) {
            val factor = if (first.grams > 0) 100f / first.grams else 1f
            val imgUrl = RealFoodImageResolver.resolve(first.name, brandName)
            var rawKcal = first.calories * factor
            var rawP = first.protein * factor
            var rawC = first.carbs * factor
            var rawF = first.fat * factor

            if (rawKcal <= 0f && (rawP > 0f || rawC > 0f || rawF > 0f)) {
                rawKcal = (rawP * 4f) + (rawC * 4f) + (rawF * 9f)
            }
            if (rawKcal <= 0f && !first.name.contains("agua", ignoreCase = true)) {
                val inf = FoodCatalogService.inferMacrosIfZero(first.name, 0f, rawP, rawC, rawF)
                rawKcal = inf.calories
                if (rawP <= 0f) rawP = inf.protein
                if (rawC <= 0f) rawC = inf.carbs
                if (rawF <= 0f) rawF = inf.fat
            }

            return@withContext FoodCatalogItem(
                id = "ai_local_${trimmed.hashCode()}",
                name = first.name,
                brand = brandName,
                caloriesPer100g = rawKcal,
                proteinPer100g = rawP,
                carbsPer100g = rawC,
                fatPer100g = rawF,
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
                var calories = obj.optDouble("calories", 0.0).toFloat()
                var protein = obj.optDouble("protein", 0.0).toFloat()
                var carbs = obj.optDouble("carbs", 0.0).toFloat()
                var fat = obj.optDouble("fat", 0.0).toFloat()

                // Si la IA devolvió 0 o negativo en calorías pero hay macronutrientes:
                if (calories <= 0f && (protein > 0f || carbs > 0f || fat > 0f)) {
                    calories = (protein * 4f) + (carbs * 4f) + (fat * 9f)
                }
                // Si sigue siendo 0 o menor y no es agua, inferir según el alimento (café solo, cerveza, salsa, etc.)
                if (calories <= 0f && !name.contains("agua", ignoreCase = true)) {
                    val factor = if (grams > 0f) grams / 100f else 1f
                    val inf = FoodCatalogService.inferMacrosIfZero(name, 0f, protein, carbs, fat)
                    calories = inf.calories * factor
                    if (protein <= 0f) protein = inf.protein * factor
                    if (carbs <= 0f) carbs = inf.carbs * factor
                    if (fat <= 0f) fat = inf.fat * factor
                }

                val rawBrand = obj.optString("brand").trim()
                val isGenericExplicit = obj.optBoolean("isGeneric", true)
                val isGeneric = when {
                    rawBrand.isBlank() -> true
                    rawBrand.equals("genérico", ignoreCase = true) ||
                    rawBrand.equals("generico", ignoreCase = true) ||
                    rawBrand.equals("casero", ignoreCase = true) ||
                    rawBrand.equals("básico", ignoreCase = true) ||
                    rawBrand.equals("basico", ignoreCase = true) -> true
                    else -> isGenericExplicit
                }
                val brand = if (isGeneric) "Genérico" else rawBrand.replaceFirstChar { it.uppercase() }

                items.add(
                    DetectedFoodItem(
                        name = name.replaceFirstChar { it.uppercase() },
                        grams = if (grams > 0) grams else 100f,
                        calories = calories,
                        protein = protein,
                        carbs = carbs,
                        fat = fat,
                        brand = brand,
                        isGeneric = isGeneric
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

        // 1. Extraer multiplicadores de cantidad y unidades (ej: "2 huevos", "1 lata de atún", "3 rebanadas de pan", "150g de pollo")
        fun findGramsOrMultiplier(segment: String, defaultGrams: Float, unitMultiplier: Float = 1f): Float {
            val s = segment.lowercase(Locale.ROOT)
            // Buscar gramos explícitos: ej "150g" o "150 gramos"
            val gramMatch = Pattern.compile("(\\d+)\\s*(?:g|gr|gramos|ml|cl)", Pattern.CASE_INSENSITIVE).matcher(s)
            if (gramMatch.find()) {
                val g = gramMatch.group(1)?.toFloatOrNull()
                if (g != null && g > 0) return g
            }

            // Detectar multiplicador numérico
            val count: Float = run {
                val countMatch = Pattern.compile("\\b(\\d+(?:[.,]\\d+)?)\\b").matcher(s)
                if (countMatch.find()) {
                    countMatch.group(1)?.replace(',', '.')?.toFloatOrNull() ?: 1f
                } else when {
                    s.contains("dos") || s.contains("par de") -> 2f
                    s.contains("tres") -> 3f
                    s.contains("cuatro") -> 4f
                    s.contains("cinco") -> 5f
                    s.contains("seis") -> 6f
                    s.contains("medio") || s.contains("media") -> 0.5f
                    s.contains("un ") || s.contains("una ") || s.contains("uno ") -> 1f
                    else -> 1f
                }
            }

            // Detectar tipo de unidad de medida
            return when {
                s.contains("lata") -> {
                    val unitW = if (s.contains("cerveza") || s.contains("refresco") || s.contains("coca")) 330f else 60f
                    count * unitW
                }
                s.contains("rebanada") -> count * 28f
                s.contains("loncha") -> count * 25f
                s.contains("huevo") -> count * 60f
                s.contains("tostada") -> count * 15f
                s.contains("yogur") || s.contains("tarrina") -> {
                    val unitW = if (s.contains("copa") || s.contains("batido")) 200f else 125f
                    count * unitW
                }
                s.contains("vaso") -> count * 200f
                s.contains("taza") -> count * 150f
                s.contains("tazón") || s.contains("tazon") -> count * 300f
                s.contains("cucharada") || s.contains("cda") -> count * 14f
                s.contains("cucharadita") || s.contains("cdita") -> count * 5f
                s.contains("tortita") -> count * 9f
                s.contains("onza") -> count * 10f
                s.contains("paquete") || s.contains("barqueta") -> count * 120f
                s.contains("filete") -> count * 130f
                s.contains("hamburguesa") -> count * 130f
                s.contains("tercio") || s.contains("botellín") -> count * 330f
                s.contains("caña") -> count * 200f
                count in 1f..15f && count != 1f -> count * defaultGrams
                else -> defaultGrams * unitMultiplier
            }
        }

        // Proteger expresiones culinarias compuestas habituales para no partirlas por "con"
        var normalized = lower
        val compoundReplacements = listOf(
            "cafe con leche" to "cafe_con_leche",
            "café con leche" to "café_con_leche",
            "arroz con pollo" to "arroz_con_pollo",
            "arroz con tomate" to "arroz_con_tomate",
            "lentejas con chorizo" to "lentejas_con_chorizo",
            "pan con tomate" to "pan_con_tomate",
            "pan con aceite" to "pan_con_aceite",
            "pan con mantequilla" to "pan_con_mantequilla",
            "tostada con tomate" to "tostada_con_tomate",
            "tostada con aceite" to "tostada_con_aceite",
            "tostada con jamon" to "tostada_con_jamón",
            "tostada con jamón" to "tostada_con_jamón",
            "tostada con mantequilla" to "tostada_con_mantequilla",
            "leche con colacao" to "leche_con_colacao",
            "leche con cereales" to "leche_con_cereales",
            "macarrones con queso" to "macarrones_con_queso",
            "macarrones con tomate" to "macarrones_con_tomate",
            "pasta con tomate" to "pasta_con_tomate",
            "pasta con atun" to "pasta_con_atún",
            "pasta con atún" to "pasta_con_atún",
            "ensalada con atun" to "ensalada_con_atún",
            "ensalada con atún" to "ensalada_con_atún",
            "tortilla de patata" to "tortilla_de_patatas",
            "tortilla de patatas" to "tortilla_de_patatas",
            "huevos con jamon" to "huevos_con_jamón",
            "huevos con jamón" to "huevos_con_jamón"
        )
        for ((phrase, safeToken) in compoundReplacements) {
            normalized = normalized.replace(phrase, safeToken)
        }

        // Dividir la descripción por conectores de múltiples platos
        val rawClauses = normalized
            .split(Regex("[,;+\\n]+|\\by\\b|\\be\\b|\\bcon\\b|\\bmás\\b|\\bmas\\b|\\bluego\\b|\\bdespues\\b|\\bdespués\\b"))
            .map { it.replace('_', ' ').trim() }
            .filter { it.isNotBlank() }

        // 2. Detección de marcas y supermercados específicos en el texto
        fun detectBrandForSegment(segment: String): Pair<String, Boolean> {
            val s = segment.lowercase(Locale.ROOT)
            return when {
                s.contains("hacendado") -> "Hacendado" to false
                s.contains("mercadona") -> "Mercadona" to false
                s.contains("carrefour") -> "Carrefour" to false
                s.contains("alcampo") || s.contains("auchan") -> "Alcampo" to false
                s.contains("lidl") || s.contains("milbona") -> "Lidl" to false
                s.contains("danone") -> "Danone" to false
                s.contains("aldi") -> "Aldi" to false
                s.contains("eroski") -> "Eroski" to false
                s.contains("supercor") || s.contains("corte ingles") || s.contains("corte inglés") -> "El Corte Inglés" to false
                s.contains("central lechera") || s.contains("asturiana") -> "Central Lechera Asturiana" to false
                Regex("\\b(del?|en)\\s+d[ií]a\\b").containsMatchIn(s) || Regex("\\bd[ií]a\\b").containsMatchIn(s) -> "Día" to false
                else -> {
                    // Si el usuario escribió un único alimento (una sola frase/cláusula) y en él estaba la marca
                    if (rawClauses.size <= 1) {
                        val full = lower
                        when {
                            full.contains("hacendado") -> "Hacendado" to false
                            full.contains("mercadona") -> "Mercadona" to false
                            full.contains("carrefour") -> "Carrefour" to false
                            full.contains("alcampo") || full.contains("auchan") -> "Alcampo" to false
                            full.contains("lidl") || full.contains("milbona") -> "Lidl" to false
                            full.contains("danone") -> "Danone" to false
                            full.contains("aldi") -> "Aldi" to false
                            full.contains("eroski") -> "Eroski" to false
                            full.contains("supercor") || full.contains("corte ingles") || full.contains("corte inglés") -> "El Corte Inglés" to false
                            full.contains("central lechera") || full.contains("asturiana") -> "Central Lechera Asturiana" to false
                            Regex("\\b(del?|en)\\s+d[ií]a\\b").containsMatchIn(full) -> "Día" to false
                            else -> "Genérico" to true
                        }
                    } else {
                        "Genérico" to true
                    }
                }
            }
        }

        for (clause in rawClauses) {
            val countBefore = items.size
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
                // Platos compuestos tradicionales
                clause.contains("arroz con pollo") -> {
                    val grams = findGramsOrMultiplier(clause, 280f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Arroz con pollo", grams, 140f * factor, 9.8f * factor, 17f * factor, 2.8f * factor))
                }
                clause.contains("arroz con tomate") -> {
                    val grams = findGramsOrMultiplier(clause, 220f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Arroz blanco con salsa de tomate", grams, 125f * factor, 2.5f * factor, 25f * factor, 1.2f * factor))
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
                clause.contains("solomillo") || clause.contains("entrecot") || clause.contains("chuleta") || clause.contains("chuleton") || clause.contains("chuletón") -> {
                    val grams = findGramsOrMultiplier(clause, 180f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Entrecot / Solomillo a la plancha", grams, 210f * factor, 25f * factor, 0f, 12f * factor))
                }
                clause.contains("ternera") || clause.contains("filete") || clause.contains("carne") -> {
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
                clause.contains("tostada con tomate") || clause.contains("pan con tomate") -> {
                    val grams = findGramsOrMultiplier(clause, 80f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Tostada con tomate natural y aceite de oliva", grams, 195f * factor, 3.8f * factor, 26f * factor, 8.2f * factor))
                }
                clause.contains("tostada con jamon") || clause.contains("tostada con jamón") || clause.contains("pan con jamon") || clause.contains("pan con jamón") -> {
                    val grams = findGramsOrMultiplier(clause, 90f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Tostada con tomate, aceite y jamón serrano", grams, 235f * factor, 9.5f * factor, 26f * factor, 10.5f * factor))
                }
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
                    val grams = findGramsOrMultiplier(clause, 150f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Ensalada mixta fresca", grams, 65f * factor, 2.5f * factor, 4f * factor, 4.5f * factor))
                }
                clause.contains("verdura") || clause.contains("verduras") || clause.contains("salteado") || clause.contains("menestra") -> {
                    val grams = findGramsOrMultiplier(clause, 160f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Verduras salteadas o al vapor", grams, 45f * factor, 2.5f * factor, 7f * factor, 0.8f * factor))
                }
                clause.contains("brocoli") || clause.contains("brócoli") -> {
                    val grams = findGramsOrMultiplier(clause, 150f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Brócoli al vapor", grams, 35f * factor, 2.4f * factor, 7.2f * factor, 0.4f * factor))
                }
                clause.contains("champin") || clause.contains("champiñ") || clause.contains("seta") || clause.contains("setas") || clause.contains("hongo") -> {
                    val grams = findGramsOrMultiplier(clause, 150f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Champiñones / Setas a la plancha", grams, 38f * factor, 3.8f * factor, 4.5f * factor, 0.5f * factor))
                }
                clause.contains("esparrago") || clause.contains("espárrago") || clause.contains("esparragos") || clause.contains("espárragos") -> {
                    val grams = findGramsOrMultiplier(clause, 140f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Espárragos trigueros a la plancha", grams, 25f * factor, 2.8f * factor, 3.2f * factor, 0.2f * factor))
                }
                clause.contains("judia") || clause.contains("judía") || clause.contains("vainas") -> {
                    val grams = findGramsOrMultiplier(clause, 180f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Judías verdes cocidas", grams, 35f * factor, 2.0f * factor, 7.5f * factor, 0.2f * factor))
                }
                clause.contains("calabacin") || clause.contains("calabacín") || clause.contains("berenjena") -> {
                    val grams = findGramsOrMultiplier(clause, 160f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Calabacín / Berenjena a la plancha", grams, 32f * factor, 1.8f * factor, 5.5f * factor, 0.4f * factor))
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
                clause.contains("yogur") || clause.contains("yogurt") -> {
                    val isGriego = clause.contains("griego")
                    val isProteina = clause.contains("proteina") || clause.contains("proteína") || clause.contains("+protein")
                    val grams = findGramsOrMultiplier(clause, 125f)
                    val factor = grams / 100f
                    when {
                        isProteina -> items.add(DetectedFoodItem("Yogur +Proteínas", grams, 52f * factor, 10f * factor, 3f * factor, 0.1f * factor))
                        isGriego -> items.add(DetectedFoodItem("Yogur griego natural", grams, 120f * factor, 6f * factor, 4.5f * factor, 9f * factor))
                        else -> items.add(DetectedFoodItem("Yogur natural", grams, 65f * factor, 4.5f * factor, 4.8f * factor, 3.2f * factor))
                    }
                }
                clause.contains("queso") -> {
                    val grams = findGramsOrMultiplier(clause, 50f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Queso tierno / semicurado", grams, 350f * factor, 23f * factor, 1.5f * factor, 28f * factor))
                }
                // Desayuno: Café (con o sin leche, solo o cortado)
                clause.contains("cafe") || clause.contains("café") -> {
                    val hasMilk = clause.contains("leche")
                    val isCortado = clause.contains("cortado")
                    when {
                        hasMilk -> items.add(DetectedFoodItem("Café con leche", 200f, 110f, 6.4f, 10f, 4.8f))
                        isCortado -> items.add(DetectedFoodItem("Café cortado", 100f, 38f, 2.2f, 3.5f, 1.6f))
                        clause.contains("americano") -> items.add(DetectedFoodItem("Café solo americano", 150f, 3f, 0.3f, 0.4f, 0f))
                        else -> items.add(DetectedFoodItem("Café solo expreso", 60f, 2f, 0.2f, 0.3f, 0f))
                    }
                }
                // Leche (sin café)
                clause.contains("leche") -> {
                    val grams = findGramsOrMultiplier(clause, 200f)
                    val factor = grams / 100f
                    when {
                        clause.contains("desnatada") -> items.add(DetectedFoodItem("Leche desnatada", grams, 35f * factor, 3.3f * factor, 4.9f * factor, 0.2f * factor))
                        clause.contains("entera") -> items.add(DetectedFoodItem("Leche entera", grams, 63f * factor, 3.1f * factor, 4.7f * factor, 3.6f * factor))
                        clause.contains("avena") -> items.add(DetectedFoodItem("Bebida de avena", grams, 45f * factor, 1f * factor, 7.5f * factor, 1.2f * factor))
                        clause.contains("soja") -> items.add(DetectedFoodItem("Bebida de soja", grams, 38f * factor, 3.2f * factor, 1.8f * factor, 1.8f * factor))
                        clause.contains("almendra") -> items.add(DetectedFoodItem("Bebida de almendras", grams, 24f * factor, 0.5f * factor, 3f * factor, 1.1f * factor))
                        else -> items.add(DetectedFoodItem("Leche semidesnatada", grams, 46f * factor, 3.2f * factor, 4.8f * factor, 1.6f * factor))
                    }
                }
                // Desayuno: Avena
                clause.contains("avena") -> {
                    val grams = findGramsOrMultiplier(clause, 50f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Copos de avena integral", grams, 375f * factor, 13.5f * factor, 59f * factor, 7f * factor))
                }
                // Bebidas: Cervezas y Vino
                clause.contains("cerveza") || clause.contains("caña") || clause.contains("cana") ||
                clause.contains("tercio") || clause.contains("botellin") || clause.contains("botellín") ||
                clause.contains("quinto") || clause.contains("birra") || clause.contains("radler") || clause.contains("clara") -> {
                    val is00 = clause.contains("0,0") || clause.contains("0.0") || clause.contains("sin alcohol") || clause.contains("cero")
                    val isRadler = clause.contains("radler") || clause.contains("clara") || clause.contains("limon") || clause.contains("limón")
                    val isTostada = clause.contains("tostada") || clause.contains("especial")
                    val defaultG = if (clause.contains("tercio") || clause.contains("lata") || clause.contains("botellin") || clause.contains("botellín")) 330f else 250f
                    val grams = findGramsOrMultiplier(clause, defaultG)
                    val factor = grams / 100f
                    when {
                        is00 -> items.add(DetectedFoodItem("Cerveza 0,0% sin alcohol", grams, 24f * factor, 0.3f * factor, 5.2f * factor, 0f))
                        isRadler -> items.add(DetectedFoodItem("Cerveza Radler / Clara con limón", grams, 35f * factor, 0.2f * factor, 6.2f * factor, 0f))
                        isTostada -> items.add(DetectedFoodItem("Cerveza especial / tostada", grams, 48f * factor, 0.5f * factor, 3.8f * factor, 0f))
                        else -> items.add(DetectedFoodItem("Cerveza rubia con alcohol", grams, 43f * factor, 0.5f * factor, 3.5f * factor, 0f))
                    }
                }
                clause.contains("vino") -> {
                    val grams = findGramsOrMultiplier(clause, 150f)
                    val factor = grams / 100f
                    items.add(DetectedFoodItem("Copa de vino", grams, 75f * factor, 0.1f * factor, 2.5f * factor, 0f))
                }
                // Salsas y condimentos
                clause.contains("salsa") || clause.contains("mayonesa") || clause.contains("alioli") ||
                clause.contains("allioli") || clause.contains("ketchup") || clause.contains("kétchup") || clause.contains("mostaza") -> {
                    val grams = findGramsOrMultiplier(clause, 30f)
                    val factor = grams / 100f
                    when {
                        clause.contains("mayonesa") || clause.contains("alioli") || clause.contains("allioli") ->
                            items.add(DetectedFoodItem("Salsa mayonesa / alioli", grams, 645f * factor, 1.1f * factor, 2.5f * factor, 71f * factor))
                        clause.contains("barbacoa") ->
                            items.add(DetectedFoodItem("Salsa barbacoa", grams, 135f * factor, 1.2f * factor, 30f * factor, 0.4f * factor))
                        clause.contains("burger") ->
                            items.add(DetectedFoodItem("Salsa burger", grams, 290f * factor, 1.1f * factor, 12f * factor, 26f * factor))
                        clause.contains("cesar") || clause.contains("césar") ->
                            items.add(DetectedFoodItem("Salsa César", grams, 295f * factor, 1.5f * factor, 6.5f * factor, 30f * factor))
                        clause.contains("pesto") ->
                            items.add(DetectedFoodItem("Salsa pesto", grams, 460f * factor, 5.2f * factor, 6f * factor, 47f * factor))
                        clause.contains("soja") ->
                            items.add(DetectedFoodItem("Salsa de soja", grams, 65f * factor, 8.5f * factor, 7.5f * factor, 0.1f * factor))
                        clause.contains("ketchup") || clause.contains("kétchup") ->
                            items.add(DetectedFoodItem("Salsa kétchup", grams, 110f * factor, 1.2f * factor, 25f * factor, 0.2f * factor))
                        clause.contains("mostaza") ->
                            items.add(DetectedFoodItem("Salsa mostaza", grams, 90f * factor, 5f * factor, 5f * factor, 5f * factor))
                        clause.contains("brava") ->
                            items.add(DetectedFoodItem("Salsa brava", grams, 95f * factor, 1.2f * factor, 11f * factor, 5f * factor))
                        clause.contains("tomate") ->
                            items.add(DetectedFoodItem("Salsa de tomate frito", grams, 75f * factor, 1.5f * factor, 8.5f * factor, 4f * factor))
                        else ->
                            items.add(DetectedFoodItem("Salsa para acompañar", grams, 150f * factor, 1.5f * factor, 12f * factor, 10.5f * factor))
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

            // Asignar marca o genérico a los alimentos identificados en esta cláusula
            val (brand, isGeneric) = detectBrandForSegment(clause)
            for (idx in countBefore until items.size) {
                items[idx] = items[idx].copy(brand = brand, isGeneric = isGeneric)
            }
        }

        // Si no reconoció alimentos específicos pero el usuario escribió texto
        if (items.isEmpty()) {
            val fallbackG = findGramsOrMultiplier(lower, 150f)
            val (fallbackBrand, fallbackIsGen) = detectBrandForSegment(text)
            items.add(
                DetectedFoodItem(
                    name = text.trim().take(35).replaceFirstChar { it.uppercase() },
                    grams = fallbackG,
                    calories = 180f * (fallbackG / 100f),
                    protein = 12f * (fallbackG / 100f),
                    carbs = 18f * (fallbackG / 100f),
                    fat = 6f * (fallbackG / 100f),
                    brand = fallbackBrand,
                    isGeneric = fallbackIsGen
                )
            )
        }

        return items.distinctBy { it.name.lowercase() }
    }

    /**
     * Asesor / Coach Nutricional con IA: indica si el usuario necesita comer más, menos o qué comer.
     */
    /**
     * Asesor / Coach Nutricional: indica si el usuario necesita comer más, menos o qué comer.
     * Utiliza directamente el motor local heurístico para NO gastar la cuota de la API de Gemini,
     * reservando los usos exclusivamente para escaneo de fotos y texto libre de platos.
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
    ): String = withContext(Dispatchers.Default) {
        val proteinDebt = (targetProtein - protein).coerceAtLeast(0f)
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
