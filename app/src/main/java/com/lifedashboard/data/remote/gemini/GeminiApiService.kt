package com.lifedashboard.data.remote.gemini

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.lifedashboard.BuildConfig
import com.lifedashboard.domain.usecase.ai.DataAggregatorUseCase
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GeminiService"

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * GeminiApiService
 *
 * Integra el SDK de Google AI para Android.
 * Incluye un System Prompt cuidadosamente diseñado que convierte a Gemini
 * en un "Analista de Vida" que cruza datos de salud, entreno y agenda.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Singleton
class GeminiApiService @Inject constructor(
    private val aggregator: DataAggregatorUseCase
) {
    private val model by lazy {
        GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = BuildConfig.GEMINI_API_KEY,
            generationConfig = generationConfig {
                temperature = 0.7f
                maxOutputTokens = 300       // Respuesta corta e impactante
                topP = 0.9f
            },
            systemInstruction = content {
                text(SYSTEM_PROMPT)
            }
        )
    }

    /**
     * Genera el insight diario accionable.
     * Devuelve null si falla (la UI muestra un fallback).
     */
    suspend fun getDailyInsight(): String? {
        return try {
            val contextJson = aggregator.buildContextJson()
            Log.d(TAG, "Enviando contexto a Gemini (${contextJson.length} chars)")

            val userPrompt = """
                Aquí están mis datos de los últimos 7 días en JSON:
                
                $contextJson
                
                Analiza los patrones, busca correlaciones entre el sueño, los pasos y el rendimiento en el entrenamiento.
                Dame UN SOLO insight accionable y concreto para hoy. Máximo 3 frases. 
                Sé directo y específico — nada genérico.
            """.trimIndent()

            val response = model.generateContent(userPrompt)
            response.text?.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error generando insight Gemini", e)
            null
        }
    }

    companion object {
        private val SYSTEM_PROMPT = """
            Eres un Analista de Vida Personal altamente especializado. Tu función es cruzar datos 
            de salud, entrenamiento y agenda de un usuario para detectar patrones y correlaciones 
            reales — no consejos genéricos.
            
            REGLAS ESTRICTAS:
            1. Analiza siempre la correlación entre sueño y rendimiento físico.
            2. Si los pasos de hoy son bajos pero ayer hizo cardio, sugiere una estrategia concreta.
            3. Si el volumen en un ejercicio bajó respecto a la semana anterior, explica por qué podría ser.
            4. Si hay un evento importante en el calendario, considera si afecta la recuperación o energía.
            5. Responde en español, en primera persona del plural (nosotros) o segunda persona (tú).
            6. Máximo 3 frases. Sin emojis. Sin introducciones ("Hola", "He analizado", etc.).
            7. Empieza directamente con el dato observado. Ejemplo de tono:
               "Tus pesos en sentadilla bajaron un 8% los días en que dormiste menos de 6h. 
                Esta noche tienes 7.5h de margen antes de tu sesión de mañana — prioriza el sueño."
        """.trimIndent()
    }
}
