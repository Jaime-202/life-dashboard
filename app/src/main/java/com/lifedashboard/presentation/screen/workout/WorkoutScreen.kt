package com.lifedashboard.presentation.screen.workout

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lifedashboard.core.worker.WorkManagerScheduler
import com.lifedashboard.data.local.dao.WorkoutDao
import com.lifedashboard.data.local.entity.WorkoutSessionEntity
import com.lifedashboard.presentation.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.Locale
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class WorkoutUiState(
    val recentSessions: List<WorkoutSessionEntity> = emptyList(),
    val weekSessions: List<WorkoutSessionEntity> = emptyList(),
    val uniqueTrainedDaysThisWeek: Int = 0,
    val totalMinutesThisWeek: Int = 0,
    val strengthCount: Int = 0,
    val cardioCount: Int = 0,
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false
)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val scheduler: WorkManagerScheduler
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutUiState())
    val state: StateFlow<WorkoutUiState> = _state.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val weekStart = com.lifedashboard.core.util.DateUtils.weekStartMillis()
        val nextWeek = weekStart + 7 * 24 * 60 * 60 * 1000L
        val zone = ZoneId.systemDefault()

        combine(
            workoutDao.getSessionsInRange(weekStart, nextWeek),
            workoutDao.observeRecentSessions(30)
        ) { weekSessionsList, allRecent ->
            val uniqueDays = weekSessionsList.groupBy {
                Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate()
            }

            val trainedDaysCount = uniqueDays.size

            val totalMins = uniqueDays.values.sumOf { daySessions ->
                val maxDuration = daySessions.maxOfOrNull { it.durationMinutes } ?: 0
                if (maxDuration > 0) maxDuration else 45
            }

            val deduplicatedRecent = allRecent
                .groupBy { Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate() }
                .map { (_, list) -> list.first() }
                .sortedByDescending { it.dateMillis }

            val strength = weekSessionsList.count { it.type == "STRENGTH" }
            val cardio = weekSessionsList.count { it.type == "CARDIO" }

            WorkoutUiState(
                recentSessions            = deduplicatedRecent,
                weekSessions              = weekSessionsList,
                uniqueTrainedDaysThisWeek = trainedDaysCount,
                totalMinutesThisWeek      = totalMins,
                strengthCount             = strength,
                cardioCount               = cardio,
                isLoading                 = false,
                isSyncing                 = false
            )
        }
        .flowOn(Dispatchers.Default)
        .onEach { newState ->
            _state.value = newState
        }
        .launchIn(viewModelScope)
    }

    fun triggerSync() {
        viewModelScope.launch {
            _state.update { it.copy(isSyncing = true) }
            scheduler.runHealthSyncNow()
            kotlinx.coroutines.delay(1200)
            _state.update { it.copy(isSyncing = false) }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun WorkoutScreen(viewModel: WorkoutViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GradientStart, GradientEnd)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 56.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Encabezado con acción de sincronización directa
        WorkoutHeader(
            isSyncing = state.isSyncing,
            onSyncClick = { viewModel.triggerSync() }
        )

        // ─── Tarjeta de Volumen & Racha Semanal ─────────────────────────────
        WeeklySummaryCard(
            trainedDays  = state.uniqueTrainedDaysThisWeek,
            totalMinutes = state.totalMinutesThisWeek
        )

        // ─── Calendario Semanal de Consistencia (L a D) ───────────────────
        Text(
            text       = "Días de Actividad Semanal",
            style      = MaterialTheme.typography.titleMedium,
            color      = White,
            fontWeight = FontWeight.Bold
        )
        NewWeekDayIndicator(weekSessions = state.weekSessions)

        // ─── Desglose de Modalidad de Entrenamiento ────────────────────────
        if (state.totalMinutesThisWeek > 0 || state.weekSessions.isNotEmpty()) {
            TrainingDistributionCard(
                strengthSessions = state.strengthCount,
                cardioSessions   = state.cardioCount,
                totalMinutes     = state.totalMinutesThisWeek
            )
        }

        // ─── Historial de Sesiones Registradas vía Health Connect ─────────
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier              = Modifier.fillMaxWidth()
        ) {
            Text(
                text       = "Historial de Sesiones",
                style      = MaterialTheme.typography.titleMedium,
                color      = White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text  = "Health Connect",
                style = MaterialTheme.typography.labelSmall,
                color = Emerald400,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (state.recentSessions.isNotEmpty()) {
            RecentWorkoutsTimeline(sessions = state.recentSessions)
        } else if (!state.isLoading) {
            EmptyWorkoutCard(
                isSyncing = state.isSyncing,
                onSyncClick = { viewModel.triggerSync() }
            )
        }
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun WorkoutHeader(
    isSyncing: Boolean,
    onSyncClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sync_rotation_anim"
    )

    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier.fillMaxWidth()
    ) {
        Column {
            Text(
                text       = "Entrenamiento",
                style      = MaterialTheme.typography.headlineSmall,
                color      = White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Emerald400)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text  = "Sincronizado vía Health Connect",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray400
                )
            }
        }

        FilledTonalButton(
            onClick = onSyncClick,
            enabled = !isSyncing,
            colors  = ButtonDefaults.filledTonalButtonColors(
                containerColor = Indigo800,
                contentColor   = Violet300
            ),
            border         = BorderStroke(1.dp, Indigo700),
            shape          = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Default.Sync,
                    contentDescription = "Sincronizar entrenamientos",
                    modifier           = Modifier
                        .size(17.dp)
                        .then(if (isSyncing) Modifier.rotate(rotation) else Modifier)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text       = if (isSyncing) "Sincronizando..." else "Sincronizar",
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─── Componente: Resumen Semanal ──────────────────────────────────────────────

@Composable
private fun WeeklySummaryCard(trainedDays: Int, totalMinutes: Int) {
    val targetDays = 4
    val targetMinutes = 180
    val progress = (totalMinutes.toFloat() / targetMinutes).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(Indigo800, Color(0xFF22134F))
                )
            )
            .border(1.dp, Indigo700, RoundedCornerShape(26.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Violet400.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector        = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint               = Violet300,
                            modifier           = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text       = "Volumen Semanal",
                            style      = MaterialTheme.typography.titleMedium,
                            color      = White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text  = "Meta de consistencia: $targetDays sesiones/sem",
                            style = MaterialTheme.typography.bodySmall,
                            color = Gray400
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (trainedDays >= targetDays) Emerald400.copy(alpha = 0.2f) else Indigo700)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text       = "$trainedDays / $targetDays días",
                        style      = MaterialTheme.typography.labelSmall,
                        color      = if (trainedDays >= targetDays) Emerald400 else Violet300,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Métrica Principal
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text       = "$totalMinutes",
                    style      = MaterialTheme.typography.headlineLarge,
                    color      = White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text     = " min entrenados / $targetMinutes min objetivo",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = Gray400,
                    modifier = Modifier.padding(bottom = 6.dp, start = 6.dp)
                )
            }

            LinearProgressIndicator(
                progress   = { progress },
                modifier   = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color      = Violet400,
                trackColor = RingTrack
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Text(
                    text  = if (totalMinutes >= targetMinutes) "¡Meta semanal superada con éxito! 🎉"
                            else "Faltan ${targetMinutes - totalMinutes} min para tu meta",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (totalMinutes >= targetMinutes) Emerald400 else Violet300,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text  = "$trainedDays ${if (trainedDays == 1) "sesión" else "sesiones"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Gray400
                )
            }
        }
    }
}

