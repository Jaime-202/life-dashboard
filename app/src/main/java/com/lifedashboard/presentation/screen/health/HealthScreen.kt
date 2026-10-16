package com.lifedashboard.presentation.screen.health

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lifedashboard.core.worker.WorkManagerScheduler
import com.lifedashboard.data.local.dao.HealthDao
import com.lifedashboard.data.local.entity.HealthDataEntity
import com.lifedashboard.data.repository.UserPreferencesRepository
import com.lifedashboard.presentation.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

enum class HealthTab { PASOS, SUENO, CALORIAS, CORAZON }

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class HealthUiState(
    val last14Days: List<HealthDataEntity> = emptyList(),
    val stepsTarget: Int = 10000,
    val sleepTarget: Float = 8.0f,
    val caloriesTarget: Int = 2200,
    val isSyncing: Boolean = false
)

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val healthDao: HealthDao,
    private val userPreferences: UserPreferencesRepository,
    private val scheduler: WorkManagerScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthUiState())
    val uiState: StateFlow<HealthUiState> = _uiState.asStateFlow()

    init {
        healthDao.observeLastNDays(14)
            .onEach { list ->
                _uiState.update { it.copy(last14Days = list) }
            }
            .flowOn(Dispatchers.IO)
            .launchIn(viewModelScope)

        userPreferences.userGoals
            .onEach { goals ->
                _uiState.update {
                    it.copy(
                        stepsTarget    = goals.stepsTarget,
                        sleepTarget    = goals.sleepTargetHours,
                        caloriesTarget = goals.caloriesTarget.toInt()
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun triggerSync() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            scheduler.runHealthSyncNow()
            kotlinx.coroutines.delay(1200)
            _uiState.update { it.copy(isSyncing = false) }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun HealthScreen(
    initialTabName: String = "PASOS",
    onBack: () -> Unit = {},
    viewModel: HealthViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val initialTab = remember(initialTabName) {
        when (initialTabName.uppercase()) {
            "SUENO", "SUEÑO" -> HealthTab.SUENO
            "CALORIAS", "CALORÍAS" -> HealthTab.CALORIAS
            "CORAZON", "CORAZÓN", "CARDIO", "HEART", "PULSO" -> HealthTab.CORAZON
            else -> HealthTab.PASOS
        }
    }
    var selectedTab by remember { mutableStateOf(initialTab) }

    LaunchedEffect(initialTabName) {
        selectedTab = when (initialTabName.uppercase()) {
            "SUENO", "SUEÑO" -> HealthTab.SUENO
            "CALORIAS", "CALORÍAS" -> HealthTab.CALORIAS
            "CORAZON", "CORAZÓN", "CARDIO", "HEART", "PULSO" -> HealthTab.CORAZON
            else -> HealthTab.PASOS
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
        // Cabecera superior moderna con botón de volver al panel y sincronización
        HealthHeader(
            onBack = onBack,
            isSyncing = state.isSyncing,
            onSyncClick = { viewModel.triggerSync() }
        )

        // ─── Selector de Pestañas Segmentado Neón ─────────────────────────
        ModernTabSelector(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it }
        )

        // ─── Contenido según sub-apartado (Visualización e Insights) ───────
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(180)) },
            label = "health_tab_content"
        ) { tab ->
            when (tab) {
                HealthTab.PASOS    -> PasosSubSection(state = state)
                HealthTab.SUENO    -> SuenoSubSection(state = state)
                HealthTab.CALORIAS -> CaloriasSubSection(state = state)
                HealthTab.CORAZON  -> CorazonSubSection(state = state)
            }
        }
    }
}

// ─── Header con botón de volver al Panel ─────────────────────────────────────

@Composable
private fun HealthHeader(
    onBack: () -> Unit,
    isSyncing: Boolean,
    onSyncClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "health_sync_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "health_sync_rotation_anim"
    )

    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.weight(1f)
        ) {
            IconButton(
                onClick  = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Indigo800)
                    .border(1.dp, Indigo700, CircleShape)
            ) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver al Panel",
                    tint               = White,
                    modifier           = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text       = "Salud & Metas",
                    style      = MaterialTheme.typography.titleLarge,
                    color      = White,
                    fontWeight = FontWeight.Bold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(
                    text       = "Métricas biométricas en tiempo real",
                    style      = MaterialTheme.typography.bodySmall,
                    color      = Gray400,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        IconButton(
            onClick  = onSyncClick,
            enabled  = !isSyncing,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isSyncing) Violet400.copy(alpha = 0.2f) else Indigo800)
                .border(1.dp, if (isSyncing) Violet400 else Indigo700, CircleShape)
        ) {
            Icon(
                imageVector        = Icons.Default.Sync,
                contentDescription = if (isSyncing) "Sincronizando..." else "Sincronizar Salud",
                tint               = if (isSyncing) Violet300 else White,
                modifier           = Modifier
                    .size(20.dp)
                    .then(if (isSyncing) Modifier.rotate(rotation) else Modifier)
            )
        }
    }
}

