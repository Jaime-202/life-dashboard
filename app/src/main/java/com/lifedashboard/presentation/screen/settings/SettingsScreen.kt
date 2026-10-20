package com.lifedashboard.presentation.screen.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifedashboard.core.worker.WorkManagerScheduler
import com.lifedashboard.data.local.dao.WeightDao
import com.lifedashboard.data.local.entity.WeightEntryEntity
import com.lifedashboard.data.repository.UserPreferencesRepository
import com.lifedashboard.domain.repository.CalendarRepository
import com.lifedashboard.presentation.common.components.WeightEvolutionChart
import com.lifedashboard.presentation.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class ThemeMode { OSCURO, CLARO, SISTEMA }

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class SettingsState(
    val userName: String = "Jaime",
    val fitnessLevel: String = "INTERMEDIO",
    val primaryGoal: String = "SALUD_GENERAL",
    val weightKg: Float = 75.0f,
    val heightCm: Int = 175,
    val themeMode: ThemeMode = ThemeMode.OSCURO,
    val showHeroRings: Boolean = true,
    val showAiInsight: Boolean = true,
    val showUpcomingEvents: Boolean = true,
    val dailyReminders: Boolean = true,
    val hydrationReminder: Boolean = true,
    val inactivityAlerts: Boolean = false,
    val eveningReview: Boolean = true,
    val aiStyle: String = "CIENTIFICO",
    val syncFrequency: String = "30_MIN",
    val geminiApiKey: String = "",
    val stepsTarget: Int = 10000,
    val sleepTargetHours: Float = 8.0f,
    val caloriesTarget: Float = 2200f,
    val workoutDaysTarget: Int = 4,
    val workoutMinutesTarget: Int = 180,
    val healthConnectGranted: Boolean = false,
    val calendarGranted: Boolean = false,
    val weightHistory: List<WeightEntryEntity> = emptyList(),
    val bedtimeReminderEnabled: Boolean = true,
    val wakeUpHour: Int = 8,
    val wakeUpMinute: Int = 30,
    val bedtimeReminderMinutesBefore: Int = 30,
    val proteinTargetGrams: Float = 140f,
    val carbsTargetGrams: Float = 250f,
    val fatTargetGrams: Float = 70f
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val scheduler: WorkManagerScheduler,
    private val calendarRepository: CalendarRepository,
    private val userPreferences: UserPreferencesRepository,
    private val weightDao: WeightDao,
    private val foodAiService: com.lifedashboard.data.remote.food.FoodAiService,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        userPreferences.userGoals
            .onEach { goals ->
                val currentTheme = when (goals.themeMode) {
                    "CLARO"   -> ThemeMode.CLARO
                    "SISTEMA" -> ThemeMode.SISTEMA
                    else      -> ThemeMode.OSCURO
                }
                _state.value = _state.value.copy(
                    userName             = goals.userName,
                    fitnessLevel         = goals.fitnessLevel,
                    primaryGoal          = goals.primaryGoal,
                    weightKg             = goals.weightKg,
                    heightCm             = goals.heightCm,
                    themeMode            = currentTheme,
                    showHeroRings        = goals.showHeroRings,
                    showAiInsight        = goals.showAiInsight,
                    showUpcomingEvents   = goals.showUpcomingEvents,
                    dailyReminders       = goals.dailyReminders,
                    hydrationReminder    = goals.hydrationReminder,
                    inactivityAlerts     = goals.inactivityAlerts,
                    eveningReview        = goals.eveningReview,
                    aiStyle              = goals.aiStyle,
                    syncFrequency        = goals.syncFrequency,
                    geminiApiKey         = goals.geminiApiKey,
                    stepsTarget          = goals.stepsTarget,
                    sleepTargetHours     = goals.sleepTargetHours,
                    caloriesTarget       = goals.caloriesTarget,
                    workoutDaysTarget    = goals.workoutDaysTarget,
                    workoutMinutesTarget = goals.workoutMinutesTarget,
                    bedtimeReminderEnabled = goals.bedtimeReminderEnabled,
                    wakeUpHour           = goals.wakeUpHour,
                    wakeUpMinute         = goals.wakeUpMinute,
                    bedtimeReminderMinutesBefore = goals.bedtimeReminderMinutesBefore,
                    proteinTargetGrams   = goals.proteinTargetGrams,
                    carbsTargetGrams     = goals.carbsTargetGrams,
                    fatTargetGrams       = goals.fatTargetGrams
                )
            }
            .launchIn(viewModelScope)

        weightDao.observeAllWeights()
            .onEach { weights ->
                _state.value = _state.value.copy(weightHistory = weights)
            }
            .launchIn(viewModelScope)
    }

    fun toggleBedtimeReminder(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.updateBedtimeReminder(enabled)
            if (enabled) {
                scheduler.scheduleBedtimeReminder(
                    wakeUpHour = _state.value.wakeUpHour,
                    wakeUpMinute = _state.value.wakeUpMinute,
                    sleepTargetHours = _state.value.sleepTargetHours,
                    minutesBefore = _state.value.bedtimeReminderMinutesBefore
                )
            } else {
                scheduler.cancelBedtimeReminder()
            }
        }
    }

    fun updateWakeUpTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            userPreferences.updateWakeUpTime(hour, minute)
            if (_state.value.bedtimeReminderEnabled) {
                scheduler.scheduleBedtimeReminder(
                    wakeUpHour = hour,
                    wakeUpMinute = minute,
                    sleepTargetHours = _state.value.sleepTargetHours,
                    minutesBefore = _state.value.bedtimeReminderMinutesBefore
                )
            }
        }
    }

    fun logWeight(weight: Float, note: String?) {
        if (weight <= 0f) return
        viewModelScope.launch {
            val entry = WeightEntryEntity(
                dateMillis = System.currentTimeMillis(),
                weightKg = weight,
                note = note?.takeIf { it.isNotBlank() }
            )
            weightDao.upsert(entry)
            userPreferences.updateFitnessProfile(
                level = _state.value.fitnessLevel,
                goal = _state.value.primaryGoal,
                weight = weight,
                height = _state.value.heightCm
            )
        }
    }

    fun deleteWeightEntry(id: Long) {
        viewModelScope.launch {
            weightDao.deleteById(id)
        }
    }

    fun updateProfile(name: String, level: String, goal: String, weight: Float, height: Int) {
        viewModelScope.launch {
            if (name.isNotBlank()) userPreferences.updateUserName(name.trim())
            userPreferences.updateFitnessProfile(level, goal, weight, height)
        }
    }

    fun updateGeminiApiKey(apiKey: String) {
        viewModelScope.launch { userPreferences.updateGeminiApiKey(apiKey.trim()) }
    }

    suspend fun testApiKey(apiKey: String): Result<String> {
        return foodAiService.testApiKeyConnection(apiKey)
    }

    fun updateStepsTarget(target: Int) {
        if (target > 0) {
            viewModelScope.launch {
                userPreferences.updateStepsTarget(target)
                com.lifedashboard.core.worker.WidgetUpdateWorker.updateAllWidgets(context)
            }
        }
    }

    fun updateSleepTarget(target: Float) {
        if (target > 0f) {
            viewModelScope.launch {
                userPreferences.updateSleepTarget(target)
                com.lifedashboard.core.worker.WidgetUpdateWorker.updateAllWidgets(context)
            }
        }
    }

    fun updateCaloriesTarget(target: Float) {
        if (target > 0f) {
            viewModelScope.launch {
                userPreferences.updateCaloriesTarget(target)
                com.lifedashboard.core.worker.WidgetUpdateWorker.updateAllWidgets(context)
            }
        }
    }

    fun updateWorkoutTargets(days: Int, minutes: Int) {
        viewModelScope.launch {
            if (days > 0) userPreferences.updateWorkoutDaysTarget(days)
            if (minutes > 0) userPreferences.updateWorkoutMinutesTarget(minutes)
            com.lifedashboard.core.worker.WidgetUpdateWorker.updateAllWidgets(context)
        }
    }

    fun updateMacroTargets(protein: Float, carbs: Float, fat: Float) {
        viewModelScope.launch {
            userPreferences.updateMacroTargets(protein, carbs, fat)
            com.lifedashboard.core.worker.WidgetUpdateWorker.updateAllWidgets(context)
        }
    }

    fun checkPermissions(context: Context) {
        val calendarPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        val healthStatus = try {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            false
        }

        _state.value = _state.value.copy(
            calendarGranted = calendarPermission,
            healthConnectGranted = healthStatus
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            userPreferences.updateThemeMode(mode.name)
        }
    }

    fun toggleShowHeroRings(enabled: Boolean) {
        viewModelScope.launch { userPreferences.updateShowHeroRings(enabled) }
    }

    fun toggleShowAiInsight(enabled: Boolean) {
        viewModelScope.launch { userPreferences.updateShowAiInsight(enabled) }
    }

    fun toggleShowUpcomingEvents(enabled: Boolean) {
        viewModelScope.launch { userPreferences.updateShowUpcomingEvents(enabled) }
    }

    fun toggleDailyReminders(enabled: Boolean) {
        viewModelScope.launch { userPreferences.updateDailyReminders(enabled) }
    }

    fun toggleHydrationReminder(enabled: Boolean) {
        viewModelScope.launch { userPreferences.updateHydrationReminder(enabled) }
    }

    fun toggleInactivityAlerts(enabled: Boolean) {
        viewModelScope.launch { userPreferences.updateInactivityAlerts(enabled) }
    }

    fun toggleEveningReview(enabled: Boolean) {
        viewModelScope.launch { userPreferences.updateEveningReview(enabled) }
    }

    fun setAiStyle(style: String) {
        viewModelScope.launch { userPreferences.updateAiStyle(style) }
    }

    fun setSyncFrequency(freq: String) {
        viewModelScope.launch { userPreferences.updateSyncFrequency(freq) }
    }

    fun onHealthConnectConnect(context: Context) {
        _state.value = _state.value.copy(healthConnectGranted = true)
        scheduler.runHealthSyncNow()
        Toast.makeText(context, "Sincronizando con Health Connect", Toast.LENGTH_SHORT).show()
    }

    fun onCalendarPermissionGranted(context: Context) {
        _state.value = _state.value.copy(calendarGranted = true)
        CoroutineScope(Dispatchers.IO).launch {
            calendarRepository.syncCalendarEvents()
        }
        Toast.makeText(context, "Calendario sincronizado", Toast.LENGTH_SHORT).show()
    }

    fun forceSyncAll(context: Context) {
        scheduler.runHealthSyncNow()
        CoroutineScope(Dispatchers.IO).launch {
            calendarRepository.syncCalendarEvents()
        }
        Toast.makeText(context, "Sincronizando fuentes de datos…", Toast.LENGTH_SHORT).show()
    }

    fun exportData(context: Context) {
        val summary = """
            {
              "user": "${_state.value.userName}",
              "fitness_level": "${_state.value.fitnessLevel}",
              "primary_goal": "${_state.value.primaryGoal}",
              "weight_kg": ${_state.value.weightKg},
              "height_cm": ${_state.value.heightCm},
              "targets": {
                "steps": ${_state.value.stepsTarget},
                "sleep_hours": ${_state.value.sleepTargetHours},
                "calories": ${_state.value.caloriesTarget},
                "workout_days": ${_state.value.workoutDaysTarget}
              }
            }
        """.trimIndent()
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, summary)
            type = "application/json"
        }
        context.startActivity(Intent.createChooser(sendIntent, "Exportar Datos de Life Dashboard"))
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var showProfileModal by remember { mutableStateOf(false) }
    var showApiKeyModal by remember { mutableStateOf(false) }
    var showStepsModal by remember { mutableStateOf(false) }
    var showSleepModal by remember { mutableStateOf(false) }
    var showCaloriesModal by remember { mutableStateOf(false) }
    var showWorkoutModal by remember { mutableStateOf(false) }
    var showAddWeightModal by remember { mutableStateOf(false) }
    var showWeightHistoryModal by remember { mutableStateOf(false) }
    var showWakeUpTimeModal by remember { mutableStateOf(false) }
    var showMacrosModal by remember { mutableStateOf(false) }

    val healthPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    val healthPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        if (grantedPermissions.containsAll(healthPermissions)) {
            viewModel.onHealthConnectConnect(context)
        } else {
            Toast.makeText(context, "Permisos de salud parciales o denegados", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermissions(context)
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onCalendarPermissionGranted(context)
        } else {
            Toast.makeText(context, "Permiso de calendario denegado", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GradientStart, GradientEnd)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 56.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text       = "Personalización & Ajustes",
            style      = MaterialTheme.typography.headlineSmall,
            color      = White,
            fontWeight = FontWeight.Bold
        )

        // ─── Tarjeta de Perfil Avanzada ──────────────────────────────────
        EnhancedUserProfileCard(
            userName      = state.userName,
            fitnessLevel  = state.fitnessLevel,
            primaryGoal   = state.primaryGoal,
            weightKg      = state.weightHistory.firstOrNull()?.weightKg ?: state.weightKg,
            heightCm      = state.heightCm,
            onOpenProfile = { showProfileModal = true },
            onSyncAll     = { viewModel.forceSyncAll(context) }
        )

        // ─── Seguimiento de Peso & Composición ─────────────────────────────
        WeightTrackerCard(
            latestWeight  = state.weightHistory.firstOrNull()?.weightKg ?: state.weightKg,
            heightCm      = state.heightCm,
            weightHistory = state.weightHistory,
            onAddWeight   = { showAddWeightModal = true },
            onViewHistory = { showWeightHistoryModal = true }
        )

        // ─── Metas & Objetivos Personales (Centralizado) ───────────────────
        SectionHeader("Metas & Objetivos Personales")
        SettingsCard {
            SettingsItem(
                icon      = Icons.AutoMirrored.Filled.DirectionsWalk,
                iconColor = RingSteps,
                title     = "Objetivo de Pasos",
                subtitle  = "${state.stepsTarget} pasos al día",
                trailing  = {
                    OutlinedButton(
                        onClick = { showStepsModal = true },
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                        border  = BorderStroke(1.dp, Violet400)
                    ) {
                        Text("Modificar", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )

            HorizontalDivider(color = Indigo700)

            SettingsItem(
                icon      = Icons.Default.Bedtime,
                iconColor = RingSleep,
                title     = "Objetivo de Sueño",
                subtitle  = "${String.format(Locale.US, "%.1f", state.sleepTargetHours)} horas por noche",
                trailing  = {
                    OutlinedButton(
                        onClick = { showSleepModal = true },
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                        border  = BorderStroke(1.dp, Violet400)
                    ) {
                        Text("Modificar", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )

            HorizontalDivider(color = Indigo700)

            SettingsItem(
                icon      = Icons.Default.LocalFireDepartment,
                iconColor = Amber400,
                title     = "Objetivo de Calorías Activas",
                subtitle  = "${state.caloriesTarget.toInt()} kcal al día",
                trailing  = {
                    OutlinedButton(
                        onClick = { showCaloriesModal = true },
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                        border  = BorderStroke(1.dp, Violet400)
                    ) {
                        Text("Modificar", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )

            HorizontalDivider(color = Indigo700)

            SettingsItem(
                icon      = Icons.Default.FitnessCenter,
                iconColor = Violet400,
                title     = "Sesiones de Entrenamiento",
                subtitle  = "${state.workoutDaysTarget} días / semana (${state.workoutMinutesTarget} min)",
                trailing  = {
                    OutlinedButton(
                        onClick = { showWorkoutModal = true },
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                        border  = BorderStroke(1.dp, Violet400)
                    ) {
                        Text("Modificar", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )

            HorizontalDivider(color = Indigo700)

            SettingsItem(
                icon      = Icons.Default.Restaurant,
                iconColor = Emerald400,
                title     = "Objetivo de Macronutrientes",
                subtitle  = "P: ${state.proteinTargetGrams.toInt()}g · C: ${state.carbsTargetGrams.toInt()}g · G: ${state.fatTargetGrams.toInt()}g",
                trailing  = {
                    OutlinedButton(
                        onClick = { showMacrosModal = true },
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                        border  = BorderStroke(1.dp, Violet400)
                    ) {
                        Text("Modificar", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        }

        // ─── Hábitos & Recordatorios Inteligentes ──────────────────────────
        SectionHeader("Notificaciones & Hábitos")
        SettingsCard {
            SwitchSettingItem(
                title           = "Recordatorios Diarios",
                subtitle        = "Notificación matutina con el resumen de tus metas",
                checked         = state.dailyReminders,
                onCheckedChange = { viewModel.toggleDailyReminders(it) }
            )
            HorizontalDivider(color = Indigo700)
            SwitchSettingItem(
                title           = "Alerta de Hidratación",
                subtitle        = "Avisos periódicos para beber agua y pausas activas",
                checked         = state.hydrationReminder,
                onCheckedChange = { viewModel.toggleHydrationReminder(it) }
            )
            HorizontalDivider(color = Indigo700)
            SwitchSettingItem(
                title           = "Resumen Nocturno de Recuperación",
                subtitle        = "Consejos para optimizar el sueño según tu día",
                checked         = state.eveningReview,
                onCheckedChange = { viewModel.toggleEveningReview(it) }
            )
            HorizontalDivider(color = Indigo700)
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Aviso para Ir a Dormir", style = MaterialTheme.typography.labelLarge, color = White, fontWeight = FontWeight.Bold)
                        Text(
                            "Notificación inteligente 30 min antes de la hora recomendada para dormir",
                            style = MaterialTheme.typography.bodySmall,
                            color = Gray400
                        )
                    }
                    Switch(
                        checked = state.bedtimeReminderEnabled,
                        onCheckedChange = { viewModel.toggleBedtimeReminder(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor   = Indigo900,
                            checkedTrackColor   = Violet400,
                            uncheckedThumbColor = Gray400,
                            uncheckedTrackColor = Indigo700
                        )
                    )
                }

                if (state.bedtimeReminderEnabled) {
                    val wakeMinutes = state.wakeUpHour * 60 + state.wakeUpMinute
                    val sleepMinutes = (state.sleepTargetHours * 60).toInt()
                    var bedtimeMin = (wakeMinutes - sleepMinutes) % 1440
                    if (bedtimeMin < 0) bedtimeMin += 1440
                    val bedtimeHour = bedtimeMin / 60
                    val bedtimeM = bedtimeMin % 60

                    var reminderMin = (bedtimeMin - state.bedtimeReminderMinutesBefore) % 1440
                    if (reminderMin < 0) reminderMin += 1440
                    val reminderHour = reminderMin / 60
                    val reminderM = reminderMin % 60

                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Indigo900.copy(alpha = 0.5f))
                            .border(1.dp, Indigo700.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Alarm, contentDescription = null, tint = RingSleep, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Hora de levantarse: ${String.format(Locale.US, "%02d:%02d", state.wakeUpHour, state.wakeUpMinute)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                OutlinedButton(
                                    onClick = { showWakeUpTimeModal = true },
                                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                                    border  = BorderStroke(1.dp, Violet400),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Cambiar", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Dormir a las: ${String.format(Locale.US, "%02d:%02d", bedtimeHour, bedtimeM)} (${String.format(Locale.US, "%.1fh", state.sleepTargetHours)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Gray300
                                )
                                Text(
                                    text = "Aviso: ${String.format(Locale.US, "%02d:%02d", reminderHour, reminderM)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Amber400,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // ─── Inteligencia Artificial & Coach ──────────────────────────────
        SectionHeader("Inteligencia Artificial & Coach")
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Estilo de Análisis del Coach", style = MaterialTheme.typography.labelLarge, color = White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OptionChip("Científico", state.aiStyle == "CIENTIFICO", Modifier.weight(1f)) {
                        viewModel.setAiStyle("CIENTIFICO")
                    }
                    OptionChip("Motivador", state.aiStyle == "MOTIVADOR", Modifier.weight(1f)) {
                        viewModel.setAiStyle("MOTIVADOR")
                    }
                    OptionChip("Directo", state.aiStyle == "DIRECTO", Modifier.weight(1f)) {
                        viewModel.setAiStyle("DIRECTO")
                    }
                }
            }

            HorizontalDivider(color = Indigo700)

            SettingsItem(
                icon      = Icons.Default.AutoAwesome,
                iconColor = Violet400,
                title     = "Motor Gemini 2.0 Flash",
                subtitle  = if (state.geminiApiKey.isNotBlank()) "Clave personalizada activa (Google AI Studio)"
                            else "Motor Heurístico Local inteligente (100% Offline y Gratis)",
                trailing  = {
                    OutlinedButton(
                        onClick = { showApiKeyModal = true },
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                        border  = BorderStroke(1.dp, Violet400)
                    ) {
                        Text(if (state.geminiApiKey.isNotBlank()) "Modificar" else "Añadir API Key", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        }

        // ─── Personalización del Panel ─────────────────────────────────────
        SectionHeader("Diseño del Panel")
        SettingsCard {
            SwitchSettingItem(
                title           = "Pulso Biométrico Hero",
                subtitle        = "Muestra el indicador de vitalidad en cabecera",
                checked         = state.showHeroRings,
                onCheckedChange = { viewModel.toggleShowHeroRings(it) }
            )
            HorizontalDivider(color = Indigo700)
            SwitchSettingItem(
                title           = "Tarjeta de Insight IA",
                subtitle        = "Muestra el análisis inteligente del día",
                checked         = state.showAiInsight,
                onCheckedChange = { viewModel.toggleShowAiInsight(it) }
            )
            HorizontalDivider(color = Indigo700)
            SwitchSettingItem(
                title           = "Próximos Eventos",
                subtitle        = "Visualiza eventos próximos del calendario local",
                checked         = state.showUpcomingEvents,
                onCheckedChange = { viewModel.toggleShowUpcomingEvents(it) }
            )
        }

        // ─── Sincronización & Fuentes de Datos ─────────────────────────────
        SectionHeader("Sincronización & Salud")
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Frecuencia de Auto-Sincronización", style = MaterialTheme.typography.labelLarge, color = White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    OptionChip("15 min", state.syncFrequency == "15_MIN", Modifier.weight(1f)) {
                        viewModel.setSyncFrequency("15_MIN")
                    }
                    OptionChip("30 min", state.syncFrequency == "30_MIN", Modifier.weight(1f)) {
                        viewModel.setSyncFrequency("30_MIN")
                    }
                    OptionChip("1 hora", state.syncFrequency == "60_MIN", Modifier.weight(1f)) {
                        viewModel.setSyncFrequency("60_MIN")
                    }
                    OptionChip("Manual", state.syncFrequency == "MANUAL", Modifier.weight(1f)) {
                        viewModel.setSyncFrequency("MANUAL")
                    }
                }
            }

            HorizontalDivider(color = Indigo700)

            SettingsItem(
                icon      = Icons.Default.Favorite,
                iconColor = Rose400,
                title     = "Health Connect",
                subtitle  = if (state.healthConnectGranted) "Conectado y sincronizando"
                            else "Toca para vincular Health Connect",
                trailing  = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    healthPermissionLauncher.launch(healthPermissions)
                                } catch (e: Exception) {
                                    openHealthConnectSettings(context)
                                }
                            },
                            colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                            border  = BorderStroke(1.dp, Violet400)
                        ) { Text("Conectar", style = MaterialTheme.typography.labelMedium) }

                        IconButton(onClick = { openHealthConnectSettings(context) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Ajustes de Salud", tint = Gray400, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )

            HorizontalDivider(color = Indigo700)

            SettingsItem(
                icon      = Icons.Default.CalendarMonth,
                iconColor = Cyan400,
                title     = "Calendario del Dispositivo",
                subtitle  = if (state.calendarGranted) "Sincronizado localmente"
                            else "Permiso READ_CALENDAR requerido",
                trailing  = {
                    if (state.calendarGranted) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Concedido", tint = Emerald400, modifier = Modifier.size(20.dp))
                    } else {
                        OutlinedButton(
                            onClick = { calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                            colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                            border  = BorderStroke(1.dp, Violet400)
                        ) { Text("Permitir", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            )
        }

        // ─── Apariencia & Tema ─────────────────────────────────────────────
        SectionHeader("Apariencia & Tema")
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Modo de Tema", style = MaterialTheme.typography.labelLarge, color = White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ThemeOptionChip("Oscuro", Icons.Default.DarkMode, state.themeMode == ThemeMode.OSCURO, Modifier.weight(1f)) {
                        viewModel.setThemeMode(ThemeMode.OSCURO)
                    }
                    ThemeOptionChip("Claro", Icons.Default.LightMode, state.themeMode == ThemeMode.CLARO, Modifier.weight(1f)) {
                        viewModel.setThemeMode(ThemeMode.CLARO)
                    }
                    ThemeOptionChip("Sistema", Icons.Default.SettingsSuggest, state.themeMode == ThemeMode.SISTEMA, Modifier.weight(1f)) {
                        viewModel.setThemeMode(ThemeMode.SISTEMA)
                    }
                }
            }
        }

        // ─── Gestión de Datos & Privacidad ─────────────────────────────────
        SectionHeader("Datos & Privacidad")
        SettingsCard {
            SettingsItem(
                icon      = Icons.Default.Share,
                iconColor = Cyan400,
                title     = "Exportar Resumen Biométrico",
                subtitle  = "Copia de seguridad en formato JSON estructurado",
                trailing  = {
                    OutlinedButton(
                        onClick = { viewModel.exportData(context) },
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = Cyan400),
                        border  = BorderStroke(1.dp, Cyan400)
                    ) { Text("Exportar", style = MaterialTheme.typography.labelMedium) }
                }
            )
            HorizontalDivider(color = Indigo700)
            SettingsItem(
                icon      = Icons.Default.CleaningServices,
                iconColor = Amber400,
                title     = "Limpiar Caché Temporal",
                subtitle  = "Libera memoria y optimiza el rendimiento",
                trailing  = {
                    OutlinedButton(
                        onClick = {
                            context.cacheDir.deleteRecursively()
                            Toast.makeText(context, "Caché temporal liberada", Toast.LENGTH_SHORT).show()
                        },
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = Amber400),
                        border  = BorderStroke(1.dp, Amber400)
                    ) { Text("Limpiar", style = MaterialTheme.typography.labelMedium) }
                }
            )
        }

        // ─── Información de la App ─────────────────────────────────────────
        SectionHeader("Información")
        SettingsCard {
            SettingsItem(
                icon      = Icons.Default.Info,
                iconColor = Gray400,
                title     = "Life Dashboard",
                subtitle  = "v1.4 · Dashboard Biométrico y Hábitos",
                trailing  = {}
            )
        }
    }

    // ─── Modales ──────────────────────────────────────────────────────────

    if (showProfileModal) {
        EnhancedProfileModal(
            currentName   = state.userName,
            currentLevel  = state.fitnessLevel,
            currentGoal   = state.primaryGoal,
            currentWeight = state.weightKg,
            currentHeight = state.heightCm,
            onDismiss     = { showProfileModal = false },
            onSave        = { name, level, goal, weight, height ->
                viewModel.updateProfile(name, level, goal, weight, height)
                showProfileModal = false
                Toast.makeText(context, "Perfil biométrico actualizado", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showApiKeyModal) {
        EditApiKeyModal(
            currentKey = state.geminiApiKey,
            onDismiss  = { showApiKeyModal = false },
            onTest     = { key -> viewModel.testApiKey(key) },
            onSave     = { newKey ->
                viewModel.updateGeminiApiKey(newKey)
                showApiKeyModal = false
                Toast.makeText(context, "Clave de Gemini guardada correctamente", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showStepsModal) {
        EditStepsGoalModal(
            currentValue = state.stepsTarget,
            onDismiss    = { showStepsModal = false },
            onSave       = { newSteps ->
                viewModel.updateStepsTarget(newSteps)
                showStepsModal = false
                Toast.makeText(context, "Objetivo de pasos actualizado", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showSleepModal) {
        EditSleepGoalModal(
            currentValue = state.sleepTargetHours,
            onDismiss    = { showSleepModal = false },
            onSave       = { newSleep ->
                viewModel.updateSleepTarget(newSleep)
                showSleepModal = false
                Toast.makeText(context, "Objetivo de sueño actualizado", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showCaloriesModal) {
        EditCaloriesGoalModal(
            currentValue = state.caloriesTarget,
            onDismiss    = { showCaloriesModal = false },
            onSave       = { newCal ->
                viewModel.updateCaloriesTarget(newCal)
                showCaloriesModal = false
                Toast.makeText(context, "Objetivo de calorías actualizado", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showWorkoutModal) {
        EditWorkoutGoalModal(
            currentDays    = state.workoutDaysTarget,
            currentMinutes = state.workoutMinutesTarget,
            onDismiss      = { showWorkoutModal = false },
            onSave         = { days, minutes ->
                viewModel.updateWorkoutTargets(days, minutes)
                showWorkoutModal = false
                Toast.makeText(context, "Meta de entrenamiento actualizada", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showAddWeightModal) {
        AddWeightEntryModal(
            currentWeight = state.weightHistory.firstOrNull()?.weightKg ?: state.weightKg,
            onDismiss     = { showAddWeightModal = false },
            onSave        = { weight, note ->
                viewModel.logWeight(weight, note)
                showAddWeightModal = false
                Toast.makeText(context, "Pesaje registrado con éxito", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showWeightHistoryModal) {
        WeightHistoryModal(
            history   = state.weightHistory,
            onDismiss = { showWeightHistoryModal = false },
            onDelete  = { id ->
                viewModel.deleteWeightEntry(id)
                Toast.makeText(context, "Registro eliminado", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showWakeUpTimeModal) {
        EditWakeUpTimeModal(
            currentHour   = state.wakeUpHour,
            currentMinute = state.wakeUpMinute,
            onDismiss     = { showWakeUpTimeModal = false },
            onSave        = { h, m ->
                viewModel.updateWakeUpTime(h, m)
                showWakeUpTimeModal = false
                Toast.makeText(context, "Hora para levantarse actualizada", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showMacrosModal) {
        EditMacrosGoalModal(
            currentProtein = state.proteinTargetGrams,
            currentCarbs   = state.carbsTargetGrams,
            currentFat     = state.fatTargetGrams,
            onDismiss      = { showMacrosModal = false },
            onSave         = { p, c, f ->
                viewModel.updateMacroTargets(p, c, f)
                showMacrosModal = false
                Toast.makeText(context, "Objetivos de macronutrientes actualizados", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// ─── Componente: Tarjeta de Perfil Avanzada ───────────────────────────────────

@Composable
private fun EnhancedUserProfileCard(
    userName: String,
    fitnessLevel: String,
    primaryGoal: String,
    weightKg: Float,
    heightCm: Int,
    onOpenProfile: () -> Unit,
    onSyncAll: () -> Unit
) {
    val imc = if (heightCm > 0) {
        val hM = heightCm / 100f
        String.format(Locale.US, "%.1f", weightKg / (hM * hM))
    } else "24.0"

    val levelLabel = when (fitnessLevel) {
        "PRINCIPIANTE" -> "Principiante"
        "AVANZADO"     -> "Avanzado"
        "ATLETA"       -> "Atleta"
        else           -> "Intermedio"
    }

    val goalLabel = when (primaryGoal) {
        "GRASA"   -> "Pérdida de Grasa"
        "MUSCULO" -> "Hipertrofia"
        "CARDIO"  -> "Resistencia Cardio"
        else      -> "Salud Integral"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Violet400.copy(alpha = 0.22f),
                        Indigo800
                    ),
                    radius = 800f
                )
            )
            .border(1.dp, Indigo700, RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenProfile)
                ) {
                    // Avatar con inicial
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Violet400, Color(0xFF6366F1))
                                )
                            )
                    ) {
                        Text(
                            text       = userName.firstOrNull()?.uppercase() ?: "J",
                            fontSize   = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = White
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column {
                        Text(
                            text       = userName,
                            style      = MaterialTheme.typography.titleMedium,
                            color      = White,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 18.sp
                        )
                        Spacer(Modifier.height(3.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Violet400.copy(alpha = 0.25f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(levelLabel, fontSize = 10.sp, color = Violet300, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Cyan400.copy(alpha = 0.20f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(goalLabel, fontSize = 10.sp, color = Cyan400, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                IconButton(
                    onClick  = onOpenProfile,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Indigo700.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar perfil", tint = White, modifier = Modifier.size(16.dp))
                }
            }

            // Datos antropométricos
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricChip("Peso", "${weightKg.toInt()} kg", Modifier.weight(1f))
                MetricChip("Altura", "${heightCm} cm", Modifier.weight(1f))
                MetricChip("IMC", imc, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Indigo900.copy(alpha = 0.6f))
            .border(1.dp, Indigo700.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Gray400, fontSize = 10.sp)
            Spacer(Modifier.height(2.dp))
            Text(text = value, style = MaterialTheme.typography.labelMedium, color = White, fontWeight = FontWeight.Bold)
        }
    }
}

// ─── Componente: Seguimiento de Peso & Composición ───────────────────────────

@Composable
private fun WeightTrackerCard(
    latestWeight: Float,
    heightCm: Int,
    weightHistory: List<WeightEntryEntity>,
    onAddWeight: () -> Unit,
    onViewHistory: () -> Unit
) {
    val imc = if (heightCm > 0) {
        val hM = heightCm / 100f
        latestWeight / (hM * hM)
    } else 24.0f

    val imcCategory = when {
        imc < 18.5f -> "Bajo peso" to Cyan400
        imc < 25.0f -> "Peso saludable" to Emerald400
        imc < 30.0f -> "Sobrepeso" to Amber400
        else        -> "Obesidad" to Rose400
    }

    val deltaTextAndColor: Triple<String, Color, ImageVector?> = if (weightHistory.size >= 2) {
        val diff = latestWeight - weightHistory[1].weightKg
        when {
            diff < -0.05f -> Triple(String.format(Locale.US, "-%.1f kg", Math.abs(diff)), Emerald400, Icons.Default.ArrowDownward)
            diff > 0.05f  -> Triple(String.format(Locale.US, "+%.1f kg", diff), Amber400, Icons.Default.ArrowUpward)
            else          -> Triple("0.0 kg", Gray400, Icons.Default.Remove)
        }
    } else if (weightHistory.size == 1) {
        Triple("Primer registro", Violet300, null)
    } else {
        Triple("Sin historial", Gray400, null)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Indigo800,
                        Indigo900.copy(alpha = 0.9f)
                    )
                )
            )
            .border(1.dp, Indigo700, RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Cabecera con título y botón de registro
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Violet400.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = Violet300,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "Seguimiento de Peso",
                            style = MaterialTheme.typography.titleMedium,
                            color = White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Progreso de composición física",
                            style = MaterialTheme.typography.bodySmall,
                            color = Gray400,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                IconButton(
                    onClick  = onAddWeight,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Violet400)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Registrar pesaje",
                        tint = Indigo900,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Fila central: Peso Actual + Variación + IMC
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Indigo700.copy(alpha = 0.35f))
                    .border(1.dp, Indigo700.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column {
                    Text(
                        text = "ÚLTIMO REGISTRO",
                        style = MaterialTheme.typography.labelSmall,
                        color = Gray400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format(Locale.US, "%.1f", latestWeight),
                            style = MaterialTheme.typography.headlineMedium,
                            color = White,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "kg",
                            style = MaterialTheme.typography.titleMedium,
                            color = Violet300,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }

                // Variación respecto al pesaje anterior
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "VARIACIÓN",
                        style = MaterialTheme.typography.labelSmall,
                        color = Gray400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        deltaTextAndColor.third?.let { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = deltaTextAndColor.second,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                        }
                        Text(
                            text = deltaTextAndColor.first,
                            style = MaterialTheme.typography.bodyMedium,
                            color = deltaTextAndColor.second,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "IMC ${String.format(Locale.US, "%.1f", imc)} · ${imcCategory.first}",
                        style = MaterialTheme.typography.labelSmall,
                        color = imcCategory.second,
                        fontSize = 11.sp
                    )
                }
            }

            // Gráfica interactiva de evolución de peso
            if (weightHistory.isNotEmpty()) {
                WeightEvolutionChart(
                    weightHistory = weightHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )

                // Enlace al historial completo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onViewHistory)
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "Ver historial completo (${weightHistory.size} registros)",
                        style = MaterialTheme.typography.labelMedium,
                        color = Violet300,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Violet300,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Text(
                    text = "Aún no has registrado ningún pesaje. Pulsa en 'Pesar' para comenzar tu seguimiento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray500
                )
            }
        }
    }
}

// ─── Modales de Pesaje ────────────────────────────────────────────────────────

@Composable
private fun AddWeightEntryModal(
    currentWeight: Float,
    onDismiss: () -> Unit,
    onSave: (Float, String?) -> Unit
) {
    var weightInput by remember { mutableStateOf(String.format(Locale.US, "%.1f", currentWeight)) }
    var noteInput by remember { mutableStateOf("") }

    val adjustWeight = { delta: Float ->
        val current = weightInput.toFloatOrNull() ?: currentWeight
        val updated = (current + delta).coerceAtLeast(30.0f)
        weightInput = String.format(Locale.US, "%.1f", updated)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        shape            = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Violet400.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = Violet300,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text("Registrar Pesaje", color = White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it.replace(',', '.') },
                    label = { Text("Peso corporal (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Botones rápidos de ajuste
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(-0.5f, -0.1f, +0.1f, +0.5f).forEach { delta ->
                        val sign = if (delta > 0) "+$delta" else "$delta"
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Indigo700)
                                .clickable { adjustWeight(delta) }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "$sign kg",
                                style = MaterialTheme.typography.labelSmall,
                                color = White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Nota (opcional)") },
                    placeholder = { Text("Ej. En ayunas, tras entrenar...", color = Gray500) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = weightInput.toFloatOrNull() ?: currentWeight
                    onSave(w, noteInput.takeIf { it.isNotBlank() })
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Violet400,
                    contentColor = Indigo900
                )
            ) {
                Text("Guardar Pesaje", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Gray400)
            }
        }
    )
}

@Composable
private fun WeightHistoryModal(
    history: List<WeightEntryEntity>,
    onDismiss: () -> Unit,
    onDelete: (Long) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale("es")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        shape            = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = Violet300,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Historial de Pesajes", color = White, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "${history.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Gray400
                )
            }
        },
        text = {
            if (history.isEmpty()) {
                Text(
                    text = "No hay registros guardados en tu historial.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray400,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    history.forEach { entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Indigo900.copy(alpha = 0.6f))
                                .border(1.dp, Indigo700.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", entry.weightKg)} kg",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = dateFormat.format(Date(entry.dateMillis)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Gray400,
                                    fontSize = 11.sp
                                )
                                if (!entry.note.isNullOrBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = entry.note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Violet300,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onDelete(entry.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Eliminar pesaje",
                                    tint = Rose400,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors  = ButtonDefaults.buttonColors(containerColor = Violet400, contentColor = Indigo900)
            ) {
                Text("Cerrar", fontWeight = FontWeight.Bold)
            }
        }
    )
}

// ─── Modal: Editar Perfil Completo ────────────────────────────────────────────

@Composable
private fun EnhancedProfileModal(
    currentName: String,
    currentLevel: String,
    currentGoal: String,
    currentWeight: Float,
    currentHeight: Int,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Float, Int) -> Unit
) {
    var nameInput by remember { mutableStateOf(currentName) }
    var selectedLevel by remember { mutableStateOf(currentLevel) }
    var selectedGoal by remember { mutableStateOf(currentGoal) }
    var weightInput by remember { mutableStateOf(currentWeight.toInt().toString()) }
    var heightInput by remember { mutableStateOf(currentHeight.toString()) }

    val levels = listOf(
        "PRINCIPIANTE" to "Principiante",
        "INTERMEDIO"   to "Intermedio",
        "AVANZADO"     to "Avanzado",
        "ATLETA"       to "Atleta"
    )

    val goals = listOf(
        "SALUD_GENERAL" to "Salud General",
        "GRASA"         to "Bajar Grasa",
        "MUSCULO"       to "Ganar Músculo",
        "CARDIO"        to "Rendimiento"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        shape            = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Violet300, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Perfil Biométrico", color = White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value         = nameInput,
                    onValueChange = { nameInput = it },
                    label         = { Text("Nombre") },
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet400, unfocusedBorderColor = Indigo600,
                        focusedTextColor = White, unfocusedTextColor = White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Nivel de Entrenamiento:", style = MaterialTheme.typography.labelMedium, color = Gray300)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    levels.take(2).forEach { (key, label) ->
                        OptionChip(label, selectedLevel == key, Modifier.weight(1f)) { selectedLevel = key }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    levels.drop(2).forEach { (key, label) ->
                        OptionChip(label, selectedLevel == key, Modifier.weight(1f)) { selectedLevel = key }
                    }
                }

                Text("Objetivo Primario:", style = MaterialTheme.typography.labelMedium, color = Gray300)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    goals.take(2).forEach { (key, label) ->
                        OptionChip(label, selectedGoal == key, Modifier.weight(1f)) { selectedGoal = key }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    goals.drop(2).forEach { (key, label) ->
                        OptionChip(label, selectedGoal == key, Modifier.weight(1f)) { selectedGoal = key }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value         = weightInput,
                        onValueChange = { weightInput = it },
                        label         = { Text("Peso (kg)") },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Violet400, unfocusedBorderColor = Indigo600,
                            focusedTextColor = White, unfocusedTextColor = White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value         = heightInput,
                        onValueChange = { heightInput = it },
                        label         = { Text("Altura (cm)") },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Violet400, unfocusedBorderColor = Indigo600,
                            focusedTextColor = White, unfocusedTextColor = White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = weightInput.toFloatOrNull() ?: currentWeight
                    val h = heightInput.toIntOrNull() ?: currentHeight
                    onSave(nameInput, selectedLevel, selectedGoal, w, h)
                },
                colors  = ButtonDefaults.buttonColors(containerColor = Violet400, contentColor = Indigo900)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Gray400) }
        }
    )
}

// ─── Modales Auxiliares de Metas ─────────────────────────────────────────────

@Composable
private fun EditStepsGoalModal(currentValue: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var stepsText by remember { mutableStateOf(currentValue.toString()) }
    val stepOptions = listOf(6000, 8000, 10000, 12000, 15000)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        shape            = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, tint = RingSteps, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Objetivo de Pasos", color = White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Escribe tu objetivo diario personalizado de pasos:", style = MaterialTheme.typography.bodySmall, color = Gray400)

                OutlinedTextField(
                    value = stepsText,
                    onValueChange = { stepsText = it.filter { char -> char.isDigit() } },
                    label = { Text("Pasos diarios", color = Cyan400) },
                    trailingIcon = { Text("pasos", color = Gray400, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 12.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cyan400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("O elige una sugerencia rápida:", style = MaterialTheme.typography.labelSmall, color = Gray400)

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    stepOptions.forEach { steps ->
                        val isSelected = stepsText == steps.toString()
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) RingSteps else Indigo700)
                                .clickable { stepsText = steps.toString() }
                                .padding(vertical = 8.dp)
                        ) {
                            Text("${steps / 1000}k", color = if (isSelected) Indigo900 else White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = stepsText.toIntOrNull()?.coerceIn(1000, 100000) ?: currentValue
                    onSave(s)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RingSteps, contentColor = Indigo900)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Gray400) } }
    )
}

@Composable
private fun EditSleepGoalModal(currentValue: Float, onDismiss: () -> Unit, onSave: (Float) -> Unit) {
    var sleepText by remember { mutableStateOf(String.format(Locale.US, "%.1f", currentValue)) }
    val sleepOptions = listOf(6.5f, 7.0f, 7.5f, 8.0f, 8.5f, 9.0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        shape            = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bedtime, null, tint = RingSleep, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Objetivo de Sueño", color = White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Escribe las horas de descanso nocturno deseadas:", style = MaterialTheme.typography.bodySmall, color = Gray400)

                OutlinedTextField(
                    value = sleepText,
                    onValueChange = { sleepText = it.replace(',', '.') },
                    label = { Text("Horas de sueño", color = Teal400) },
                    trailingIcon = { Text("horas", color = Gray400, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 12.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Teal400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("O elige una sugerencia rápida:", style = MaterialTheme.typography.labelSmall, color = Gray400)

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    sleepOptions.forEach { hours ->
                        val formatted = String.format(Locale.US, "%.1f", hours)
                        val isSelected = sleepText == formatted || sleepText == hours.toString()
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) RingSleep else Indigo700)
                                .clickable { sleepText = formatted }
                                .padding(vertical = 8.dp)
                        ) {
                            Text("${hours}h", color = if (isSelected) Indigo900 else White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val h = sleepText.toFloatOrNull()?.coerceIn(4f, 14f) ?: currentValue
                    onSave(h)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RingSleep, contentColor = Indigo900)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Gray400) } }
    )
}

@Composable
private fun EditCaloriesGoalModal(currentValue: Float, onDismiss: () -> Unit, onSave: (Float) -> Unit) {
    var calText by remember { mutableStateOf(currentValue.toInt().toString()) }
    val calOptions = listOf(1600f, 1800f, 2000f, 2200f, 2500f, 2800f)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        shape            = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalFireDepartment, null, tint = Amber400, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Meta de Calorías Diarias", color = White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Escribe tu requerimiento calórico objetivo:", style = MaterialTheme.typography.bodySmall, color = Gray400)

                OutlinedTextField(
                    value = calText,
                    onValueChange = { calText = it.filter { char -> char.isDigit() } },
                    label = { Text("Calorías diarias", color = Amber400) },
                    trailingIcon = { Text("kcal", color = Gray400, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 12.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("O elige una sugerencia rápida:", style = MaterialTheme.typography.labelSmall, color = Gray400)

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    calOptions.forEach { cal ->
                        val isSelected = calText == cal.toInt().toString()
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Amber400 else Indigo700)
                                .clickable { calText = cal.toInt().toString() }
                                .padding(vertical = 8.dp)
                        ) {
                            Text("${cal.toInt()}", color = if (isSelected) Indigo900 else White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val c = calText.toFloatOrNull()?.coerceIn(800f, 8000f) ?: currentValue
                    onSave(c)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Amber400, contentColor = Indigo900)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Gray400) } }
    )
}

@Composable
private fun EditWorkoutGoalModal(currentDays: Int, currentMinutes: Int, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    var daysText by remember { mutableStateOf(currentDays.toString()) }
    var minutesText by remember { mutableStateOf(currentMinutes.toString()) }
    val dayOptions = listOf(2, 3, 4, 5, 6)
    val minuteOptions = listOf(120, 150, 180, 240, 300)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        shape            = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FitnessCenter, null, tint = Violet300, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Meta de Entrenamientos", color = White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Personaliza tus objetivos semanales escribiendo los valores:", style = MaterialTheme.typography.bodySmall, color = Gray400)

                OutlinedTextField(
                    value = daysText,
                    onValueChange = { daysText = it.filter { char -> char.isDigit() } },
                    label = { Text("Días por semana", color = Violet300) },
                    trailingIcon = { Text("días", color = Gray400, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 12.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    dayOptions.forEach { days ->
                        OptionChip("$days d", days.toString() == daysText, Modifier.weight(1f)) { daysText = days.toString() }
                    }
                }

                Spacer(Modifier.height(4.dp))

                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter { char -> char.isDigit() } },
                    label = { Text("Minutos totales semanales", color = Violet300) },
                    trailingIcon = { Text("min", color = Gray400, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 12.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    minuteOptions.forEach { mins ->
                        OptionChip("${mins}m", mins.toString() == minutesText, Modifier.weight(1f)) { minutesText = mins.toString() }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val d = daysText.toIntOrNull()?.coerceIn(1, 7) ?: currentDays
                    val m = minutesText.toIntOrNull()?.coerceIn(30, 2000) ?: currentMinutes
                    onSave(d, m)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Violet400, contentColor = Indigo900)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Gray400) } }
    )
}

@Composable
private fun EditApiKeyModal(
    currentKey: String,
    onDismiss: () -> Unit,
    onTest: (suspend (String) -> Result<String>)? = null,
    onSave: (String) -> Unit
) {
    var keyInput by remember { mutableStateOf(currentKey) }
    var isTesting by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var testIsSuccess by remember { mutableStateOf<Boolean?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        shape            = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, contentDescription = null, tint = Violet300, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Google Gemini API Key", color = White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text  = "Consigue una clave gratis en Google AI Studio (aistudio.google.com). Con tu clave, la IA de Gemini 2.0 Flash analizará tus fotos de platos y descripciones con máxima precisión.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray300
                )

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Abre aistudio.google.com en tu navegador", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan400),
                    border = BorderStroke(1.dp, Cyan400.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Obtener Clave Gratis en Google AI Studio", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                OutlinedTextField(
                    value         = keyInput,
                    onValueChange = {
                        keyInput = it
                        testResultText = null
                        testIsSuccess = null
                    },
                    label         = { Text("Clave API de Gemini", color = Gray400) },
                    placeholder   = { Text("AIzaSy...", color = Gray500) },
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet400, unfocusedBorderColor = Indigo600,
                        focusedTextColor = White, unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (onTest != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                if (keyInput.isBlank()) {
                                    Toast.makeText(context, "Escribe o pega una clave primero", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isTesting = true
                                testResultText = null
                                coroutineScope.launch {
                                    val res = onTest(keyInput.trim())
                                    isTesting = false
                                    if (res.isSuccess) {
                                        testIsSuccess = true
                                        testResultText = res.getOrNull() ?: "Conexión exitosa"
                                    } else {
                                        testIsSuccess = false
                                        testResultText = res.exceptionOrNull()?.message ?: "Error al conectar"
                                    }
                                }
                            },
                            enabled = !isTesting && keyInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo700, contentColor = White),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(color = Violet300, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Comprobando...", style = MaterialTheme.typography.labelSmall)
                            } else {
                                Icon(Icons.Default.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(14.dp), tint = Violet300)
                                Spacer(Modifier.width(6.dp))
                                Text("Probar Clave", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        if (keyInput.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    keyInput = ""
                                    testResultText = null
                                    testIsSuccess = null
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Borrar", style = MaterialTheme.typography.labelSmall, color = Rose400)
                            }
                        }
                    }
                }

                testResultText?.let { msg ->
                    val isSuccess = testIsSuccess == true
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSuccess) Emerald400.copy(alpha = 0.2f) else Rose400.copy(alpha = 0.2f))
                            .border(1.dp, if (isSuccess) Emerald400.copy(alpha = 0.5f) else Rose400.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = (if (isSuccess) "✅ " else "❌ ") + msg,
                            color = if (isSuccess) Emerald400 else Rose400,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(keyInput) },
                colors = ButtonDefaults.buttonColors(containerColor = Violet400, contentColor = Indigo900),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Gray400)
            }
        }
    )
}

// ─── Microcomponentes Reutilizables ──────────────────────────────────────────

@Composable
private fun OptionChip(label: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Violet400 else Indigo700)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp)
    ) {
        Text(
            text       = label,
            style      = MaterialTheme.typography.labelSmall,
            color      = if (isSelected) Indigo900 else White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ThemeOptionChip(label: String, icon: ImageVector, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Violet400 else Indigo700)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (isSelected) Indigo900 else White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (isSelected) Indigo900 else White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SwitchSettingItem(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Gray500)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor   = Indigo900,
                checkedTrackColor   = Violet400,
                uncheckedThumbColor = Gray400,
                uncheckedTrackColor = Indigo700
            )
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(text = title, style = MaterialTheme.typography.labelLarge, color = Gray500)
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Indigo800)
            .border(1.dp, Indigo700, RoundedCornerShape(20.dp)),
        content  = content
    )
}

@Composable
private fun SettingsItem(icon: ImageVector, iconColor: Color, title: String, subtitle: String, trailing: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(iconColor.copy(alpha = 0.15f))
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, color = White, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Gray500, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

private fun openHealthConnectSettings(context: Context) {
    val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val playStoreIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=com.google.android.apps.healthdata")
            )
            context.startActivity(playStoreIntent)
        } catch (e2: Exception) {
            Toast.makeText(context, "Health Connect no disponible en este dispositivo", Toast.LENGTH_SHORT).show()
        }
    }
}

// ─── Modal: Configuración de Hora para Levantarse ─────────────────────────────

@Composable
private fun EditWakeUpTimeModal(
    currentHour: Int,
    currentMinute: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit
) {
    var selectedHour by remember { mutableIntStateOf(currentHour) }
    var selectedMinute by remember { mutableIntStateOf(currentMinute) }

    val presetTimes = listOf(
        6 to 0,
        6 to 30,
        7 to 0,
        7 to 30,
        8 to 0,
        8 to 30,
        9 to 0,
        9 to 30
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        shape            = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Alarm, contentDescription = null, tint = RingSleep, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Hora para Levantarse", color = White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Selecciona tu hora habitual de despertar para que Vital calcule cuándo avisarte (30 min antes de dormir):",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray300
                )

                // Presets en cuadrícula
                presetTimes.chunked(4).forEach { rowPresets ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        rowPresets.forEach { (h, m) ->
                            val isSelected = h == selectedHour && m == selectedMinute
                            val formatted = String.format(Locale.US, "%02d:%02d", h, m)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) RingSleep else Indigo700)
                                    .clickable {
                                        selectedHour = h
                                        selectedMinute = m
                                    }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(
                                    text = formatted,
                                    color = if (isSelected) Indigo900 else White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Selector con botones +/- de 15 minutos para ajuste fino
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Indigo900.copy(alpha = 0.6f))
                        .border(1.dp, Indigo700.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = {
                                val totalMin = (selectedHour * 60 + selectedMinute - 15 + 1440) % 1440
                                selectedHour = totalMin / 60
                                selectedMinute = totalMin % 60
                            },
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Indigo700)
                        ) {
                            Text("-15", color = White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = String.format(Locale.US, "%02d:%02d", selectedHour, selectedMinute),
                            style = MaterialTheme.typography.headlineMedium,
                            color = White,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            onClick = {
                                val totalMin = (selectedHour * 60 + selectedMinute + 15) % 1440
                                selectedHour = totalMin / 60
                                selectedMinute = totalMin % 60
                            },
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Indigo700)
                        ) {
                            Text("+15", color = White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedHour, selectedMinute) },
                colors  = ButtonDefaults.buttonColors(containerColor = RingSleep, contentColor = Indigo900)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Gray400) }
        }
    )
}

@Composable
private fun EditMacrosGoalModal(
    currentProtein: Float,
    currentCarbs: Float,
    currentFat: Float,
    onDismiss: () -> Unit,
    onSave: (Float, Float, Float) -> Unit
) {
    var proteinText by remember { mutableStateOf("${currentProtein.toInt()}") }
    var carbsText by remember { mutableStateOf("${currentCarbs.toInt()}") }
    var fatText by remember { mutableStateOf("${currentFat.toInt()}") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Restaurant, contentDescription = null, tint = Emerald400, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Objetivo de Macros", color = White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Define tus objetivos diarios en gramos para cada macronutriente:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray400
                )

                // Proteínas
                OutlinedTextField(
                    value = proteinText,
                    onValueChange = { proteinText = it },
                    label = { Text("Proteínas (g)", color = Violet300) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Carbohidratos
                OutlinedTextField(
                    value = carbsText,
                    onValueChange = { carbsText = it },
                    label = { Text("Carbohidratos (g)", color = Cyan400) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cyan400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Grasas
                OutlinedTextField(
                    value = fatText,
                    onValueChange = { fatText = it },
                    label = { Text("Grasas (g)", color = Amber400) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = proteinText.toFloatOrNull() ?: currentProtein
                    val c = carbsText.toFloatOrNull() ?: currentCarbs
                    val f = fatText.toFloatOrNull() ?: currentFat
                    onSave(p, c, f)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald400, contentColor = Indigo900)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Gray400) }
        }
    )
}
