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
     * Si un modelo no está disponible para una clave, se prueba automáticamente el siguiente.
     */
    val FALLBACK_MODELS = listOf(
        "gemini-3.8-flash",
        "gemini-3.8-flash-lite",
        "gemini-3.7-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash",
        "gemini-3.5-flash-lite",
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-flash"
    )

    /**
     * Modelos predefinidos recomendados para selector en la interfaz de usuario.
     */
    val PRESET_MODELS = listOf(
        "gemini-3.8-flash" to "⚡ Gemini 3.8 Flash (Recomendado)",
        "gemini-3.8-flash-lite" to "🚀 Gemini 3.8 Flash-Lite (Rápido)",
        "gemini-3.7-flash" to "✨ Gemini 3.7 Flash",
        "gemini-3.5-flash" to "🔹 Gemini 3.5 Flash",
        "auto" to "🎯 Auto-detectar de mi cuenta"
    )

    /**
     * Devuelve una lista ordenada de modelos candidatos a probar.
     * Si [preferredModel] está establecido (y no es 'auto'), va en primer lugar.
     * Luego se intentan descubrir dinámicamente modelos activos vía la API de Google,
     * y finalmente se añaden los modelos de respaldo estándar.
     */
    suspend fun getCandidateModels(apiKey: String, preferredModel: String? = null): List<String> = withContext(Dispatchers.IO) {
        val candidates = linkedSetOf<String>()

        val preferred = preferredModel?.trim()
        if (!preferred.isNullOrBlank() && !preferred.equals("auto", ignoreCase = true)) {
            candidates.add(preferred)
        }

        // Modelo por defecto principal
        candidates.add(DEFAULT_MODEL)

        // Descubrimiento dinámico de la cuenta si hay API key
        if (apiKey.isNotBlank()) {
            val discovered = fetchAvailableModels(apiKey)
            for (m in discovered) {
                candidates.add(m)
            }
        }

        // Modelos de respaldo
        for (m in FALLBACK_MODELS) {
            candidates.add(m)
        }

        candidates.toList()
    }

    /**
     * Consulta la API oficial de Google AI (v1beta/models) para listar los modelos reales
     * activados para esta clave de API específica.
     */
    suspend fun fetchAvailableModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = apiKey.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=$trimmed")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
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

                    if (supportsGenerate && cleanName.contains("gemini", ignoreCase = true)) {
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
     * Ordena los modelos encontrados dando preferencia a los Flash más modernos (3.8 > 3.7 > ...).
     */
    private fun prioritizeModels(models: List<String>): List<String> {
        return models.sortedWith { a, b ->
            fun score(name: String): Int {
                return when {
                    name.contains("3.8-flash", ignoreCase = true) -> 100
                    name.contains("3.8", ignoreCase = true) -> 90
                    name.contains("3.7-flash", ignoreCase = true) -> 80
                    name.contains("3.7", ignoreCase = true) -> 75
                    name.contains("3.6-flash", ignoreCase = true) -> 70
                    name.contains("3.5-flash", ignoreCase = true) -> 65
                    name.contains("3.5", ignoreCase = true) -> 60
                    name.contains("flash", ignoreCase = true) -> 50
                    name.contains("pro", ignoreCase = true) -> 40
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
            lower.contains("quota") || lower.contains("resource_exhausted") || lower.contains("rate limit") ->
                "Límite de cuota alcanzado en tu cuenta de Google AI Studio. Espera unos momentos."
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