// ─── Selector de pestañas rediseñado ──────────────────────────────────────────

@Composable
private fun ModernTabSelector(
    selectedTab: HealthTab,
    onTabSelected: (HealthTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Indigo800)
            .border(1.dp, Indigo700, RoundedCornerShape(18.dp))
            .padding(6.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            HealthTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                val (label, icon, color) = when (tab) {
                    HealthTab.PASOS    -> Triple("Pasos", Icons.AutoMirrored.Filled.DirectionsWalk, RingSteps)
                    HealthTab.SUENO    -> Triple("Sueño", Icons.Default.Bedtime, RingSleep)
                    HealthTab.CALORIAS -> Triple("Calorías", Icons.Default.LocalFireDepartment, Amber400)
                    HealthTab.CORAZON  -> Triple("Pulso", Icons.Default.Favorite, Rose400)
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) color else Color.Transparent)
                        .clickable { onTabSelected(tab) }
                        .padding(vertical = 9.dp, horizontal = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector        = icon,
                            contentDescription = null,
                            tint               = if (isSelected) Indigo900 else Gray400,
                            modifier           = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text       = label,
                            style      = MaterialTheme.typography.labelSmall,
                            color      = if (isSelected) Indigo900 else Gray300,
                            fontWeight = FontWeight.Bold,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ─── Sub-apartado Pasos ───────────────────────────────────────────────────────

@Composable
private fun PasosSubSection(state: HealthUiState) {
    val todaySteps = state.last14Days.firstOrNull()?.steps ?: 0
    val todayDistKm = if ((state.last14Days.firstOrNull()?.distanceMeters ?: 0f) > 0f) {
        (state.last14Days.firstOrNull()?.distanceMeters ?: 0f) / 1000f
    } else {
        todaySteps * 0.00075f
    }
    val todayKcal = (todaySteps * 0.04f).toInt()
    val activeMinutes = (todaySteps / 100).coerceAtLeast(0)

    val progress = (todaySteps.toFloat() / state.stepsTarget).coerceIn(0f, 1f)
    val metDaysThisWeek = state.last14Days.take(7).count { it.steps >= state.stepsTarget }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // Hero Card de Pasos
        HealthHeroCard(
            title         = "Pasos Diarios",
            currentVal    = "$todaySteps",
            targetVal     = "${state.stepsTarget}",
            unit          = "pasos",
            progress      = progress,
            color         = RingSteps,
            icon          = Icons.AutoMirrored.Filled.DirectionsWalk,
            metric1Label  = "Distancia",
            metric1Value  = "${String.format(Locale.US, "%.2f", todayDistKm)} km",
            metric2Label  = "Quemadas",
            metric2Value  = "$todayKcal kcal",
            metric3Label  = "Tiempo activo",
            metric3Value  = "$activeMinutes min"
        )

        // Gráfico Semanal de Barras Interactivo
        WeeklyBarChart(
            title     = "Actividad de los Últimos 7 Días",
            data      = state.last14Days.take(7).reversed(),
            getValue  = { it.steps.toFloat() },
            target    = state.stepsTarget.toFloat(),
            barColor  = RingSteps,
            formatVal = { "${(it / 1000).toInt()}K" }
        )

        // Tarjeta de Consistencia y Racha
        ConsistencyCard(
            title     = "Racha de Pasos Semanal",
            subtitle  = if (metDaysThisWeek >= 5) "¡Excelente ritmo! Has cumplido la meta $metDaysThisWeek de los últimos 7 días."
                        else "Has alcanzado tu objetivo $metDaysThisWeek de 7 días esta semana. ¡Sigue sumando pasos!",
            color     = RingSteps,
            badgeText = "$metDaysThisWeek / 7 días",
            icon      = Icons.Default.EmojiEvents
        )

        // Historial Detallado
        HistoryListCard(
            title     = "Historial Detallado",
            data      = state.last14Days,
            getValue  = { it.steps.toFloat() },
            target    = state.stepsTarget.toFloat(),
            color     = RingSteps,
            formatVal = { "${it.toInt()} pasos" },
            subFormat = { item ->
                val dist = if (item.distanceMeters > 0) item.distanceMeters / 1000f else item.steps * 0.00075f
                "${String.format(Locale.US, "%.1f", dist)} km"
            }
        )
    }
}

// ─── Sub-apartado Sueño ───────────────────────────────────────────────────────

@Composable
private fun SuenoSubSection(state: HealthUiState) {
    val todayEntity = state.last14Days.firstOrNull()
    val todaySleep = todayEntity?.sleepHours ?: 0f
    val deepHours  = todayEntity?.sleepDeepHours ?: 0f
    val remHours   = todayEntity?.sleepRemHours ?: 0f
    val lightHours = (todaySleep - deepHours - remHours).coerceAtLeast(0f)

    val progress = (todaySleep / state.sleepTarget).coerceIn(0f, 1f)

    // Puntuación de Calidad del Sueño estimada (0 a 100)
    val sleepScore = remember(todaySleep, deepHours) {
        if (todaySleep <= 0f) 0
        else {
            val durationScore = (todaySleep / 8f).coerceIn(0f, 1f) * 60
            val deepScore = (deepHours / (todaySleep * 0.2f)).coerceIn(0f, 1f) * 40
            (durationScore + deepScore).toInt().coerceIn(10, 100)
        }
    }

    // Deuda de Sueño acumulada semanal
    val weeklyTotal = state.last14Days.take(7).sumOf { it.sleepHours.toDouble() }.toFloat()
    val weeklyTarget = state.sleepTarget * 7
    val sleepDebt = (weeklyTarget - weeklyTotal).coerceAtLeast(0f)

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // Hero Card de Sueño
        HealthHeroCard(
            title         = "Sueño & Descanso",
            currentVal    = "${String.format(Locale.US, "%.1f", todaySleep)}h",
            targetVal     = "${state.sleepTarget}h",
            unit          = "horas",
            progress      = progress,
            color         = RingSleep,
            icon          = Icons.Default.Bedtime,
            metric1Label  = "Calidad",
            metric1Value  = if (sleepScore > 0) "$sleepScore/100" else "--",
            metric2Label  = "Profundo",
            metric2Value  = "${String.format(Locale.US, "%.1f", deepHours)}h",
            metric3Label  = "Fase REM",
            metric3Value  = "${String.format(Locale.US, "%.1f", remHours)}h"
        )

        // Desglose Visual de Fases de Sueño
        if (todaySleep > 0f) {
            SleepStagesCard(
                totalHours = todaySleep,
                deepHours  = deepHours,
                remHours   = remHours,
                lightHours = lightHours
            )
        }

        // Gráfico Semanal de Horas Dormidas
        WeeklyBarChart(
            title     = "Horas Dormidas (Últimos 7 Días)",
            data      = state.last14Days.take(7).reversed(),
            getValue  = { it.sleepHours },
            target    = state.sleepTarget,
            barColor  = RingSleep,
            formatVal = { "${String.format(Locale.US, "%.1f", it)}h" }
        )

        // Balance de Sueño Semanal (Diseño perfectamente balanceado)
        ConsistencyCard(
            title     = "Balance de Sueño Semanal",
            subtitle  = if (sleepDebt <= 0.5f) "¡Sin deuda de sueño! Estás descansando el tiempo óptimo para recuperarte por completo."
                        else "Déficit acumulado: ${String.format(Locale.US, "%.1f", sleepDebt)}h esta semana. Intenta descansar 30 min más hoy.",
            color     = if (sleepDebt <= 1f) Emerald400 else Rose400,
            badgeText = if (sleepDebt <= 0.5f) "Óptimo" else "-${String.format(Locale.US, "%.1f", sleepDebt)}h deuda",
            icon      = Icons.Default.Nightlight
        )

        // Historial Detallado
        HistoryListCard(
            title     = "Historial de Noches",
            data      = state.last14Days,
            getValue  = { it.sleepHours },
            target    = state.sleepTarget,
            color     = RingSleep,
            formatVal = { "${String.format(Locale.US, "%.1f", it)} horas" },
            subFormat = { item -> "Profundo: ${String.format(Locale.US, "%.1f", item.sleepDeepHours)}h · REM: ${String.format(Locale.US, "%.1f", item.sleepRemHours)}h" }
        )
    }
}

