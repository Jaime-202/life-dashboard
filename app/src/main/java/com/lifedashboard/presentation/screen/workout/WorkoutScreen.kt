package com.lifedashboard.presentation.screen.workout

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lifedashboard.core.util.DateUtils.toStartOfDayMillis
import com.lifedashboard.data.local.dao.WorkoutDao
import com.lifedashboard.data.local.entity.ExerciseSetEntity
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
    val isLoading: Boolean = true
)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutDao: WorkoutDao
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
            // Agrupar sesiones por día de calendario único para evitar conteos duplicados
            val uniqueDays = weekSessionsList.groupBy {
                Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate()
            }

            // Cada día cuenta como una sesión/día de entrenamiento completado
            val trainedDaysCount = uniqueDays.size

            // Sumar la duración real por cada día único (evitando duplicaciones de series)
            val totalMins = uniqueDays.values.sumOf { daySessions ->
                val maxDuration = daySessions.maxOfOrNull { it.durationMinutes } ?: 0
                if (maxDuration > 0) maxDuration else 45 // 45 min por defecto para sesiones de fuerza
            }

            // Desduplicar el historial reciente por día para mostrar una tarjeta por fecha
            val deduplicatedRecent = allRecent
                .groupBy { Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate() }
                .map { (_, list) -> list.first() }
                .sortedByDescending { it.dateMillis }

            WorkoutUiState(
                recentSessions            = deduplicatedRecent,
                weekSessions              = weekSessionsList,
                uniqueTrainedDaysThisWeek = trainedDaysCount,
                totalMinutesThisWeek      = totalMins,
                isLoading                 = false
            )
        }
        .flowOn(Dispatchers.Default)
        .onEach { newState ->
            _state.value = newState
        }
        .launchIn(viewModelScope)
    }

    fun addManualWorkout(
        durationMinutes: Int,
        exerciseName: String,
        reps: Int,
        weightKg: Float
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val todayMillis = LocalDate.now().toStartOfDayMillis()

            val sessionId = workoutDao.upsertSession(
                WorkoutSessionEntity(
                    dateMillis      = todayMillis,
                    type            = "STRENGTH",
                    durationMinutes = if (durationMinutes > 0) durationMinutes else 45,
                    sourceFile      = "manual"
                )
            )

            if (exerciseName.isNotBlank() && reps > 0) {
                workoutDao.upsertSets(
                    listOf(
                        ExerciseSetEntity(
                            sessionId    = sessionId,
                            exerciseName = exerciseName.trim(),
                            setNumber    = 1,
                            reps         = reps,
                            weightKg     = weightKg,
                            volumeKg     = weightKg * reps
                        )
                    )
                )
            }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun WorkoutScreen(viewModel: WorkoutViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GradientStart, GradientEnd)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 56.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Encabezado
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
                Text(
                    text  = "Registro y evolución física",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray400
                )
            }

            Button(
                onClick = { showAddDialog = true },
                colors  = ButtonDefaults.buttonColors(containerColor = Violet400, contentColor = Indigo900),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                shape   = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector        = Icons.Default.Add,
                        contentDescription = "Añadir entrenamiento",
                        modifier           = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Añadir", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // ─── Tarjeta Resumen Semanal ───────────────────────────────────────
        WeeklySummaryCard(
            trainedDays  = state.uniqueTrainedDaysThisWeek,
            totalMinutes = state.totalMinutesThisWeek
        )

        // ─── Días de la Semana (Diseño de Pastillas) ───────────────────────
        Text(
            text       = "Días de Actividad Semanal",
            style      = MaterialTheme.typography.titleMedium,
            color      = White,
            fontWeight = FontWeight.Bold
        )
        NewWeekDayIndicator(weekSessions = state.weekSessions)

        // ─── Línea de Tiempo de Entrenamientos Recientes ───────────────────
        Text(
            text       = "Historial de Sesiones",
            style      = MaterialTheme.typography.titleMedium,
            color      = White,
            fontWeight = FontWeight.Bold
        )

        if (state.recentSessions.isNotEmpty()) {
            RecentWorkoutsTimeline(sessions = state.recentSessions)
        } else if (!state.isLoading) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Indigo800),
                shape  = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier            = Modifier.padding(32.dp).fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Indigo700)
                    ) {
                        Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = Violet300, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Sin entrenamientos registrados", color = White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("Toca en '+ Añadir' o sincroniza tus datos", color = Gray400, fontSize = 12.sp)
                }
            }
        }
    }

    if (showAddDialog) {
        AddWorkoutDialog(
            onDismiss = { showAddDialog = false },
            onSave    = { duration, name, reps, weight ->
                viewModel.addManualWorkout(duration, name, reps, weight)
                showAddDialog = false
            }
        )
    }
}

// ─── Componente: Resumen Semanal Rediseñado ───────────────────────────────────

