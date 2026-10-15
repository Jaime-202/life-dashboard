package com.lifedashboard.data.remote.gemini

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.lifedashboard.BuildConfig
import com.lifedashboard.data.local.dao.HealthDao
import com.lifedashboard.data.local.dao.WorkoutDao
import com.lifedashboard.data.repository.UserPreferencesRepository
import com.lifedashboard.domain.usecase.ai.DataAggregatorUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GeminiService"

@Singleton
class GeminiApiService @Inject constructor(
    private val aggregator: DataAggregatorUseCase,
    private val userPreferences: UserPreferencesRepository,
    private val healthDao: HealthDao,
    private val workoutDao: WorkoutDao
) {

    /**
     * Genera el insight diario accionable.
     * Si hay API key disponible intenta con Gemini 2.0 Flash.
     * Si falla o no hay clave, genera un análisis inteligente con el motor heurístico local.
     */
    suspend fun getDailyInsight(): String {
        val userGoals = userPreferences.userGoals.first()
        val configuredKey = userGoals.geminiApiKey.ifBlank {
            BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() && !it.contains("PEGA_AQUI") } ?: ""
        }

        if (configuredKey.isNotBlank()) {
            try {
                val model = GenerativeModel(
                    modelName = "gemini-2.0-flash",
                    apiKey = configuredKey,
                    generationConfig = generationConfig {
                        temperature = 0.7f
                        maxOutputTokens = 250
                        topP = 0.9f
                    },
                    systemInstruction = content {
                        text(SYSTEM_PROMPT)
                    }
                )

                val contextJson = aggregator.buildContextJson()
                Log.d(TAG, "Enviando contexto a Gemini (${contextJson.length} caracteres)")

                val userPrompt = """
                    Datos de mis últimos 7 días:
                    $contextJson
                    
                    Analiza patrones entre sueño, pasos y entrenamiento.
                    Dame un insight accionable directo para hoy en máximo 3 frases concisas.
                """.trimIndent()

                val response = model.generateContent(userPrompt)
                val text = response.text?.trim()
                if (!text.isNullOrBlank()) {
                    return text
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini API falló, activando motor de análisis heurístico local", e)
            }
        }

        // ─── Motor de Análisis Heurístico Inteligente Local ────────────────────
        return generateLocalHeuristicInsight()
    }

    private suspend fun generateLocalHeuristicInsight(): String {
        try {
            val healthList = healthDao.getLastNDays(7)
            val todayHealth = healthList.firstOrNull()
            val avgSleep = if (healthList.isNotEmpty()) healthList.map { it.sleepHours }.average().toFloat() else 7.5f
            val recentSessions = workoutDao.getRecentSessions(7)
            val trainedYesterday = recentSessions.any {
                val sessionDate = java.time.Instant.ofEpochMilli(it.dateMillis)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                sessionDate == java.time.LocalDate.now().minusDays(1)
            }

            val todaySleep = todayHealth?.sleepHours ?: avgSleep
            val todaySteps = todayHealth?.steps ?: 0

            return when {
                todaySleep < 6.5f && trainedYesterday -> {
                    "Has dormido ${(todaySleep * 10).toInt() / 10f}h tras una sesión de entrenamiento reciente. Tu cuerpo necesita mayor recuperación: hoy prioriza hidratación, caminatas suaves y descanso reparador."
                }
                todaySleep >= 8.0f && todaySteps < 4000 -> {
                    "Tu descanso ha sido óptimo (${(todaySleep * 10).toInt() / 10f}h de sueño acumulado), pero tu nivel de movimiento hoy está por debajo del ritmo ideal. Una caminata de 20 minutos reactivará tu energía metabólica."
                }
                recentSessions.size >= 4 -> {
                    "Excelente consistencia con ${recentSessions.size} sesiones esta semana. Mantén el ritmo cuidando la ingesta proteica y asegúrate de programar un día de descarga activa."
                }
                todaySteps >= 10000 -> {
                    "¡Meta de pasos superada hoy con más de $todaySteps pasos! El aumento de actividad diaria favorece la conciliación de un sueño profundo esta noche."
                }
                else -> {
                    "Tu media de sueño en los últimos días se sitúa en ${(avgSleep * 10).toInt() / 10f}h. Mantener una rutina fija al acostarte mejorará tu rendimiento muscular en las próximas sesiones."
                }
            }
        } catch (e: Exception) {
            return "Mantén tu hidratación y procura cumplir tus metas de pasos y descanso para maximizar tu energía diaria."
        }
    }

    companion object {
        private val SYSTEM_PROMPT = """
            Eres un Analista de Vida Personal y Rendimiento Deportivo de alto nivel.
            Analiza correlaciones entre sueño, pasos y entrenamiento con tono profesional, motivador y directo.
            REGLAS:
            1. En español.
            2. Máximo 3 frases.
            3. Empieza directamente con el dato y la recomendación práctica.
            4. Evita introducciones como "Hola" o "He analizado tus datos".
        """.trimIndent()
    }
}