// ─── Sub-apartado Calorías ────────────────────────────────────────────────────

@Composable
private fun CaloriasSubSection(state: HealthUiState) {
    val todayCal = state.last14Days.firstOrNull()?.activeCalories?.toInt() ?: 0
    val progress = (todayCal.toFloat() / state.caloriesTarget).coerceIn(0f, 1f)
    val bmr = 1650
    val totalExpenditure = bmr + todayCal
    val avgCal = if (state.last14Days.isNotEmpty()) state.last14Days.map { it.activeCalories }.average().toInt() else 0

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // Hero Card de Calorías
        HealthHeroCard(
            title         = "Calorías Activas",
            currentVal    = "$todayCal",
            targetVal     = "${state.caloriesTarget}",
            unit          = "kcal",
            progress      = progress,
            color         = Amber400,
            icon          = Icons.Default.LocalFireDepartment,
            metric1Label  = "Gasto Total",
            metric1Value  = "$totalExpenditure kcal",
            metric2Label  = "Basal (BMR)",
            metric2Value  = "$bmr kcal",
            metric3Label  = "Promedio 7d",
            metric3Value  = "$avgCal kcal"
        )

        // Gráfico Semanal de Calorías
        WeeklyBarChart(
            title     = "Calorías Quemadas (Últimos 7 Días)",
            data      = state.last14Days.take(7).reversed(),
            getValue  = { it.activeCalories },
            target    = state.caloriesTarget.toFloat(),
            barColor  = Amber400,
            formatVal = { "${it.toInt()}" }
        )

        // Intensidad y Equivalencias
        ConsistencyCard(
            title     = "Intensidad del Gasto Calórico",
            subtitle  = when {
                todayCal >= state.caloriesTarget -> "¡Nivel de actividad alto! Has alcanzado tu gasto calórico objetivo para hoy."
                todayCal >= (state.caloriesTarget * 0.6f) -> "Actividad moderada. Una caminata o sesión ligera completará tu objetivo diario."
                else -> "Día de baja actividad o recuperación. Mantén una buena hidratación."
            },
            color     = Amber400,
            badgeText = if (todayCal >= state.caloriesTarget) "Cumplido" else "En Progreso",
            icon      = Icons.Default.Bolt
        )

        // Historial Detallado
        HistoryListCard(
            title     = "Historial de Gasto Energético",
            data      = state.last14Days,
            getValue  = { it.activeCalories },
            target    = state.caloriesTarget.toFloat(),
            color     = Amber400,
            formatVal = { "${it.toInt()} kcal" },
            subFormat = { item -> "Total con basal: ~${(item.activeCalories + bmr).toInt()} kcal" }
        )
    }
}

