package com.lifedashboard.presentation.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifedashboard.core.notification.ReminderScheduler
import com.lifedashboard.core.util.DateUtils
import com.lifedashboard.core.util.DateUtils.toStartOfDayMillis
import com.lifedashboard.core.util.ReadinessCalculator
import com.lifedashboard.data.local.dao.HealthDao
import com.lifedashboard.data.local.dao.ReminderDao
import com.lifedashboard.data.local.dao.WeightDao
import com.lifedashboard.data.local.dao.WorkoutDao
import com.lifedashboard.data.local.entity.ReminderEntity
import com.lifedashboard.data.local.entity.ReminderType
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

data class DashboardReminderItem(
    val id: Long,
    val title: String,
    val type: String,
    val timeOfDay: String,
    val daysOfWeek: String,
    val iconEmoji: String,
    val isCompleted: Boolean,
    val subtitle: String
)

data class DashboardUiState(
    val userName: String = "Jaime",
    val todaySteps: Int = 0,
    val todaySleepHours: Float = 0f,
    val todayCalories: Float = 0f,
    val todayMealsCalories: Float = 0f,
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
    val weeklyWorkoutsCount: Int = 0,
    val todayReminders: List<DashboardReminderItem> = emptyList(),

    // Puntuación de Preparación (Readiness Score)
    val readinessScore: Int = 85,
    val readinessStatus: String = "Óptima",

    // Comparativa Semanal
    val weeklyStepsDiffPercent: Int = 0,
    val weeklySleepDiffHours: Float = 0f
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val healthDao: HealthDao,
    private val workoutDao: WorkoutDao,
    private val weightDao: WeightDao,
    private val mealDao: com.lifedashboard.data.local.dao.MealDao,
    private val reminderDao: ReminderDao,
    private val calendarRepository: CalendarRepository,
    private val geminiService: GeminiApiService,
    private val evaluateAchievements: EvaluateRoutineAchievementUseCase,
    private val userPreferences: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val currentDate = DateUtils.currentDateFlow()
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    init {
        observeUserGoals()
        observeTodayHealth()
        observeTodayMeals()
        observeWeights()
        observeCalendar()
        observeReminders()
        buildProgressMap()
        viewModelScope.launch(Dispatchers.IO) { evaluateAchievements() }
    }

    private fun observeUserGoals() {
        userPreferences.userGoals
            .onEach { goals ->
                _uiState.update { state ->
                    val stepsProg = if (goals.stepsTarget > 0) (state.todaySteps.toFloat() / goals.stepsTarget).coerceIn(0f, 1f) else 0f
                    val sleepProg = if (goals.sleepTargetHours > 0) (state.todaySleepHours / goals.sleepTargetHours).coerceIn(0f, 1f) else 0f
                    val effectiveCalories = if (state.todayCalories > 0f) state.todayCalories else (state.todaySteps * 0.04f).coerceAtLeast(0f)
                    val calProg   = if (goals.activeCaloriesTarget > 0) (effectiveCalories / goals.activeCaloriesTarget).coerceIn(0f, 1f) else 0f

                    state.copy(
                        userName           = goals.userName,
                        stepsTarget        = goals.stepsTarget,
                        sleepTargetHours   = goals.sleepTargetHours,
                        caloriesTarget     = goals.activeCaloriesTarget,
                        workoutDaysTarget  = goals.workoutDaysTarget,
                        stepsProgress      = stepsProg,
                        sleepProgress      = sleepProg,
                        todayCalories      = effectiveCalories,
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

    private fun observeTodayMeals() {
        currentDate.flatMapLatest { today ->
            mealDao.observeMealsForDay(
                today.toStartOfDayMillis(),
                today.plusDays(1).toStartOfDayMillis() - 1
            )
        }
            .onEach { meals ->
                val consumed = meals.sumOf { it.calories.toDouble() }.toFloat()
                _uiState.update { state -> state.copy(todayMealsCalories = consumed) }
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
        // Al cambiar de día, lo que estaba seleccionado como "hoy" pasa a la nueva fecha
        var previousDate: LocalDate? = null
        currentDate
            .onEach { today ->
                val wasFollowingToday = _uiState.value.selectedDate == previousDate
                if (wasFollowingToday) _uiState.update { it.copy(selectedDate = today) }
                previousDate = today
            }
            .launchIn(viewModelScope)

        currentDate.flatMapLatest { today ->
            combine(healthDao.observeLastNDays(14), userPreferences.userGoals) { list, goals ->
                Triple(today, list, goals)
            }
        }
            .onEach { (today, list, goals) ->
                val todayMillis = today.toStartOfDayMillis()
                val h = list.find { it.dateMillis == todayMillis }
                val steps = h?.steps ?: 0
                val sleep = h?.sleepHours ?: 0f
                val rawCalories = h?.activeCalories ?: 0f
                val activeCalories = if (rawCalories > 0f) rawCalories else (steps * 0.04f).coerceAtLeast(0f)
                val yesterdayMillis = today.minusDays(1).toStartOfDayMillis()
                val yesterdaySteps = list.find { it.dateMillis == yesterdayMillis }?.steps ?: steps
                val readiness = ReadinessCalculator.calculate(sleep, goals.sleepTargetHours, yesterdaySteps, goals.stepsTarget)

                // Comparativa semanal (últimos 7 días vs anteriores 7 días)
                val weekStart = today.minusDays(6).toStartOfDayMillis()
                val (thisWeek, prevWeek) = list.partition { it.dateMillis >= weekStart }
                val thisWeekSteps = thisWeek.sumOf { it.steps }
                val prevWeekSteps = prevWeek.sumOf { it.steps }
                val stepsDiffPct = if (prevWeekSteps > 0) {
                    (((thisWeekSteps - prevWeekSteps).toFloat() / prevWeekSteps) * 100).toInt()
                } else 0

                val thisWeekSleepAvg = thisWeek.map { it.sleepHours }.filter { it > 0 }.let { if (it.isNotEmpty()) it.average().toFloat() else 0f }
                val prevWeekSleepAvg = prevWeek.map { it.sleepHours }.filter { it > 0 }.let { if (it.isNotEmpty()) it.average().toFloat() else 0f }
                val sleepDiffHours = if (thisWeekSleepAvg > 0f && prevWeekSleepAvg > 0f) thisWeekSleepAvg - prevWeekSleepAvg else 0f

                _uiState.update { state ->
                    state.copy(
                        todaySteps             = steps,
                        todaySleepHours        = sleep,
                        todayCalories          = activeCalories,
                        stepsProgress          = if (goals.stepsTarget > 0) (steps.toFloat() / goals.stepsTarget).coerceIn(0f, 1f) else 0f,
                        sleepProgress          = if (goals.sleepTargetHours > 0) (sleep / goals.sleepTargetHours).coerceIn(0f, 1f) else 0f,
                        caloriesProgress       = if (goals.activeCaloriesTarget > 0) (activeCalories / goals.activeCaloriesTarget).coerceIn(0f, 1f) else 0f,
                        readinessScore         = readiness.score,
                        readinessStatus        = readiness.status,
                        weeklyStepsDiffPercent = stepsDiffPct,
                        weeklySleepDiffHours   = sleepDiffHours,
                        isDataLoading          = false
                    )
                }
            }
            .launchIn(viewModelScope)

        currentDate.flatMapLatest { today ->
            workoutDao.getSessionsInRange(today.toStartOfDayMillis(), today.plusDays(1).toStartOfDayMillis() - 1)
        }
            .onEach { sessions ->
                val todaySession = sessions.firstOrNull { it.type != "REST" } ?: sessions.firstOrNull()
                _uiState.update { state ->
                    state.copy(
                        workoutDoneToday = todaySession != null,
                        workoutTypeToday = todaySession?.type?.toWorkoutType()
                    )
                }
            }
            .launchIn(viewModelScope)

        // Días entrenados en la semana en curso (lunes a domingo): la meta se mide en días, no en sesiones
        currentDate.flatMapLatest { today ->
            val monday = today.with(java.time.DayOfWeek.MONDAY)
            workoutDao.getSessionsInRange(monday.toStartOfDayMillis(), monday.plusDays(7).toStartOfDayMillis() - 1)
        }
            .onEach { weekSessions ->
                val trainedDays = weekSessions.filter { it.type != "REST" }.map { it.dateMillis }.distinct().size
                _uiState.update { it.copy(weeklyWorkoutsCount = trainedDays) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeCalendar() {
        currentDate.flatMapLatest { calendarRepository.getUpcomingEvents(limit = 4) }
            .onEach { events ->
                _uiState.update { it.copy(upcomingEvents = events) }
            }
            .launchIn(viewModelScope)
    }

    private fun String.toWorkoutType(): WorkoutType = when (this) {
        "REST" -> WorkoutType.REST
        "CARDIO" -> WorkoutType.CARDIO
        else -> WorkoutType.STRENGTH
    }

    private fun buildProgressMap() {
        currentDate.flatMapLatest { today ->
            val from = today.minusDays(60)
            val to   = today.plusDays(7)
            val fromMillis = from.toStartOfDayMillis()
            val toMillis   = to.toStartOfDayMillis()

            combine(
                healthDao.getHealthRange(fromMillis, toMillis),
                workoutDao.getSessionsInRange(fromMillis, toMillis),
                userPreferences.userGoals
            ) { healthList, sessions, goals ->
                val zone = ZoneId.systemDefault()
                val healthByDate = healthList.associateBy { Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate() }
                val sessionsByDate = sessions.groupBy { session ->
                    Instant.ofEpochMilli(session.dateMillis).atZone(zone).toLocalDate()
                }

                val dates = generateSequence(from) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(to) }
                    .toList()

                dates.associateWith { date ->
                    val h = healthByDate[date]
                    val daySessions = sessionsByDate[date].orEmpty()
                    val daySession = daySessions.firstOrNull { it.type != "REST" } ?: daySessions.firstOrNull()
                    val steps = h?.steps ?: 0
                    val sleep = h?.sleepHours ?: 0f
                    val storedCalories = h?.activeCalories ?: 0f

                    DailyProgress(
                        date             = date,
                        workoutCompleted = daySession != null,
                        workoutType      = daySession?.type?.toWorkoutType(),
                        stepsProgress    = if (goals.stepsTarget > 0) (steps.toFloat() / goals.stepsTarget).coerceIn(0f, 1f) else 0f,
                        sleepProgress    = if (goals.sleepTargetHours > 0) (sleep / goals.sleepTargetHours).coerceIn(0f, 1f) else 0f,
                        waterProgress    = 0f,
                        actualSteps      = steps,
                        actualSleepHours = sleep,
                        actualCalories   = if (storedCalories > 0f) storedCalories else (steps * 0.04f).coerceAtLeast(0f)
                    )
                }
            }
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
            try {
                val insight = geminiService.getDailyInsight()
                _uiState.update { it.copy(aiInsight = insight, isAiLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isAiLoading = false) }
            }
        }
    }

    fun setAddWeightDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showAddWeightDialog = visible) }
    }

    private fun observeReminders() {
        combine(
            _uiState.map { it.selectedDate }.distinctUntilChanged(),
            reminderDao.observeEnabled()
        ) { selectedDate, enabledReminders ->
            selectedDate to enabledReminders
        }.flatMapLatest { (selectedDate, enabledReminders) ->
            val dateMillis = selectedDate.toStartOfDayMillis()
            reminderDao.observeLogsForDate(dateMillis).map { logs ->
                val logsMap = logs.associateBy { it.reminderId }
                val dayOfWeekNumber = selectedDate.dayOfWeek.value // 1 (Mon) .. 7 (Sun)

                enabledReminders.filter { reminder ->
                    ReminderScheduler.appliesOnDay(reminder.type, reminder.daysOfWeek, dayOfWeekNumber)
                }.map { reminder ->
                    val isDone = logsMap[reminder.id]?.isCompleted == true
                    val subtitle = when (reminder.type) {
                        ReminderType.HORARIO -> "⏰ ${reminder.timeOfDay}"
                        ReminderType.DIARIO -> "📅 Diario · ${reminder.timeOfDay}"
                        ReminderType.SEMANAL -> "📆 Semanal · ${reminder.timeOfDay}"
                        else -> "Aviso · ${reminder.timeOfDay}"
                    }
                    DashboardReminderItem(
                        id = reminder.id,
                        title = reminder.title,
                        type = reminder.type,
                        timeOfDay = reminder.timeOfDay,
                        daysOfWeek = reminder.daysOfWeek,
                        iconEmoji = reminder.iconEmoji,
                        isCompleted = isDone,
                        subtitle = subtitle
                    )
                }.sortedWith(
                    compareBy<DashboardReminderItem> { it.isCompleted }
                        .thenBy { it.timeOfDay }
                )
            }
        }.onEach { reminders ->
            _uiState.update { it.copy(todayReminders = reminders) }
        }.launchIn(viewModelScope)
    }

    fun toggleReminder(reminderId: Long, isCompleted: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val dateMillis = _uiState.value.selectedDate.toStartOfDayMillis()
            reminderDao.toggleCompletion(reminderId, dateMillis, isCompleted)
        }
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

    fun deleteWeight(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            weightDao.deleteById(id)
            weightDao.getLatestWeight()?.let { userPreferences.updateWeight(it.weightKg) }
        }
    }
}