// ─── Componente: Indicador de Días de la Semana ───────────────────────────────

@Composable
private fun NewWeekDayIndicator(weekSessions: List<WorkoutSessionEntity>) {
    val days = listOf("L", "M", "X", "J", "V", "S", "D")
    val zone = ZoneId.systemDefault()
    val todayIdx = LocalDate.now().dayOfWeek.value - 1

    val workedOutDays = remember(weekSessions) {
        weekSessions.map {
            Instant.ofEpochMilli(it.dateMillis).atZone(zone).get(ChronoField.DAY_OF_WEEK) - 1
        }.toSet()
    }

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier.fillMaxWidth()
    ) {
        days.forEachIndexed { idx, dayLabel ->
            val hasWorkout = workedOutDays.contains(idx)
            val isToday = idx == todayIdx

            val bgColor = if (hasWorkout) Violet400 else if (isToday) Indigo700 else Color.Transparent
            val textColor = if (hasWorkout) Indigo900 else if (isToday) White else Gray500
            val borderColor = if (isToday && !hasWorkout) Violet400 else if (!hasWorkout) Indigo700 else Color.Transparent

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(42.dp)
                    .height(58.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(bgColor)
                    .border(1.5.dp, borderColor, RoundedCornerShape(22.dp))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = dayLabel,
                        style      = MaterialTheme.typography.labelMedium,
                        color      = textColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(5.dp))
                    if (hasWorkout) {
                        Icon(
                            imageVector        = Icons.Default.Check,
                            contentDescription = "Entrenado",
                            tint               = Indigo900,
                            modifier           = Modifier.size(15.dp)
                        )
                    } else {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isToday) Violet400 else Gray600)
                        )
                    }
                }
            }
        }
    }
}