// ─── Sub-apartado Ritmo Cardíaco ──────────────────────────────────────────────

@Composable
private fun CorazonSubSection(state: HealthUiState) {
    val todayEntity = state.last14Days.firstOrNull()
    val avgBpm = todayEntity?.avgHeartRate ?: 0
    val minBpm = todayEntity?.minHeartRate ?: 0
    val maxBpm = todayEntity?.maxHeartRate ?: 0

    // Si aún no hay BPM registrado hoy, intentamos obtener el último promedio no cero de los 14 días
    val displayAvgBpm = if (avgBpm > 0) avgBpm else state.last14Days.firstOrNull { it.avgHeartRate > 0 }?.avgHeartRate ?: 0
    val displayMinBpm = if (minBpm > 0) minBpm else state.last14Days.firstOrNull { it.minHeartRate > 0 }?.minHeartRate ?: 0
    val displayMaxBpm = if (maxBpm > 0) maxBpm else state.last14Days.firstOrNull { it.maxHeartRate > 0 }?.maxHeartRate ?: 0

    val heartStateText = when {
        displayAvgBpm == 0 -> "Sin registros"
        displayAvgBpm < 60 -> "Reposo Atlético"
        displayAvgBpm <= 80 -> "Ritmo Óptimo"
        displayAvgBpm <= 100 -> "Normal"
        else -> "Elevado"
    }

    val progress = if (displayAvgBpm > 0) (displayAvgBpm / 140f).coerceIn(0.1f, 1f) else 0f

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // Hero Card de Ritmo Cardíaco
        HealthHeroCard(
            title         = "Frecuencia Cardíaca",
            currentVal    = if (displayAvgBpm > 0) "$displayAvgBpm" else "--",
            targetVal     = "60-100",
            unit          = "bpm promedio",
            progress      = progress,
            color         = Rose400,
            icon          = Icons.Default.Favorite,
            metric1Label  = "En Reposo",
            metric1Value  = if (displayMinBpm > 0) "$displayMinBpm bpm" else "--",
            metric2Label  = "Pico Máx",
            metric2Value  = if (displayMaxBpm > 0) "$displayMaxBpm bpm" else "--",
            metric3Label  = "Estado",
            metric3Value  = heartStateText
        )

        // Gráfico Semanal de Ritmo Cardíaco (Últimos 7 Días)
        WeeklyBarChart(
            title     = "Ritmo Cardíaco Promedio (Últimos 7 Días)",
            data      = state.last14Days.take(7).reversed(),
            getValue  = { it.avgHeartRate.toFloat() },
            target    = 75f,
            barColor  = Rose400,
            formatVal = { if (it > 0f) "${it.toInt()}" else "-" }
        )

        // Zonas Cardiovasculares
        HeartRateZonesCard(currentBpm = displayAvgBpm)

        // Card de Salud y Recuperación
        ConsistencyCard(
            title     = "Salud Cardiovascular",
            subtitle  = when {
                displayAvgBpm in 50..75 -> "Tu frecuencia cardíaca en reposo refleja un excelente tono vagal y recuperación física."
                displayAvgBpm in 76..95 -> "Ritmo cardíaco dentro del rango saludable estándar. Una caminata o cardio moderado mejora tu eficiencia cardíaca."
                displayAvgBpm > 95      -> "Ritmo elevado. Asegúrate de descansar bien, reducir el estrés y mantenerte bien hidratado."
                else                    -> "Sincroniza tu pulsera o reloj inteligente con Health Connect para registrar tu ritmo cardíaco en tiempo real."
            },
            color     = Rose400,
            badgeText = if (displayAvgBpm > 0) "$displayAvgBpm BPM" else "Health Connect",
            icon      = Icons.Default.Favorite
        )

        // Historial Detallado de Ritmo Cardíaco
        HistoryListCard(
            title     = "Historial de Frecuencia Cardíaca",
            data      = state.last14Days,
            getValue  = { it.avgHeartRate.toFloat() },
            target    = 75f,
            color     = Rose400,
            formatVal = { if (it > 0f) "${it.toInt()} bpm" else "Sin datos" },
            subFormat = { item ->
                if (item.minHeartRate > 0 || item.maxHeartRate > 0) {
                    "Mín: ${item.minHeartRate} · Máx: ${item.maxHeartRate} bpm"
                } else {
                    "Health Connect"
                }
            }
        )
    }
}

