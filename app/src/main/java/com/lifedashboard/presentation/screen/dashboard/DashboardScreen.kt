package com.lifedashboard.presentation.screen.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import com.lifedashboard.data.local.entity.WeightEntryEntity
import com.lifedashboard.presentation.common.components.WeightEvolutionChart
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
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToHealth: (String) -> Unit = {},
    onNavigateToWorkout: () -> Unit = {},
    onNavigateToNutrition: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddWeightModal by remember { mutableStateOf(false) }

    DashboardContent(
        state                 = state,
        onSelectDate          = { viewModel.selectDate(it) },
        onRefreshAi           = { viewModel.requestAiInsight() },
        onAddWeightClick      = { showAddWeightModal = true },
        onNavigateToHealth    = onNavigateToHealth,
        onNavigateToWorkout   = onNavigateToWorkout,
        onNavigateToNutrition = onNavigateToNutrition
    )

    if (showAddWeightModal) {
        DashboardAddWeightModal(
            currentWeight = state.latestWeightKg ?: 75.0f,
            onDismiss     = { showAddWeightModal = false },
            onSave        = { weight, note ->
                viewModel.addWeightEntry(weight, note)
                showAddWeightModal = false
            }
        )
    }
}

@Composable
fun DashboardContent(
    state: DashboardUiState,
    onSelectDate: (LocalDate) -> Unit = {},
    onRefreshAi: () -> Unit = {},
    onAddWeightClick: () -> Unit = {},
    onNavigateToHealth: (String) -> Unit = {},
    onNavigateToWorkout: () -> Unit = {},
    onNavigateToNutrition: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val greeting = remember(state.userName) {
        val hour = LocalDateTime.now().hour
        val timeGreeting = when {
            hour < 12 -> "Buenos días"
            hour < 20 -> "Buenas tardes"
            else      -> "Buenas noches"
        }
        "$timeGreeting, ${state.userName}"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GradientStart, GradientEnd),
                    startY = 0f,
                    endY   = 1400f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(top = 56.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Cabecera principal con saludo y estado del día
            DashboardHeader(greeting = greeting, state = state)

            // 1. Resumen Hero (Anillos de progreso) - controlado por preferencia
            if (state.showHeroRings) {
                DayHeroSection(
                    state = state,
                    onNavigateToHealth = onNavigateToHealth
                )
            }

            // 2. Pilares de Salud y Rendimiento (Pasos, Sueño, Calorías, Entreno)
            PillarGrid(
                state = state,
                onNavigateToHealth = onNavigateToHealth,
                onNavigateToWorkout = onNavigateToWorkout
            )

            // Tarjeta de Acceso a Nutrición & Comidas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Indigo800,
                                Color(0xFF1E1442)
                            )
                        )
                    )
                    .border(1.dp, Indigo700, RoundedCornerShape(22.dp))
                    .clickable(onClick = onNavigateToNutrition)
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Violet400.copy(alpha = 0.15f))
                        ) {
                            Icon(Icons.Default.Restaurant, contentDescription = null, tint = Violet300, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Nutrición & Comidas", style = MaterialTheme.typography.titleSmall, color = White, fontWeight = FontWeight.Bold)
                            Text("Registra platos, macros o foto con IA ✨", style = MaterialTheme.typography.bodySmall, color = Gray400, fontSize = 11.sp)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Ir a nutrición", tint = Gray400, modifier = Modifier.size(18.dp))
                }
            }

            // Tarjeta de Evolución y Seguimiento de Peso en el Panel
            DashboardWeightCard(
                latestWeightKg = state.latestWeightKg,
                userHeightCm   = state.userHeightCm,
                weightHistory  = state.weightHistory,
                onAddWeight    = onAddWeightClick
            )

            // 3. Calendario de Actividad y consistencia
            SectionTitle("Calendario de Hábitos")
            CalendarView(
                progressByDate = state.progressByDate,
                selectedDate   = state.selectedDate,
                onDateSelected = onSelectDate
            )

            // 4. Detalle del día seleccionado
            SelectedDayDetailCard(
                selectedDate        = state.selectedDate,
                progress            = state.progressByDate[state.selectedDate],
                onNavigateToHealth  = onNavigateToHealth,
                onNavigateToWorkout = onNavigateToWorkout
            )

            // 5. Próximos Eventos - controlado por preferencia
            if (state.showUpcomingEvents) {
                SectionTitle("Próximos Eventos")
                UpcomingEventsList(events = state.upcomingEvents)
            }

            // 6. Análisis Inteligente - controlado por preferencia
            if (state.showAiInsight) {
                SectionTitle("Análisis Inteligente")
                AiInsightCard(
                    insight   = state.aiInsight,
                    isLoading = state.isAiLoading,
                    onRefresh = onRefreshAi
                )
            }
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
                text       = greeting,
                style      = MaterialTheme.typography.headlineSmall,
                color      = White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
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
                    if (state.workoutDoneToday) Violet400.copy(alpha = 0.25f)
                    else Indigo800
                )
                .border(1.5.dp, if (state.workoutDoneToday) Violet400 else Indigo700, CircleShape)
        ) {
            Icon(
                imageVector = when (state.workoutTypeToday) {
                    WorkoutType.STRENGTH -> Icons.Default.FitnessCenter
                    WorkoutType.CARDIO   -> Icons.AutoMirrored.Filled.DirectionsRun
                    WorkoutType.REST     -> Icons.Default.Bedtime
                    null                 -> Icons.Default.Person
                },
                contentDescription = "Estado del día",
                tint               = if (state.workoutDoneToday) Violet300 else Gray400,
                modifier           = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun DayHeroSection(
    state: DashboardUiState,
    onNavigateToHealth: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Violet400.copy(alpha = 0.20f),
                        Indigo800
                    ),
                    radius = 700f
                )
            )
            .border(1.dp, Indigo700, RoundedCornerShape(26.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header del Hero Card
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Cyan400)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text          = "PULSO BIOMÉTRICO",
                        fontSize      = 11.sp,
                        fontWeight    = FontWeight.Bold,
                        color         = Gray300,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text     = "✦ Health Connect",
                    fontSize = 11.sp,
                    color    = Gray500
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onNavigateToHealth("PASOS") }
                ) {
                    DayProgressRings(
                        stepsProgress    = state.stepsProgress,
                        caloriesProgress = state.caloriesProgress,
                        sleepProgress    = state.sleepProgress,
                        size             = 116.dp,
                        strokeWidth      = 9.dp
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier            = Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                ) {
                    FillableStatItem(
                        value    = "${state.todaySteps} / ${if (state.stepsTarget >= 1000) "${state.stepsTarget / 1000}K" else state.stepsTarget}",
                        label    = "pasos",
                        progress = state.stepsProgress,
                        color    = RingSteps,
                        onClick  = { onNavigateToHealth("PASOS") }
                    )
                    FillableStatItem(
                        value    = "${(state.todaySleepHours * 10).toInt() / 10f}h / ${state.sleepTargetHours}h",
                        label    = "sueño",
                        progress = state.sleepProgress,
                        color    = RingSleep,
                        onClick  = { onNavigateToHealth("SUENO") }
                    )
                    FillableStatItem(
                        value    = "${state.todayCalories.toInt()} / ${state.caloriesTarget.toInt()} kcal",
                        label    = "calorías",
                        progress = state.caloriesProgress,
                        color    = Amber400,
                        onClick  = { onNavigateToHealth("CALORIAS") }
                    )
                }
            }
        }
    }
}