@Composable
private fun WeeklySummaryCard(trainedDays: Int, totalMinutes: Int) {
    val targetDays = 4 // Meta semanal habitual: 4 días
    val targetMinutes = 180 // 3 horas semanales
    val progress = (totalMinutes.toFloat() / targetMinutes).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(Indigo800, Color(0xFF231450))
                )
            )
            .border(1.dp, Indigo700, RoundedCornerShape(24.dp))
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
                            text  = "Meta de consistencia: $targetDays días",
                            style = MaterialTheme.typography.bodySmall,
                            color = Gray400
                        )
                    }
                }

                // Badge de días
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

            // Datos grandes
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text       = "$totalMinutes",
                    style      = MaterialTheme.typography.headlineLarge,
                    color      = White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text     = " min / $targetMinutes min objetivo",
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
                    text  = if (totalMinutes >= targetMinutes) "¡Meta semanal superada! 🎉" 
                            else "Faltan ${targetMinutes - totalMinutes} min para el objetivo",
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

// ─── Componente: Indicador de Días ───────────────────────────────────────────

@Composable
private fun NewWeekDayIndicator(weekSessions: List<WorkoutSessionEntity>) {
    val days = listOf("L", "M", "X", "J", "V", "S", "D")
    val zone = ZoneId.systemDefault()
    val todayIdx = LocalDate.now().dayOfWeek.value - 1 // 0 (Lunes) a 6 (Domingo)

    // Agrupar sesiones por día de la semana (0 a 6)
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

// ─── Componente: Timeline de Historial Reciente ───────────────────────────────

private fun workoutTypeName(type: String): String = when (type) {
    "STRENGTH" -> "Entrenamiento de Fuerza"
    "CARDIO"   -> "Entrenamiento de Cardio"
    "REST"     -> "Día de Descanso"
    else       -> "Entrenamiento General"
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
                                .height(42.dp)
                                .background(Indigo600)
                        )
                    }
                }

                // Contenido de la sesión
                Column(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
                    Text(
                        text       = typeTitle,
                        style      = MaterialTheme.typography.titleSmall,
                        color      = White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = Gray500, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text  = "$dateStr · ${if (session.durationMinutes > 0) "${session.durationMinutes} min" else "45 min"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Gray400
                        )
                    }
                }
            }
        }
    }
}

// ─── Modal Diálogo Añadir Entrenamiento Manual ───────────────────────────────

@Composable
private fun AddWorkoutDialog(
    onDismiss: () -> Unit,
    onSave: (Int, String, Int, Float) -> Unit
) {
    var exerciseName by remember { mutableStateOf("") }
    var repsText by remember { mutableStateOf("10") }
    var weightText by remember { mutableStateOf("60") }
    var durationText by remember { mutableStateOf("45") }
    var durationError by remember { mutableStateOf(false) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = Violet400,
        unfocusedBorderColor = Indigo600,
        focusedTextColor     = White,
        unfocusedTextColor   = White,
        focusedLabelColor    = Violet300,
        unfocusedLabelColor  = Gray400,
        errorBorderColor     = MaterialTheme.colorScheme.error,
        errorLabelColor      = MaterialTheme.colorScheme.error
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        shape            = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = Violet300, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Registrar Entrenamiento", color = White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier            = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text  = "Añade la duración y si lo deseas el ejercicio principal que realizaste hoy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray300
                )

                OutlinedTextField(
                    value           = durationText,
                    onValueChange   = {
                        durationText = it
                        durationError = (it.toIntOrNull() ?: 0) <= 0
                    },
                    label           = { Text("Duración Total (minutos)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine      = true,
                    modifier        = Modifier.fillMaxWidth(),
                    isError         = durationError,
                    supportingText  = if (durationError) {
                        { Text("La duración debe ser mayor que 0", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    colors = textFieldColors
                )

                OutlinedTextField(
                    value         = exerciseName,
                    onValueChange = { exerciseName = it },
                    label         = { Text("Ejercicio Principal (Opcional)") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = textFieldColors
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value           = repsText,
                        onValueChange   = { repsText = it },
                        label           = { Text("Reps") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier        = Modifier.weight(1f),
                        colors          = textFieldColors
                    )
                    OutlinedTextField(
                        value           = weightText,
                        onValueChange   = { weightText = it },
                        label           = { Text("Peso (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier        = Modifier.weight(1f),
                        colors          = textFieldColors
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val duration = durationText.toIntOrNull() ?: 0
                    if (duration <= 0) {
                        durationError = true
                        return@Button
                    }
                    val reps = repsText.toIntOrNull() ?: 0
                    val weight = weightText.toFloatOrNull() ?: 0f
                    onSave(duration, exerciseName.trim(), reps, weight)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Violet400, contentColor = Indigo900),
                shape  = RoundedCornerShape(12.dp)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Gray400) }
        }
    )
}