@Composable
private fun HeartRateZonesCard(currentBpm: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Indigo800)
            .border(1.dp, Indigo700, RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector        = Icons.Default.Speed,
                        contentDescription = null,
                        tint               = Rose400,
                        modifier           = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text       = "Zonas de Frecuencia Cardíaca",
                        style      = MaterialTheme.typography.titleSmall,
                        color      = White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text  = "Referencia",
                    style = MaterialTheme.typography.labelSmall,
                    color = Gray400
                )
            }

            HorizontalDivider(color = Indigo700, thickness = 1.dp)

            ZoneItem("Zona 1 · Reposo / Recuperación", "< 100 bpm", "Recuperación activa y descanso", Cyan400, currentBpm in 1..99)
            ZoneItem("Zona 2 · Quema de Grasa", "100 - 130 bpm", "Resistencia aeróbica base", Emerald400, currentBpm in 100..130)
            ZoneItem("Zona 3 · Cardio Aeróbico", "131 - 160 bpm", "Capacidad cardiovascular y fondo", Amber400, currentBpm in 131..160)
            ZoneItem("Zona 4 · Pico / Anaeróbico", "> 160 bpm", "Potencia máxima y sprint", Rose400, currentBpm > 160)
        }
    }
}

@Composable
private fun ZoneItem(
    title: String,
    range: String,
    desc: String,
    color: Color,
    isActive: Boolean
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) color.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text       = title,
                    style      = MaterialTheme.typography.labelMedium,
                    color      = if (isActive) White else Gray300,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold
                )
                Text(
                    text     = desc,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = Gray500,
                    fontSize = 11.sp
                )
            }
        }
        Text(
            text       = range,
            style      = MaterialTheme.typography.labelSmall,
            color      = color,
            fontWeight = FontWeight.Bold
        )
    }
}