@Composable
private fun FillableStatItem(
    value: String,
    label: String,
    progress: Float,
    color: Color,
    onClick: (() -> Unit)? = null
) {
    val animatedProgress by animateFloatAsState(
        targetValue   = progress.coerceIn(0f, 1f),
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label         = "stat_fill"
    )

    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Column(modifier = clickMod) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text     = label,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = color,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (onClick != null) {
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = color.copy(alpha = 0.7f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        LinearProgressIndicator(
            progress   = { animatedProgress },
            modifier   = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color      = color,
            trackColor = RingTrack
        )
    }
}

@Composable
private fun PillarGrid(
    state: DashboardUiState,
    onNavigateToHealth: (String) -> Unit,
    onNavigateToWorkout: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillarCard(
                title    = "Pasos",
                value    = state.todaySteps.toString(),
                unit     = "/ ${state.stepsTarget}",
                subtitle = when {
                    state.todaySteps >= state.stepsTarget -> "¡Meta superada!"
                    state.todaySteps >= 7_000             -> "Meta cumplida"
                    else                                  -> "${state.stepsTarget - state.todaySteps} para la meta"
                },
                progress  = state.stepsProgress,
                ringColor = RingSteps,
                icon      = Icons.AutoMirrored.Filled.DirectionsWalk,
                modifier  = Modifier.weight(1f),
                onClick   = { onNavigateToHealth("PASOS") }
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
                modifier  = Modifier.weight(1f),
                onClick   = { onNavigateToHealth("SUENO") }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillarCard(
                title     = "Calorías",
                value     = state.todayCalories.toInt().toString(),
                unit      = "/ ${state.caloriesTarget.toInt()} kcal",
                subtitle  = if (state.todayCalories >= state.caloriesTarget) "Objetivo alcanzado"
                            else "${(state.caloriesTarget - state.todayCalories).toInt()} kcal restantes",
                progress  = state.caloriesProgress,
                ringColor = Amber400,
                icon      = Icons.Default.LocalFireDepartment,
                modifier  = Modifier.weight(1f),
                onClick   = { onNavigateToHealth("CALORIAS") }
            )

            val workoutProgress = (state.weeklyWorkoutsCount.toFloat() / state.workoutDaysTarget.coerceAtLeast(1)).coerceIn(0f, 1f)
            PillarCard(
                title    = "Entreno",
                value    = "${state.weeklyWorkoutsCount}",
                unit     = "/ ${state.workoutDaysTarget} d",
                subtitle = when {
                    state.workoutDoneToday -> "Hoy completado ✓"
                    state.weeklyWorkoutsCount >= state.workoutDaysTarget -> "Meta semanal lograda"
                    else -> "${(state.workoutDaysTarget - state.weeklyWorkoutsCount).coerceAtLeast(0)} días restantes"
                },
                progress  = workoutProgress,
                ringColor = RingWorkout,
                icon      = Icons.Default.FitnessCenter,
                modifier  = Modifier.weight(1f),
                onClick   = onNavigateToWorkout
            )
        }
    }
}



