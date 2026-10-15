package com.lifedashboard.presentation.screen.workout

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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.Locale
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class WorkoutUiState(
    val recentSessions: List<WorkoutSessionEntity> = emptyList(),
    val weekSessions: List<WorkoutSessionEntity> = emptyList(),
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

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val weekStart = com.lifedashboard.core.util.DateUtils.weekStartMillis()
            val nextWeek = weekStart + 7 * 24 * 60 * 60 * 1000L
            val recent = workoutDao.getRecentSessions(20)

            // Consultar las sesiones de la semana actual para el resumen y los indicadores
            workoutDao.getSessionsInRange(weekStart, nextWeek).collectLatest { weekSessionsList ->
                val totalMins = weekSessionsList.sumOf { it.durationMinutes }

                // Desduplicar sesiones para mostrar un listado limpio
                val deduplicatedRecent = recent
                    .groupBy { Instant.ofEpochMilli(it.dateMillis).atZone(ZoneId.systemDefault()).toLocalDate() }
                    .map { (_, list) -> list.first() }
                    .sortedByDescending { it.dateMillis }

                _state.update {
                    it.copy(
                        recentSessions       = deduplicatedRecent,
                        weekSessions         = weekSessionsList,
                        totalMinutesThisWeek = totalMins,
                        isLoading            = false
                    )
                }
            }
        }
    }

    fun addManualWorkout(
        durationMinutes: Int,
        exerciseName: String,
        reps: Int,
        weightKg: Float
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val todayMillis = java.time.LocalDate.now().toStartOfDayMillis()

            val sessionId = workoutDao.upsertSession(
                WorkoutSessionEntity(
                    dateMillis      = todayMillis,
                    type            = "STRENGTH",
                    durationMinutes = durationMinutes,
                    sourceFile      = "manual"
                )
            )

            if (exerciseName.isNotBlank() && reps > 0) {
                workoutDao.upsertSets(
                    listOf(
                        ExerciseSetEntity(
                            sessionId    = sessionId,
                            exerciseName = exerciseName,
                            setNumber    = 1,
                            reps         = reps,
                            weightKg     = weightKg,
                            volumeKg     = reps * weightKg
                        )
                    )
                )
            }

            loadData()
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
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier              = Modifier.fillMaxWidth()
        ) {
            Text(
                text       = "Entrenamiento",
                style      = MaterialTheme.typography.headlineSmall,
                color      = White,
                fontWeight = FontWeight.Bold
            )

            // FAB con contentDescription para accesibilidad
            Button(
                onClick = { showAddDialog = true },
                colors  = ButtonDefaults.buttonColors(containerColor = Violet400, contentColor = Indigo900),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                shape   = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Añadir entrenamiento",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Añadir", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // ─── Tarjeta Resumen Semanal ───────────────────────────────────────
        WeeklySummaryCard(
            totalSessions = state.weekSessions.size,
            totalMinutes = state.totalMinutesThisWeek
        )

        // ─── Días de la Semana (Diseño de Pastillas) ───────────────────────
        Text("Días de Actividad", style = MaterialTheme.typography.titleMedium, color = White, fontWeight = FontWeight.Bold)
        NewWeekDayIndicator(weekSessions = state.weekSessions)

        // ─── Línea de Tiempo de Entrenamientos Recientes ───────────────────
        Text("Historial Reciente", style = MaterialTheme.typography.titleMedium, color = White, fontWeight = FontWeight.Bold)

        if (state.recentSessions.isNotEmpty()) {
            RecentWorkoutsTimeline(sessions = state.recentSessions)
        } else if (!state.isLoading) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Indigo800),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp).fillMaxWidth()
                ) {
                    Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = Gray500, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("No hay entrenamientos recientes", color = Gray300, fontWeight = FontWeight.Bold)
                    Text("Toca en + Añadir o usa Health Connect", color = Gray400, fontSize = 12.sp)
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

// ─── Componente: Resumen Semanal ──────────────────────────────────────────────

@Composable
private fun WeeklySummaryCard(totalSessions: Int, totalMinutes: Int) {
    // Objetivo recomendado de la OMS: 150 mins de actividad moderada a la semana
    val goalMinutes = 150
    val progress = (totalMinutes.toFloat() / goalMinutes).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Indigo800)
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
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Violet400.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Violet300, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Actividad Semanal", style = MaterialTheme.typography.titleMedium, color = White, fontWeight = FontWeight.Bold)
                        Text("Recomendación de la OMS: 150m", style = MaterialTheme.typography.bodySmall, color = Gray400)
                    }
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text("$totalMinutes", style = MaterialTheme.typography.headlineLarge, color = White, fontWeight = FontWeight.Bold)
                Text(" min / 150 min", style = MaterialTheme.typography.bodyMedium, color = Gray400, modifier = Modifier.padding(bottom = 5.dp, start = 6.dp))
            }

            LinearProgressIndicator(
                progress   = { progress },
                modifier   = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color      = RingWorkout,
                trackColor = RingTrack
            )

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text  = if (totalMinutes >= goalMinutes) "¡Objetivo Semanal Superado! \uD83C\uDF89" else "Te faltan ${goalMinutes - totalMinutes} mins",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (totalMinutes >= goalMinutes) Emerald400 else Violet300,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text  = "$totalSessions sesiones",
                    style = MaterialTheme.typography.labelSmall,
                    color = Gray400
                )
            }
        }
    }
}