// ─── Componente: Distribución de Entrenamiento ────────────────────────────────

@Composable
private fun TrainingDistributionCard(
    strengthSessions: Int,
    cardioSessions: Int,
    totalMinutes: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Indigo800)
            .border(1.dp, Indigo700, RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text       = "Distribución Semanal",
                style      = MaterialTheme.typography.labelLarge,
                color      = White,
                fontWeight = FontWeight.Bold
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Indigo700)
                        .padding(14.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FitnessCenter, null, tint = Violet300, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Fuerza", style = MaterialTheme.typography.labelSmall, color = Gray300)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text       = "$strengthSessions ${if (strengthSessions == 1) "sesión" else "sesiones"}",
                            style      = MaterialTheme.typography.titleMedium,
                            color      = White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Indigo700)
                        .padding(14.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DirectionsRun, null, tint = Cyan400, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Cardio", style = MaterialTheme.typography.labelSmall, color = Gray300)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text       = "$cardioSessions ${if (cardioSessions == 1) "sesión" else "sesiones"}",
                            style      = MaterialTheme.typography.titleMedium,
                            color      = White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ─── Componente: Timeline de Sesiones Recientes ───────────────────────────────

private fun workoutTypeName(type: String): String = when (type) {
    "STRENGTH" -> "Entrenamiento de Fuerza"
    "CARDIO"   -> "Entrenamiento de Cardio"
    "REST"     -> "Día de Descanso"
    else       -> "Sesión de Ejercicio"
}

@Composable
private fun RecentWorkoutsTimeline(sessions: List<WorkoutSessionEntity>) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es"))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Indigo800)
            .border(1.dp, Indigo700, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        sessions.take(6).forEachIndexed { index, session ->
            val dt = Instant.ofEpochMilli(session.dateMillis).atZone(zone).toLocalDate()
            val dateStr = when {
                dt == today -> "Hoy"
                dt == today.minusDays(1) -> "Ayer"
                else -> dt.format(formatter).replaceFirstChar { it.uppercase() }
            }

            val typeTitle = workoutTypeName(session.type)
            val isStrength = session.type == "STRENGTH"

            Row(modifier = Modifier.fillMaxWidth()) {
                // Eje visual del timeline
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier            = Modifier.padding(end = 16.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Violet400.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector        = if (isStrength) Icons.Default.FitnessCenter else Icons.Default.DirectionsRun,
                            contentDescription = null,
                            tint               = Violet300,
                            modifier           = Modifier.size(19.dp)
                        )
                    }
                    if (index < sessions.take(6).size - 1) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(44.dp)
                                .background(Indigo600)
                        )
                    }
                }

                // Contenido de la sesión
                Column(modifier = Modifier.weight(1f).padding(top = 2.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                        modifier              = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text       = typeTitle,
                            style      = MaterialTheme.typography.titleSmall,
                            color      = White,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Indigo700)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text     = if (session.durationMinutes > 0) "${session.durationMinutes} min" else "45 min",
                                style    = MaterialTheme.typography.labelSmall,
                                color    = Violet300,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = Gray500, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text  = dateStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = Gray400
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(Gray600)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text  = "Health Connect ✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = Emerald400
                        )
                    }
                }
            }
        }
    }
}

// ─── Componente: Estado Vacío ─────────────────────────────────────────────────

@Composable
private fun EmptyWorkoutCard(
    isSyncing: Boolean,
    onSyncClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Indigo800),
        shape  = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Indigo700),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(32.dp).fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Violet400.copy(alpha = 0.15f))
            ) {
                Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = Violet300, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text       = "Sin entrenamientos recientes",
                color      = White,
                fontWeight = FontWeight.Bold,
                style      = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text       = "Tus sesiones se sincronizan automáticamente desde Health Connect y Samsung Health. Realiza un entreno con tu reloj o app de fitness para verlo reflejado.",
                color      = Gray400,
                fontSize   = 13.sp,
                textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(18.dp))
            FilledTonalButton(
                onClick = onSyncClick,
                enabled = !isSyncing,
                colors  = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Violet400,
                    contentColor   = Indigo900
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text       = if (isSyncing) "Sincronizando..." else "Sincronizar ahora",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