// ─── Componente: Hero Card de Salud ───────────────────────────────────────────

@Composable
private fun HealthHeroCard(
    title: String,
    currentVal: String,
    targetVal: String,
    unit: String,
    progress: Float,
    color: Color,
    icon: ImageVector,
    metric1Label: String,
    metric1Value: String,
    metric2Label: String,
    metric2Value: String,
    metric3Label: String,
    metric3Value: String
) {
    val animatedProgress by animateFloatAsState(
        targetValue   = progress,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label         = "hero_health_progress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = 0.16f),
                        Indigo800
                    ),
                    radius = 800f
                )
            )
            .border(1.dp, Indigo700, RoundedCornerShape(26.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Header del Hero
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(color.copy(alpha = 0.2f))
                    ) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text       = title,
                        style      = MaterialTheme.typography.titleMedium,
                        color      = White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(color.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text       = "${(progress * 100).toInt()}%",
                        style      = MaterialTheme.typography.labelSmall,
                        color      = color,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Gran Valor Principal
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text       = currentVal,
                    style      = MaterialTheme.typography.headlineLarge,
                    color      = White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 34.sp
                )
                Text(
                    text     = " / $targetVal $unit",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = Gray400,
                    modifier = Modifier.padding(bottom = 6.dp, start = 6.dp)
                )
            }

            // Barra de Progreso Neón
            LinearProgressIndicator(
                progress   = { animatedProgress },
                modifier   = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color      = color,
                trackColor = RingTrack
            )

            // Sub-métricas perfectamente equilibradas en 3 columnas
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                SubMetricPill(label = metric1Label, value = metric1Value, modifier = Modifier.weight(1f))
                SubMetricPill(label = metric2Label, value = metric2Value, modifier = Modifier.weight(1f))
                SubMetricPill(label = metric3Label, value = metric3Value, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SubMetricPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = modifier
    ) {
        Text(
            text       = value,
            style      = MaterialTheme.typography.titleSmall,
            color      = White,
            fontWeight = FontWeight.Bold,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text     = label,
            style    = MaterialTheme.typography.labelSmall,
            color    = Gray400,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

// ─── Componente: Gráfico de Barras Semanal ────────────────────────────────────

@Composable
private fun WeeklyBarChart(
    title: String,
    data: List<HealthDataEntity>,
    getValue: (HealthDataEntity) -> Float,
    target: Float,
    barColor: Color,
    formatVal: (Float) -> String
) {
    val zone = ZoneId.systemDefault()
    val dayFormatter = DateTimeFormatter.ofPattern("E", Locale("es"))
    val today = LocalDate.now()

    val maxVal = remember(data, target) {
        val highest = data.maxOfOrNull { getValue(it) } ?: target
        maxOf(highest, target * 1.15f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Indigo800)
            .border(1.dp, Indigo700, RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Text(
                    text       = title,
                    style      = MaterialTheme.typography.titleSmall,
                    color      = White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text  = "Meta: ${formatVal(target)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Gray400
                )
            }

            if (data.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.Bottom,
                    modifier              = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .padding(top = 10.dp)
                ) {
                    data.forEach { item ->
                        val date = Instant.ofEpochMilli(item.dateMillis).atZone(zone).toLocalDate()
                        val isToday = date == today
                        val value = getValue(item)
                        val fraction = (value / maxVal).coerceIn(0.04f, 1f)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier            = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Text(
                                text     = formatVal(value),
                                style    = MaterialTheme.typography.labelSmall,
                                color    = if (isToday) barColor else Gray400,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(4.dp))

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(18.dp)
                                        .fillMaxHeight(fraction)
                                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                        .background(
                                            if (isToday) Brush.verticalGradient(listOf(barColor, barColor.copy(alpha = 0.6f)))
                                            else Brush.verticalGradient(listOf(barColor.copy(alpha = 0.45f), Indigo700))
                                        )
                                        .border(
                                            width = if (isToday) 1.5.dp else 0.dp,
                                            color = if (isToday) barColor else Color.Transparent,
                                            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                        )
                                )
                            }
                            Spacer(Modifier.height(6.dp))

                            Text(
                                text       = date.format(dayFormatter).uppercase().take(2),
                                style      = MaterialTheme.typography.labelSmall,
                                color      = if (isToday) White else Gray500,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                fontSize   = 11.sp,
                                maxLines   = 1
                            )
                        }
                    }
                }
            } else {
                Text("Sin registros suficientes para graficar", color = Gray400, fontSize = 12.sp)
            }
        }
    }
}