// ─── Componente: Indicador de Días (Nuevo Diseño) ─────────────────────────────

@Composable
private fun NewWeekDayIndicator(weekSessions: List<WorkoutSessionEntity>) {
    val days = listOf("L", "M", "X", "J", "V", "S", "D")
    val zone = ZoneId.systemDefault()
    val todayIdx = java.time.LocalDate.now().dayOfWeek.value - 1 // 0 (Lunes) a 6 (Domingo)

    // Agrupar sesiones de esta semana por el índice del día (0 a 6)
    val workedOutDays = weekSessions.map {
        Instant.ofEpochMilli(it.dateMillis).atZone(zone).get(ChronoField.DAY_OF_WEEK) - 1
    }.toSet()

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
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
                    .height(56.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(bgColor)
                    .border(1.5.dp, borderColor, RoundedCornerShape(24.dp))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dayLabel, style = MaterialTheme.typography.labelMedium, color = textColor, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    if (hasWorkout) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Indigo900, modifier = Modifier.size(14.dp))
                    } else {
                        // Gray600 ahora proviene de Color.kt via el import theme.*
                        Box(Modifier.size(6.dp).clip(CircleShape).background(if (isToday) Violet400 else Gray600))
                    }
                }
            }
        }
    }
}

// ─── Componente: Timeline de Historial Reciente ───────────────────────────────

/**
 * Devuelve el nombre legible del tipo de entrenamiento basado en [type].
 */
private fun workoutTypeName(type: String): String = when (type) {
    "STRENGTH" -> "Entrenamiento de Fuerza"
    "CARDIO"   -> "Entrenamiento de Cardio"
    "REST"     -> "Día de Descanso"
    else       -> "Entrenamiento"
}

@Composable
private fun RecentWorkoutsTimeline(sessions: List<WorkoutSessionEntity>) {
    val zone = ZoneId.systemDefault()
    val today = java.time.LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es"))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Indigo800)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        sessions.take(5).forEachIndexed { index, session ->
            val dt = Instant.ofEpochMilli(session.dateMillis).atZone(zone).toLocalDate()
            val dateStr = when {
                dt == today             -> "Hoy"
                dt == today.minusDays(1) -> "Ayer"
                else -> dt.format(formatter).replaceFirstChar { it.uppercase() }
            }

            // Nombre legible derivado del tipo de sesión almacenado
            val sessionName = workoutTypeName(session.type)

            Row(modifier = Modifier.fillMaxWidth()) {
                // Eje visual del timeline
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 16.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Violet400.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = Violet300, modifier = Modifier.size(18.dp))
                    }
                    if (index < sessions.take(5).size - 1) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(40.dp)
                                .background(Indigo600)
                        )
                    }
                }

                // Contenido de la sesión
                Column(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
                    Text(sessionName, style = MaterialTheme.typography.titleSmall, color = White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = Gray500, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        // U+00B7 MIDDLE DOT como separador entre fecha y duración
                        Text(
                            text  = "$dateStr \u00B7 ${if (session.durationMinutes > 0) "${session.durationMinutes} min" else "45 min"}",
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

    // Estado de error de validación para la duración
    var durationError by remember { mutableStateOf(false) }

    // Colores compartidos para los OutlinedTextField, coherentes con el resto de la app
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = Violet400,
        unfocusedBorderColor = Indigo600,
        focusedTextColor     = White,
        unfocusedTextColor   = White,
        focusedLabelColor    = Violet300,
        unfocusedLabelColor  = Gray400
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        title = { Text("Registrar Entrenamiento", color = White, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Añade manualmente la duración de tu sesión. Si lo deseas, puedes apuntar tu ejercicio principal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray300
                )

                // Campo duración con validación: debe ser > 0
                OutlinedTextField(
                    value = durationText,
                    onValueChange = {
                        durationText = it
                        // Limpiar error en cuanto el usuario empiece a corregir el valor
                        durationError = false
                    },
                    label = { Text("Duración Total (minutos)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = durationError,
                    supportingText = if (durationError) {
                        { Text("La duración debe ser mayor que 0", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    colors = textFieldColors
                )

                OutlinedTextField(
                    value = exerciseName,
                    onValueChange = { exerciseName = it },
                    label = { Text("Ejercicio Principal (Opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = repsText,
                        onValueChange = { repsText = it },
                        label = { Text("Reps") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = textFieldColors
                    )
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        label = { Text("Peso (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = textFieldColors
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val duration = durationText.toIntOrNull() ?: 0
                    // Validación: la duración debe ser mayor que 0
                    if (duration <= 0) {
                        durationError = true
                        return@Button
                    }
                    val reps = repsText.toIntOrNull() ?: 0
                    val weight = weightText.toFloatOrNull() ?: 0f
                    onSave(duration, exerciseName.trim(), reps, weight)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Violet400, contentColor = Indigo900)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Gray400) }
        }
    )
}
