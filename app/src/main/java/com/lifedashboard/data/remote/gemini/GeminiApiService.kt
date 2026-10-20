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
     * Utiliza el motor heurístico local inteligente para NO consumir la cuota de la API de Gemini,
     * reservando los 20 usos diarios exclusivamente para reconocimiento de fotos y texto de comidas.
     */
    suspend fun getDailyInsight(): String {
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