// ─── Componente: Fases de Sueño ───────────────────────────────────────────────

@Composable
private fun SleepStagesCard(
    totalHours: Float,
    deepHours: Float,
    remHours: Float,
    lightHours: Float
) {
    val deepPct = ((deepHours / totalHours) * 100).toInt()
    val remPct  = ((remHours / totalHours) * 100).toInt()
    val lightPct = (100 - deepPct - remPct).coerceAtLeast(0)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Indigo800)
            .border(1.dp, Indigo700, RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text       = "Arquitectura de Fases de Sueño",
                style      = MaterialTheme.typography.titleSmall,
                color      = White,
                fontWeight = FontWeight.Bold
            )

            // Barra segmentada de fases
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
            ) {
                if (deepHours > 0) {
                    Box(
                        modifier = Modifier
                            .weight(deepHours.coerceAtLeast(0.1f))
                            .fillMaxHeight()
                            .background(Violet400)
                    )
                }
                if (remHours > 0) {
                    Box(
                        modifier = Modifier
                            .weight(remHours.coerceAtLeast(0.1f))
                            .fillMaxHeight()
                            .background(RingSleep)
                    )
                }
                if (lightHours > 0) {
                    Box(
                        modifier = Modifier
                            .weight(lightHours.coerceAtLeast(0.1f))
                            .fillMaxHeight()
                            .background(Indigo600)
                    )
                }
            }

            // Leyendas equilibradas en 3 columnas
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                StageLegend(label = "Profundo", hours = deepHours, pct = deepPct, color = Violet400, modifier = Modifier.weight(1f))
                StageLegend(label = "Fase REM", hours = remHours, pct = remPct, color = RingSleep, modifier = Modifier.weight(1f))
                StageLegend(label = "Ligero", hours = lightHours, pct = lightPct, color = Indigo600, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StageLegend(
    label: String,
    hours: Float,
    pct: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = Gray400, fontSize = 11.sp)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text       = "${String.format(Locale.US, "%.1f", hours)}h ($pct%)",
            style      = MaterialTheme.typography.labelMedium,
            color      = White,
            fontWeight = FontWeight.Bold
        )
    }
}

