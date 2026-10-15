package com.lifedashboard.presentation.screen.dashboard

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifedashboard.domain.model.CalendarEvent
import com.lifedashboard.domain.model.WorkoutType
import com.lifedashboard.presentation.screen.dashboard.components.*
import com.lifedashboard.presentation.theme.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardContent(
        state = state,
        onSelectDate = { viewModel.selectDate(it) },
        onRefreshAi = { viewModel.requestAiInsight() }
    )
}

@Composable
fun DashboardContent(
    state: DashboardUiState,
    onSelectDate: (LocalDate) -> Unit = {},
    onRefreshAi: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val greeting = remember {
        val hour = LocalDateTime.now().hour
        when {
            hour < 12 -> "Buenos días"
            hour < 20 -> "Buenas tardes"
            else      -> "Buenas noches"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors    = listOf(GradientStart, GradientEnd),
                    startY    = 0f,
                    endY      = 1200f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                // FIX #5: Usa WindowInsets para respetar la status bar del sistema;
                // el padding(top = 56.dp) se reemplaza por consumo de insets real.
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            DashboardHeader(greeting = greeting, state = state)

            DayHeroSection(state = state)

            PillarGrid(state = state)

            SectionTitle("Calendario de Actividad")
            CalendarView(
                progressByDate = state.progressByDate,
                selectedDate   = state.selectedDate,
                onDateSelected = onSelectDate
            )

            SelectedDayDetailCard(
                selectedDate = state.selectedDate,
                progress     = state.progressByDate[state.selectedDate]
            )

            // Siempre mostramos la sección de eventos: si está vacía se muestra
            // el estado vacío; si tiene datos, se listan las fichas.
            SectionTitle("Próximos Eventos")
            UpcomingEventsList(events = state.upcomingEvents)

            SectionTitle("Análisis Inteligente")
            AiInsightCard(
                insight   = state.aiInsight,
                isLoading = state.isAiLoading,
                onRefresh = onRefreshAi
            )
        }
    }
}

@Composable
private fun DashboardHeader(greeting: String, state: DashboardUiState) {
    val dateStr = LocalDate.now().format(
        DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("es"))
    ).replaceFirstChar { it.uppercase() }

    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier.fillMaxWidth()
    ) {
        Column {
            Text(
                text  = greeting,
                style = MaterialTheme.typography.headlineSmall,
                color = White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text  = dateStr,
                style = MaterialTheme.typography.bodyMedium,
                color = Gray400
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (state.workoutDoneToday) Violet400.copy(alpha = 0.2f)
                    else Indigo700
                )
                .border(1.5.dp, if (state.workoutDoneToday) Violet400 else Indigo600, CircleShape)
        ) {
            Icon(
                imageVector = when (state.workoutTypeToday) {
                    WorkoutType.STRENGTH -> Icons.Default.FitnessCenter
                    WorkoutType.CARDIO   -> Icons.Default.DirectionsBike
                    WorkoutType.REST     -> Icons.Default.Bedtime
                    null                 -> Icons.Default.Person
                },
                // Accesibilidad: describe el estado del entrenamiento de hoy
                contentDescription = when (state.workoutTypeToday) {
                    WorkoutType.STRENGTH -> "Entrenamiento de fuerza completado"
                    WorkoutType.CARDIO   -> "Entrenamiento de cardio completado"
                    WorkoutType.REST     -> "Día de descanso"
                    null                 -> "Sin entrenamiento registrado"
                },
                tint     = if (state.workoutDoneToday) Violet300 else Gray400,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun DayHeroSection(state: DashboardUiState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Violet400.copy(alpha = 0.15f),
                        Indigo800
                    ),
                    radius = 600f
                )
            )
            .border(1.dp, Indigo600, RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            DayProgressRings(
                stepsProgress    = state.stepsProgress,
                caloriesProgress = state.caloriesProgress,
                sleepProgress    = state.sleepProgress,
                size             = 110.dp,
                strokeWidth      = 10.dp,
                gap              = 5.dp
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                FillableStatItem(
                    value    = "${state.todaySteps} / ${if (state.stepsTarget >= 1000) "${state.stepsTarget / 1000}K" else state.stepsTarget}",
                    label    = "pasos",
                    progress = state.stepsProgress,
                    color    = RingSteps
                )
                FillableStatItem(
                    value    = "${(state.todaySleepHours * 10).toInt() / 10f}h / ${state.sleepTargetHours}h",
                    label    = "sueño",
                    progress = state.sleepProgress,
                    color    = RingSleep
                )
                FillableStatItem(
                    value    = "${state.todayCalories.toInt()} / ${state.caloriesTarget.toInt()} kcal",
                    label    = "calorías activas",
                    progress = state.caloriesProgress,
                    color    = Amber400
                )
            }
        }
    }
}

@Composable
private fun FillableStatItem(
    value: String,
    label: String,
    progress: Float,
    color: Color
) {
    val animatedProgress by animateFloatAsState(
        targetValue  = progress.coerceIn(0f, 1f),
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "stat_fill"
    )

    Column {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.Bottom,
            modifier              = Modifier.fillMaxWidth()
        ) {
            Text(
                text       = value,
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = White,
                fontSize   = 13.sp,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text     = label,
                style    = MaterialTheme.typography.labelSmall,
                color    = color,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress   = { animatedProgress },
            modifier   = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(2.5.dp)),
            color      = color,
            trackColor = RingTrack
        )
    }
}

