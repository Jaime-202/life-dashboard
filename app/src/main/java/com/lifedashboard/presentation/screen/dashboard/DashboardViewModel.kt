package com.lifedashboard.presentation.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifedashboard.core.util.DateUtils.toStartOfDayMillis
import com.lifedashboard.data.local.dao.HealthDao
import com.lifedashboard.data.local.dao.WeightDao
import com.lifedashboard.data.local.dao.WorkoutDao
import com.lifedashboard.data.local.entity.WeightEntryEntity
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
    val showUpcomingEvents: Boolean = true,
    val weightHistory: List<WeightEntryEntity> = emptyList(),
    val latestWeightKg: Float? = null,
    val userHeightCm: Int = 175,
    val showAddWeightDialog: Boolean = false,
    val workoutDaysTarget: Int = 4,
    val weeklyWorkoutsCount: Int = 0
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val healthDao: HealthDao,
    private val workoutDao: WorkoutDao,
    private val weightDao: WeightDao,
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
        observeWeights()
        observeCalendar()
        buildProgressMap()
        viewModelScope.launch(Dispatchers.IO) { evaluateAchievements() }
    }

    private fun observeUserGoals() {
        userPreferences.userGoals
            .onEach { goals ->
                _uiState.update { state ->
                    val stepsProg = if (goals.stepsTarget > 0) (state.todaySteps.toFloat() / goals.stepsTarget).coerceIn(0f, 1f) else 0f
                    val sleepProg = if (goals.sleepTargetHours > 0) (state.todaySleepHours / goals.sleepTargetHours).coerceIn(0f, 1f) else 0f
                    val calProg   = if (goals.caloriesTarget > 0) (state.todayCalories / goals.caloriesTarget).coerceIn(0f, 1f) else 0f

                    state.copy(
                        userName           = goals.userName,
                        stepsTarget        = goals.stepsTarget,
                        sleepTargetHours   = goals.sleepTargetHours,
                        caloriesTarget     = goals.caloriesTarget,
                        workoutDaysTarget  = goals.workoutDaysTarget,
                        stepsProgress      = stepsProg,
                        sleepProgress      = sleepProg,
                        caloriesProgress   = calProg,
                        showHeroRings      = goals.showHeroRings,
                        showAiInsight      = goals.showAiInsight,
                        showUpcomingEvents = goals.showUpcomingEvents,
                        userHeightCm       = goals.heightCm
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeWeights() {
        weightDao.observeAllWeights()
            .onEach { list ->
                _uiState.update { state ->
                    state.copy(
                        weightHistory = list,
                        latestWeightKg = list.firstOrNull()?.weightKg
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
                        stepsProgress    = if (state.stepsTarget > 0) (steps.toFloat() / state.stepsTarget).coerceIn(0f, 1f) else 0f,
                        sleepProgress    = if (state.sleepTargetHours > 0) (sleep / state.sleepTargetHours).coerceIn(0f, 1f) else 0f,
                        caloriesProgress = if (state.caloriesTarget > 0) (cals / state.caloriesTarget).coerceIn(0f, 1f) else 0f,
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

        // Sesiones completadas en la semana en curso (Lunes a Domingo)
        val monday = today.with(java.time.DayOfWeek.MONDAY)
        val mondayMillis = monday.toStartOfDayMillis()
        val nextMondayMillis = monday.plusDays(7).toStartOfDayMillis()
        workoutDao.getSessionsInRange(mondayMillis, nextMondayMillis)
            .onEach { weekSessions ->
                _uiState.update { it.copy(weeklyWorkoutsCount = weekSessions.size) }
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
            workoutDao.getSessionsInRange(fromMillis, toMillis),
            userPreferences.userGoals
        ) { healthList, sessions, goals ->
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
                    stepsProgress    = if (goals.stepsTarget > 0) (h.steps.toFloat() / goals.stepsTarget).coerceIn(0f, 1f) else 0f,
                    sleepProgress    = if (goals.sleepTargetHours > 0) (h.sleepHours / goals.sleepTargetHours).coerceIn(0f, 1f) else 0f,
                    waterProgress    = 0f,
                    actualSteps      = h.steps,
                    actualSleepHours = h.sleepHours,
                    actualCalories   = h.activeCalories
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

    fun setAddWeightDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showAddWeightDialog = visible) }
    }

    fun addWeightEntry(weight: Float, note: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            weightDao.upsert(
                WeightEntryEntity(
                    dateMillis = System.currentTimeMillis(),
                    weightKg = weight,
                    note = note
                )
            )
            userPreferences.updateWeight(weight)
        }
    }
}