// ─── Componente: Consistencia & Racha (Sin estiramientos) ─────────────────────

@Composable
private fun ConsistencyCard(
    title: String,
    subtitle: String,
    color: Color,
    badgeText: String,
    icon: ImageVector
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Indigo800)
            .border(1.dp, Indigo700, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(color.copy(alpha = 0.18f))
                    ) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text       = title,
                        style      = MaterialTheme.typography.titleSmall,
                        color      = White,
                        fontWeight = FontWeight.Bold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(color.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text       = badgeText,
                        style      = MaterialTheme.typography.labelSmall,
                        color      = color,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 11.sp,
                        maxLines   = 1
                    )
                }
            }

            Text(
                text       = subtitle,
                style      = MaterialTheme.typography.bodySmall,
                color      = Gray300,
                lineHeight = 18.sp
            )
        }
    }
}

// ─── Componente: Lista Detallada de Historial ─────────────────────────────────

@Composable
private fun HistoryListCard(
    title: String,
    data: List<HealthDataEntity>,
    getValue: (HealthDataEntity) -> Float,
    target: Float,
    color: Color,
    formatVal: (Float) -> String,
    subFormat: (HealthDataEntity) -> String
) {
    val zone = ZoneId.systemDefault()
    val dayFmt = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es"))
    val today = LocalDate.now()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Indigo800)
            .border(1.dp, Indigo700, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = White, fontWeight = FontWeight.Bold)

        if (data.isNotEmpty()) {
            data.take(10).forEach { item ->
                val date = Instant.ofEpochMilli(item.dateMillis).atZone(zone).toLocalDate()
                val isToday = date == today
                val dateStr = if (isToday) "Hoy" else date.format(dayFmt).replaceFirstChar { it.uppercase() }
                val valNum = getValue(item)
                val isGoalMet = valNum >= target

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Indigo700)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = dateStr,
                            style      = MaterialTheme.typography.labelMedium,
                            color      = if (isToday) Violet300 else White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text     = subFormat(item),
                            style    = MaterialTheme.typography.bodySmall,
                            color    = Gray400,
                            fontSize = 11.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text       = formatVal(valNum),
                                style      = MaterialTheme.typography.titleSmall,
                                color      = if (isGoalMet) color else White,
                                fontWeight = FontWeight.Bold
                            )
                            if (isGoalMet) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Default.CheckCircle, null, tint = Emerald400, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        } else {
            Text("Sin historial registrado aún", style = MaterialTheme.typography.bodySmall, color = Gray400)
        }
    }
}