@Composable
private fun SelectedDayDetailCard(
    selectedDate: LocalDate,
    progress: com.lifedashboard.domain.model.DailyProgress?,
    onNavigateToHealth: (String) -> Unit = {},
    onNavigateToWorkout: () -> Unit = {}
) {
    val isToday = selectedDate == LocalDate.now()
    val dateStr = selectedDate.format(
        DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("es"))
    ).replaceFirstChar { it.uppercase() }

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
                        imageVector        = Icons.Default.Event,
                        contentDescription = "Fecha seleccionada",
                        tint               = Violet300,
                        modifier           = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text       = dateStr,
                        style      = MaterialTheme.typography.titleSmall,
                        color      = White,
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    DayStatChip(
                        label   = "Entreno",
                        value   = if (progress.workoutCompleted) "Hecho ✓" else "Pendiente",
                        color   = Color(0xFF60A5FA),
                        onClick = onNavigateToWorkout
                    )
                    DayStatChip(
                        label   = "Pasos",
                        value   = "${progress.actualSteps}",
                        color   = Cyan400,
                        onClick = { onNavigateToHealth("PASOS") }
                    )
                    DayStatChip(
                        label   = "Sueño",
                        value   = "${String.format(Locale.US, "%.1f", progress.actualSleepHours)}h",
                        color   = Teal400,
                        onClick = { onNavigateToHealth("SUENO") }
                    )
                    DayStatChip(
                        label   = "Calorías",
                        value   = "${progress.actualCalories.toInt()}",
                        color   = Amber400,
                        onClick = { onNavigateToHealth("CALORIAS") }
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
private fun DayStatChip(
    label: String,
    value: String,
    color: Color,
    onClick: (() -> Unit)? = null
) {
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = clickMod.padding(4.dp)
    ) {
        Text(text = value, style = MaterialTheme.typography.titleSmall, color = White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun UpcomingEventsList(events: List<CalendarEvent>) {
    if (events.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Indigo800)
                .border(1.dp, Indigo700, RoundedCornerShape(18.dp))
                .padding(vertical = 24.dp, horizontal = 16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector        = Icons.Default.EventAvailable,
                    contentDescription = null,
                    tint               = Violet300,
                    modifier           = Modifier.size(28.dp)
                )
                Text(
                    text  = "No hay eventos próximos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray300,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text  = "Tu agenda está despejada para el resto del día",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray400
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
            .clip(RoundedCornerShape(16.dp))
            .background(Indigo800)
            .border(1.dp, Indigo700, RoundedCornerShape(16.dp))
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
            Text(
                text     = event.title,
                style    = MaterialTheme.typography.labelLarge,
                color    = White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text  = "${event.startTime.format(dateFormatter)} · ${event.startTime.format(timeFormatter)}",
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

// ─── Tarjeta de Evolución y Seguimiento de Peso en el Panel ────────────────────

@Composable
private fun DashboardWeightCard(
    latestWeightKg: Float?,
    userHeightCm: Int,
    weightHistory: List<WeightEntryEntity>,
    onAddWeight: () -> Unit
) {
    val displayWeight = latestWeightKg ?: 75.0f
    val imc = if (userHeightCm > 0) {
        val hM = userHeightCm / 100f
        displayWeight / (hM * hM)
    } else 24.0f

    val imcCategory = when {
        imc < 18.5f -> "Bajo peso" to Cyan400
        imc < 25.0f -> "Saludable" to Emerald400
        imc < 30.0f -> "Sobrepeso" to Amber400
        else        -> "Obesidad" to Rose400
    }

    val deltaTextAndColor: Triple<String, Color, androidx.compose.ui.graphics.vector.ImageVector?> = if (weightHistory.size >= 2) {
        val diff = displayWeight - weightHistory[1].weightKg
        when {
            diff < -0.05f -> Triple(String.format(Locale.US, "-%.1f kg", Math.abs(diff)), Emerald400, Icons.Default.ArrowDownward)
            diff > 0.05f  -> Triple(String.format(Locale.US, "+%.1f kg", diff), Amber400, Icons.Default.ArrowUpward)
            else          -> Triple("0.0 kg", Gray400, Icons.Default.Remove)
        }
    } else if (weightHistory.size == 1) {
        Triple("1er registro", Violet300, null)
    } else {
        Triple("Sin registros", Gray400, null)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Indigo800,
                        Color(0xFF22134E)
                    )
                )
            )
            .border(1.dp, Indigo700, RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Cabecera
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
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
                    Column {
                        Text(
                            text = "Evolución de Peso",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = White
                        )
                        Text(
                            text = "Historial y tendencia corporal",
                            style = MaterialTheme.typography.labelSmall,
                            color = Gray400,
                            fontSize = 11.sp
                        )
                    }
                }

                Button(
                    onClick = onAddWeight,
                    colors = ButtonDefaults.buttonColors(containerColor = Violet400),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Indigo900, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pesar", color = Indigo900, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // Fila central: Peso Actual + Variación + IMC
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Indigo900.copy(alpha = 0.55f))
                    .border(1.dp, Indigo700.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column {
                    Text(
                        text = "ÚLTIMO PESO",
                        style = MaterialTheme.typography.labelSmall,
                        color = Gray400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (latestWeightKg != null) String.format(Locale.US, "%.1f", displayWeight) else "--",
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

            // Gráfico interactivo de evolución de peso
            if (weightHistory.isNotEmpty()) {
                WeightEvolutionChart(
                    weightHistory = weightHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Indigo900.copy(alpha = 0.4f))
                        .padding(vertical = 20.dp, horizontal = 16.dp)
                ) {
                    Text(
                        text = "Toca en 'Pesar' para registrar tu primer peso y ver tu gráfica de evolución aquí.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Gray400,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardAddWeightModal(
    currentWeight: Float,
    onDismiss: () -> Unit,
    onSave: (Float, String?) -> Unit
) {
    var weightInput by remember { mutableStateOf(String.format(Locale.US, "%.1f", currentWeight)) }
    var noteInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Indigo800)
                .border(1.dp, Violet400.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = Violet300, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Registrar Pesaje", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = White)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Gray400, modifier = Modifier.size(18.dp))
                    }
                }

                Text(
                    text = "Introduce tu peso actual para actualizar tu progreso e IMC.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray300
                )

                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it },
                    label = { Text("Peso (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Nota opcional (ej: en ayunas)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Gray300),
                        border = BorderStroke(1.dp, Indigo600),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            val w = weightInput.replace(',', '.').toFloatOrNull()
                            if (w != null && w in 30f..300f) {
                                onSave(w, noteInput.ifBlank { null })
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Violet400),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Guardar", color = Indigo900, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
