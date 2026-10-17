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

    private fun estimateFromDescription(description: String): List<DetectedFoodItem> {
        val lower = description.lowercase()
        val items = mutableListOf<DetectedFoodItem>()

        if (lower.contains("pollo") || lower.contains("pavo")) {
            items.add(DetectedFoodItem("Pechuga de pollo", 150f, 248f, 46.5f, 0f, 5.4f))
        }
        if (lower.contains("arroz")) {
            items.add(DetectedFoodItem("Arroz blanco cocido", 150f, 195f, 4.1f, 42f, 0.5f))
        }
        if (lower.contains("huevo")) {
            items.add(DetectedFoodItem("Huevos", 120f, 172f, 15f, 0.8f, 11.4f))
        }
        if (lower.contains("pasta") || lower.contains("macarr") || lower.contains("espaguet")) {
            items.add(DetectedFoodItem("Pasta cocida", 180f, 284f, 10.4f, 55.8f, 1.6f))
        }
        if (lower.contains("patata") || lower.contains("papa")) {
            items.add(DetectedFoodItem("Patatas cocidas", 200f, 172f, 4f, 37f, 0.2f))
        }
        if (lower.contains("aceite")) {
            items.add(DetectedFoodItem("Aceite de oliva", 10f, 88f, 0f, 0f, 10f))
        }
        if (lower.contains("ensalada") || lower.contains("lechuga")) {
            items.add(DetectedFoodItem("Ensalada mixta", 150f, 35f, 1.5f, 6f, 0.4f))
        }

        if (items.isEmpty()) {
            items.add(DetectedFoodItem(description.take(30), 200f, 300f, 15f, 35f, 10f))
        }
        return items
    }
}