@Composable
private fun PillarGrid(state: DashboardUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillarCard(
                title    = "Pasos",
                value    = state.todaySteps.toString(),
                unit     = "/ ${state.stepsTarget}",
                subtitle = when {
                    state.todaySteps >= state.stepsTarget -> "¡Meta superada!"
                    state.todaySteps >= 7_000             -> "Meta mínima cumplida"
                    else                                  -> "${state.stepsTarget - state.todaySteps} para la meta"
                },
                progress  = state.stepsProgress,
                ringColor = RingSteps,
                icon      = Icons.Default.DirectionsWalk,
                modifier  = Modifier.weight(1f)
            )
            PillarCard(
                title    = "Sueño",
                value    = "${(state.todaySleepHours * 10).toInt() / 10f}",
                unit     = "/ ${state.sleepTargetHours}h",
                subtitle = when {
                    state.todaySleepHours >= state.sleepTargetHours -> "Descanso óptimo"
                    state.todaySleepHours >= 6.5f                   -> "Aceptable"
                    else                                            -> "Insuficiente"
                },
                progress  = state.sleepProgress,
                ringColor = RingSleep,
                icon      = Icons.Default.Bedtime,
                modifier  = Modifier.weight(1f)
            )
        }

        // FIX #4: Emoji de checkmark corregido — se usa "✅" directamente como
        // carácter Unicode literal para evitar codificación incorrecta de "✓".
        PillarCard(
            title    = "Entrenamiento",
            value    = if (state.workoutDoneToday) "Completado" else "Pendiente",
            unit     = if (state.workoutDoneToday) "✅" else "",
            subtitle = when (state.workoutTypeToday) {
                WorkoutType.STRENGTH -> "Sesión de fuerza registrada"
                WorkoutType.CARDIO   -> "Sesión de cardio completada"
                WorkoutType.REST     -> "Día de descanso"
                null                 -> "Sin registro para hoy"
            },
            progress  = if (state.workoutDoneToday) 1f else 0f,
            ringColor = RingWorkout,
            icon      = Icons.Default.FitnessCenter,
            modifier  = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SelectedDayDetailCard(
    selectedDate: LocalDate,
    progress: com.lifedashboard.domain.model.DailyProgress?
) {
    val isToday = selectedDate == LocalDate.now()
    val dateStr = selectedDate.format(
        DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("es"))
    ).replaceFirstChar { it.uppercase() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Indigo800)
            .border(1.dp, Indigo700, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector        = Icons.Default.Event,
                        contentDescription = "Fecha seleccionada",
                        tint     = Violet300,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text  = dateStr,
                        style = MaterialTheme.typography.titleSmall,
                        color = White,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (isToday) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Violet400.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("HOY", style = MaterialTheme.typography.labelSmall, color = Violet300, fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider(color = Indigo700, thickness = 1.dp)

            if (progress != null) {
                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    DayStatChip(
                        label = "Entreno",
                        value = if (progress.workoutCompleted) "Hecho \u2713" else "No",
                        color = Color(0xFF3B82F6)
                    )
                    DayStatChip(
                        label = "Pasos",
                        value = "${progress.actualSteps}",
                        color = Color(0xFF1D4ED8)
                    )
                    // FIX #1: DailyProgress no tiene campo actualSleepHours, solo
                    // sleepProgress (0f..1f). Se asume meta de 8h y se formatea
                    // con String.format para evitar decimales imprecisos.
                    DayStatChip(
                        label = "Sueño",
                        value = String.format("%.1f", progress.sleepProgress * 8f) + "h",
                        color = RingSleep
                    )
                }
            } else {
                Text(
                    text  = "Sin actividad registrada para este día.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray400
                )
            }
        }
    }
}

@Composable
private fun DayStatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleSmall, color = White, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/**
 * Lista de eventos próximos.
 * FIX #2: Cuando la lista está vacía se muestra un estado visual con icono y
 * mensaje en lugar de no renderizar nada (evita sección vacía confusa).
 */
@Composable
private fun UpcomingEventsList(events: List<CalendarEvent>) {
    if (events.isEmpty()) {
        // Estado vacío: caja con fondo Indigo800 y esquinas redondeadas
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Indigo800)
                .border(1.dp, Indigo700, RoundedCornerShape(16.dp))
                .padding(vertical = 28.dp, horizontal = 16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector        = Icons.Default.EventBusy,
                    contentDescription = "Sin eventos próximos",
                    tint     = Gray500,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text      = "No hay eventos próximos",
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = Gray500,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            events.forEach { event ->
                EventChip(event = event)
            }
        }
    }
}

@Composable
private fun EventChip(event: CalendarEvent) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val dateFormatter = DateTimeFormatter.ofPattern("EEE d", Locale("es"))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Indigo800)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(event.calendarColor))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(event.title, style = MaterialTheme.typography.labelLarge, color = White)
            // FIX #3: Separador entre fecha y hora — se usa " \u00B7 " (U+00B7 MIDDLE DOT)
            // explícitamente como escape Unicode para garantizar la codificación correcta.
            Text(
                "${event.startTime.format(dateFormatter)} \u00B7 ${event.startTime.format(timeFormatter)}",
                style = MaterialTheme.typography.bodySmall,
                color = Gray400
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text       = title,
        style      = MaterialTheme.typography.titleMedium,
        color      = White,
        fontWeight = FontWeight.Bold
    )
}
