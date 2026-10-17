package com.lifedashboard.presentation.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifedashboard.core.util.DateUtils.toStartOfDayMillis
import com.lifedashboard.data.local.dao.HealthDao
import com.lifedashboard.data.local.dao.WorkoutDao
import com.lifedashboard.data.remote.gemini.GeminiApiService
import com.lifedashboard.data.repository.UserPreferencesRepository
import com.lifedashboard.domain.model.CalendarEvent
import com.lifedashboard.domain.model.DailyProgress
import com.lifedashboard.domain.model.WorkoutType
import com.lifedashboard.domain.repository.CalendarRepository
import com.lifedashboard.domain.usecase.achievement.EvaluateRoutineAchievementUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class DashboardUiState(
    val userName: String = "Jaime",
    val todaySteps: Int = 0,
    val todaySleepHours: Float = 0f,
    val todayCalories: Float = 0f,
    val stepsTarget: Int = 10000,
    val sleepTargetHours: Float = 8.0f,
    val caloriesTarget: Float = 2200f,
    val stepsProgress: Float = 0f,
    val sleepProgress: Float = 0f,
    val caloriesProgress: Float = 0f,
    val workoutDoneToday: Boolean = false,
    val workoutTypeToday: WorkoutType? = null,
    val progressByDate: Map<LocalDate, DailyProgress> = emptyMap(),
    val upcomingEvents: List<CalendarEvent> = emptyList(),
    val aiInsight: String? = null,
    val isAiLoading: Boolean = false,
    val selectedDate: LocalDate = LocalDate.now(),
    val isDataLoading: Boolean = true,
    val showHeroRings: Boolean = true,
    val showAiInsight: Boolean = true,
    val showUpcomingEvents: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val healthDao: HealthDao,
    private val workoutDao: WorkoutDao,
    private val calendarRepository: CalendarRepository,
    private val geminiService: GeminiApiService,
    private val evaluateAchievements: EvaluateRoutineAchievementUseCase,
    private val userPreferences: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeUserGoals()
        observeTodayHealth()
        observeCalendar()
        buildProgressMap()
        viewModelScope.launch(Dispatchers.IO) { evaluateAchievements() }
    }

    private fun observeUserGoals() {
        userPreferences.userGoals
            .onEach { goals ->
                _uiState.update { state ->
                    val stepsProg = (state.todaySteps.toFloat() / goals.stepsTarget).coerceIn(0f, 1f)
                    val sleepProg = (state.todaySleepHours / goals.sleepTargetHours).coerceIn(0f, 1f)
                    val calProg   = (state.todayCalories / goals.caloriesTarget).coerceIn(0f, 1f)

                    state.copy(
                        userName           = goals.userName,
                        stepsTarget        = goals.stepsTarget,
                        sleepTargetHours   = goals.sleepTargetHours,
                        caloriesTarget     = goals.caloriesTarget,
                        stepsProgress      = stepsProg,
                        sleepProgress      = sleepProg,
                        caloriesProgress   = calProg,
                        showHeroRings      = goals.showHeroRings,
                        showAiInsight      = goals.showAiInsight,
                        showUpcomingEvents = goals.showUpcomingEvents
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeTodayHealth() {
        val today = LocalDate.now()
        val todayMillis = today.toStartOfDayMillis()
        val tomorrowMillis = today.plusDays(1).toStartOfDayMillis()

        healthDao.getHealthRange(todayMillis, tomorrowMillis)
            .onEach { list ->
                val h = list.firstOrNull()
                val steps = h?.steps ?: 0
                val sleep = h?.sleepHours ?: 0f
                val cals  = h?.activeCalories ?: 0f

                _uiState.update { state ->
                    state.copy(
                        todaySteps       = steps,
                        todaySleepHours  = sleep,
                        todayCalories    = cals,
                        stepsProgress    = (steps.toFloat() / state.stepsTarget).coerceIn(0f, 1f),
                        sleepProgress    = (sleep / state.sleepTargetHours).coerceIn(0f, 1f),
                        caloriesProgress = (cals / state.caloriesTarget).coerceIn(0f, 1f),
                        isDataLoading    = false
                    )
                }
            }
            .launchIn(viewModelScope)

        workoutDao.getSessionsInRange(todayMillis, tomorrowMillis)
            .onEach { sessions ->
                val todaySession = sessions.firstOrNull()
                _uiState.update { state ->
                    state.copy(
                        workoutDoneToday = todaySession != null,
                        workoutTypeToday = when (todaySession?.type) {
                            "STRENGTH" -> WorkoutType.STRENGTH
                            "CARDIO"   -> WorkoutType.CARDIO
                            "REST"     -> WorkoutType.REST
                            else       -> null
                        }
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeCalendar() {
        calendarRepository.getUpcomingEvents(limit = 4)
            .onEach { events ->
                _uiState.update { it.copy(upcomingEvents = events) }
            }
            .launchIn(viewModelScope)
    }

    private fun buildProgressMap() {
        val from = LocalDate.now().minusDays(60)
        val to   = LocalDate.now().plusDays(7)
        val fromMillis = from.toStartOfDayMillis()
        val toMillis   = to.toStartOfDayMillis()

        combine(
            healthDao.getHealthRange(fromMillis, toMillis),
            workoutDao.getSessionsInRange(fromMillis, toMillis)
        ) { healthList, sessions ->
            val zone = ZoneId.systemDefault()
            val sessionsByDate = sessions.groupBy { session ->
                Instant.ofEpochMilli(session.dateMillis).atZone(zone).toLocalDate()
            }

            val map = healthList.associate { h ->
                val date = Instant.ofEpochMilli(h.dateMillis).atZone(zone).toLocalDate()
                val daySession = sessionsByDate[date]?.firstOrNull()

                date to DailyProgress(
                    date             = date,
                    workoutCompleted = daySession != null,
                    workoutType      = when (daySession?.type) {
                        "STRENGTH" -> WorkoutType.STRENGTH
                        "CARDIO"   -> WorkoutType.CARDIO
                        "REST"     -> WorkoutType.REST
                        else       -> null
                    },
                    stepsProgress    = (h.steps.toFloat() / _uiState.value.stepsTarget).coerceIn(0f, 1f),
                    sleepProgress    = (h.sleepHours / _uiState.value.sleepTargetHours).coerceIn(0f, 1f),
                    waterProgress    = 0f,
                    actualSteps      = h.steps
                )
            }
            map
        }
        .flowOn(Dispatchers.Default)
        .onEach { progressMap ->
            _uiState.update { it.copy(progressByDate = progressMap) }
        }
        .launchIn(viewModelScope)
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun requestAiInsight() {
        if (_uiState.value.isAiLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAiLoading = true) }
            val insight = geminiService.getDailyInsight()
            _uiState.update { it.copy(aiInsight = insight, isAiLoading = false) }
        }
    }
}
