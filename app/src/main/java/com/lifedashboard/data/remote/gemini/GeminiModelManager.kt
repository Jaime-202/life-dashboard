package com.lifedashboard.data.remote.gemini

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiModelManager {
    private const val TAG = "GeminiModelManager"

    const val DEFAULT_MODEL = "gemini-3.8-flash"

    /**
     * Lista priorizada de modelos de respaldo modernos para Gemini.
     * Solo modelos probados de generación de texto y multimodal.
     */
    val FALLBACK_MODELS = listOf(
        "gemini-3.8-flash",
        "gemini-3.7-flash"
    )

    /**
     * Verifica que un modelo sea exclusivamente para generación de texto/contenido
     * y no un modelo de audio (TTS), embedding o generación de imágenes directa.
     */
    fun isCleanTextGenerationModel(name: String): Boolean {
        val lower = name.lowercase().trim()
        if (!lower.contains("gemini")) return false
        val excluded = listOf(
            "-tts", "tts", "-image", "imagen", "-embed", "embedding",
            "-realtime", "-live", "-audio", "3.8-flash-lite"
        )
        return excluded.none { lower.contains(it) }
    }

    /**
     * Modelos predefinidos recomendados para selector en la interfaz de usuario.
     */
    val PRESET_MODELS = listOf(
        "gemini-3.8-flash" to "⚡ Gemini 3.8 Flash (Recomendado)",
        "gemini-3.7-flash" to "✨ Gemini 3.7 Flash",
        "auto" to "🎯 Auto-detectar de mi cuenta"
    )

    /**
     * Devuelve una lista ordenada y limpia de modelos candidatos a probar (máximo 3).
     * Si [preferredModel] está establecido (y no es 'auto'), va en primer lugar.
     */
    suspend fun getCandidateModels(apiKey: String, preferredModel: String? = null): List<String> = withContext(Dispatchers.IO) {
        val candidates = linkedSetOf<String>()

        val preferred = preferredModel?.trim()
        if (!preferred.isNullOrBlank() && !preferred.equals("auto", ignoreCase = true) && isCleanTextGenerationModel(preferred)) {
            candidates.add(preferred)
        }

        // Modelo por defecto principal
        candidates.add(DEFAULT_MODEL)

        // Descubrimiento dinámico de la cuenta si hay API key (añadimos solo los 2 mejores)
        if (apiKey.isNotBlank()) {
            val discovered = fetchAvailableModels(apiKey)
            for (m in discovered.take(2)) {
                candidates.add(m)
            }
        }

        // Modelos de respaldo estándar
        for (m in FALLBACK_MODELS) {
            candidates.add(m)
        }

        candidates.filter { isCleanTextGenerationModel(it) }.take(3)
    }

    /**
     * Consulta la API oficial de Google AI (v1beta/models) para listar los modelos reales
     * activados para esta clave de API específica, filtrando solo modelos de texto válidos.
     */
    suspend fun fetchAvailableModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = apiKey.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=$trimmed")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val root = JSONObject(responseText)
                val modelsArray = root.optJSONArray("models") ?: return@withContext emptyList()
                val discovered = mutableListOf<String>()

                for (i in 0 until modelsArray.length()) {
                    val m = modelsArray.optJSONObject(i) ?: continue
                    val name = m.optString("name", "") // ej. "models/gemini-3.8-flash"
                    val cleanName = name.removePrefix("models/")
                    val methods = m.optJSONArray("supportedGenerationMethods")
                    val supportsGenerate = if (methods != null) {
                        (0 until methods.length()).any { methods.optString(it) == "generateContent" }
                    } else true

                    if (supportsGenerate && isCleanTextGenerationModel(cleanName)) {
                        discovered.add(cleanName)
                    }
                }

                if (discovered.isNotEmpty()) {
                    return@withContext prioritizeModels(discovered)
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error consultando modelos disponibles: ${e.message}")
        }
        emptyList()
    }

    /**
     * Ordena los modelos encontrados dando preferencia absoluta a Gemini 3.8 Flash.
     */
    private fun prioritizeModels(models: List<String>): List<String> {
        return models.filter { isCleanTextGenerationModel(it) }
            .sortedWith { a, b ->
                fun score(name: String): Int {
                    val lower = name.lowercase().trim()
                    return when {
                        lower == "gemini-3.8-flash" -> 110
                        lower == "gemini-3.8-flash-lite" -> 100
                        lower.startsWith("gemini-3.8") -> 95
                        lower == "gemini-3.7-flash" -> 85
                        lower.startsWith("gemini-3.7") -> 80
                        lower.startsWith("gemini-3.6") -> 70
                        lower.startsWith("gemini-3.5") -> 60
                        lower.contains("flash") -> 40
                        lower.contains("pro") -> 30
                        else -> 10
                    }
                }
                score(b).compareTo(score(a))
            }
    }

    /**
     * Extrae un mensaje de error limpio y legible para el usuario,
     * eliminando trazas técnicas de serialización interna.
     */
    fun extractCleanErrorMessage(rawError: String): String {
        val lower = rawError.lowercase()
        return when {
            lower.contains("api_key_invalid") || lower.contains("invalid api key") || lower.contains("key not valid") ->
                "Clave de API no válida. Cópiala directamente desde Google AI Studio (aistudio.google.com)."
            lower.contains("high demand") || lower.contains("503") || lower.contains("spikes in demand") || lower.contains("unavailable") ->
                "Los servidores de Google Gemini están con alta demanda puntual en este momento (503). Espera unos segundos y vuelve a probar."
            lower.contains("quota") || lower.contains("resource_exhausted") || lower.contains("rate limit") || lower.contains("exceeded your current quota") ->
                "Límite de cuota alcanzado en tu cuenta de Google AI Studio. Espera unos momentos o crea una clave nueva en aistudio.google.com."
            lower.contains("no longer available") -> {
                try {
                    val sIdx = rawError.indexOf("\"message\":")
                    if (sIdx != -1) {
                        val sub = rawError.substring(sIdx + 10).trim()
                        val quoteEnd = sub.indexOf('"', 1)
                        if (quoteEnd != -1) {
                            val msg = sub.substring(1, quoteEnd).trim()
                            return "$msg (Usa gemini-3.8-flash)"
                        }
                    }
                } catch (_: Exception) {}
                "El modelo anterior ya no está disponible para usuarios nuevos. Usa gemini-3.8-flash (configurado por defecto)."
            }
            lower.contains("failed to connect") || lower.contains("timeout") || lower.contains("unknownhost") ->
                "Sin conexión a internet para conectar con Google Gemini."
            else -> {
                try {
                    if (rawError.contains("\"message\":")) {
                        val sIdx = rawError.indexOf("\"message\":")
                        val sub = rawError.substring(sIdx + 10).trim()
                        val quoteEnd = sub.indexOf('"', 1)
                        if (quoteEnd != -1) {
                            val extracted = sub.substring(1, quoteEnd).trim()
                            if (extracted.isNotBlank()) return extracted
                        }
                    }
                } catch (_: Exception) {}
                rawError.lines().firstOrNull() ?: rawError
            }
        }
    }
}
