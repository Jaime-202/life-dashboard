package com.lifedashboard.presentation.screen.dashboard.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifedashboard.domain.model.DailyProgress
import com.lifedashboard.presentation.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.*

enum class CalendarViewMode { WEEK, MONTH, YEAR }

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * CalendarView (Rediseño Estético Obsidian)
 *
 * Componente de calendario con estética moderna obsidian/glassmorphic:
 * - Contenedor con borde sutil y fondo translúcido
 * - Selector segmentado de vista estilo cápsula
 * - Vista semanal con micro-barras biométricas
 * - Grid mensual limpio con indicadores de hábitos brillantes
 * - Heatmap anual sin anidamiento problemático
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Composable
fun CalendarView(
    progressByDate: Map<LocalDate, DailyProgress>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    var viewMode by remember { mutableStateOf(CalendarViewMode.MONTH) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Indigo800.copy(alpha = 0.65f))
            .border(1.dp, Indigo700, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Column {
            // ─── Selector de vista segmentado moderno ─────────────────────────
            ModernViewModeSelector(
                selected = viewMode,
                onSelect = { viewMode = it }
            )

            Spacer(Modifier.height(14.dp))

            // ─── Header de navegación con controles sutiles ───────────────────
            MonthNavigationHeader(
                currentMonth = currentMonth,
                selectedDate = selectedDate,
                viewMode     = viewMode,
                onPrev       = {
                    if (viewMode == CalendarViewMode.WEEK) {
                        onDateSelected(selectedDate.minusWeeks(1))
                    } else {
                        currentMonth = currentMonth.minusMonths(1)
                    }
                },
                onNext       = {
                    if (viewMode == CalendarViewMode.WEEK) {
                        onDateSelected(selectedDate.plusWeeks(1))
                    } else {
                        currentMonth = currentMonth.plusMonths(1)
                    }
                }
            )

            Spacer(Modifier.height(12.dp))

            // ─── Contenido animado según la vista seleccionada ────────────────
            AnimatedContent(
                targetState = viewMode,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                },
                label = "calendar_view"
            ) { mode ->
                when (mode) {
                    CalendarViewMode.WEEK  -> WeekView(
                        selectedDate    = selectedDate,
                        progressByDate  = progressByDate,
                        onDateSelected  = onDateSelected
                    )
                    CalendarViewMode.MONTH -> MonthView(
                        currentMonth    = currentMonth,
                        selectedDate    = selectedDate,
                        progressByDate  = progressByDate,
                        onDateSelected  = onDateSelected
                    )
                    CalendarViewMode.YEAR  -> YearView(
                        year           = currentMonth.year,
                        progressByDate = progressByDate,
                        onDateSelected = onDateSelected
                    )
                }
            }
        }
    }
}

// ─── Selector de vista estilo cápsula ─────────────────────────────────────────

@Composable
private fun ModernViewModeSelector(
    selected: CalendarViewMode,
    onSelect: (CalendarViewMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Indigo900.copy(alpha = 0.8f))
            .border(1.dp, Indigo700.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CalendarViewMode.entries.forEach { mode ->
            val isSelected = mode == selected
            val label = when (mode) {
                CalendarViewMode.WEEK  -> "Semana"
                CalendarViewMode.MONTH -> "Mes"
                CalendarViewMode.YEAR  -> "Año"
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSelected) Violet400 else Color.Transparent)
                    .clickable { onSelect(mode) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = label,
                    fontSize   = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color      = if (isSelected) Indigo900 else Gray400
                )
            }
        }
    }
}

// ─── Header de navegación ─────────────────────────────────────────────────────

@Composable
private fun MonthNavigationHeader(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    viewMode: CalendarViewMode,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern(
        if (viewMode == CalendarViewMode.YEAR) "yyyy" else "MMMM yyyy",
        Locale("es")
    )
    val displayDate = if (viewMode == CalendarViewMode.WEEK) {
        YearMonth.from(selectedDate).format(formatter).replaceFirstChar { it.uppercase() }
    } else {
        currentMonth.format(formatter).replaceFirstChar { it.uppercase() }
    }

    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier.fillMaxWidth()
    ) {
        IconButton(
            onClick  = onPrev,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Indigo700.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector        = Icons.Default.ChevronLeft,
                contentDescription = "Anterior",
                tint               = Gray300,
                modifier           = Modifier.size(18.dp)
            )
        }

        Text(
            text       = displayDate,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color      = White
        )

        IconButton(
            onClick  = onNext,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Indigo700.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector        = Icons.Default.ChevronRight,
                contentDescription = "Siguiente",
                tint               = Gray300,
                modifier           = Modifier.size(18.dp)
            )
        }
    }
}

// ─── Vista Semanal ────────────────────────────────────────────────────────────

@Composable
private fun WeekView(
    selectedDate: LocalDate,
    progressByDate: Map<LocalDate, DailyProgress>,
    onDateSelected: (LocalDate) -> Unit
) {
    val weekStart = selectedDate.with(java.time.DayOfWeek.MONDAY)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        (0..6).forEach { dayOffset ->
            val date = weekStart.plusDays(dayOffset.toLong())
            val progress = progressByDate[date]
            Box(modifier = Modifier.weight(1f)) {
                WeekDayCell(
                    date       = date,
                    progress   = progress,
                    isSelected = date == selectedDate,
                    onClick    = { onDateSelected(date) }
                )
            }
        }
    }
}

@Composable
private fun WeekDayCell(
    date: LocalDate,
    progress: DailyProgress?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dayName = date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale("es"))
        .replaceFirstChar { it.uppercase() }.take(1)
    val isToday = date == LocalDate.now()

    val workoutDone = progress?.workoutCompleted == true
    val stepsDone   = (progress?.stepsProgress ?: 0f) >= 0.7f

    val bg = when {
        isSelected  -> Indigo600
        workoutDone -> Indigo700
        else        -> Indigo900.copy(alpha = 0.5f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(
                width = if (isToday) 1.5.dp else if (isSelected) 1.dp else 0.dp,
                color = if (isToday) Violet300 else if (isSelected) Violet400 else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp)
    ) {
        Text(
            text       = dayName,
            style      = MaterialTheme.typography.labelSmall,
            color      = if (isSelected) Violet300 else Gray400,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(6.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (isToday) Violet400.copy(alpha = 0.25f) else Color.Transparent)
        ) {
            Text(
                text       = date.dayOfMonth.toString(),
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color      = White
            )
        }

        Spacer(Modifier.height(8.dp))

        if (progress != null) {
            CalendarDayRings(
                workoutProgress = if (progress.workoutCompleted) 1f else 0f,
                stepsProgress    = progress.stepsProgress,
                sleepProgress    = progress.sleepProgress,
                modifier         = Modifier.padding(horizontal = 2.dp)
            )
        } else {
            Spacer(Modifier.height(14.dp))
        }
    }
}

// ─── Vista Mensual ────────────────────────────────────────────────────────────

@Composable
private fun MonthView(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    progressByDate: Map<LocalDate, DailyProgress>,
    onDateSelected: (LocalDate) -> Unit
) {
    val daysOfWeek = listOf("L", "M", "X", "J", "V", "S", "D")
    val firstDay = currentMonth.atDay(1)
    val firstDayOffset = (firstDay.dayOfWeek.value - 1)

    Column {
        Row(Modifier.fillMaxWidth()) {
            daysOfWeek.forEach { day ->
                Text(
                    text       = day,
                    modifier   = Modifier.weight(1f),
                    textAlign  = TextAlign.Center,
                    style      = MaterialTheme.typography.labelSmall,
                    color      = Gray400,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        val totalCells = firstDayOffset + currentMonth.lengthOfMonth()
        val rows = (totalCells + 6) / 7

        repeat(rows) { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(7) { col ->
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - firstDayOffset + 1
                    val date = if (dayNumber in 1..currentMonth.lengthOfMonth()) {
                        currentMonth.atDay(dayNumber)
                    } else null

                    Box(Modifier.weight(1f)) {
                        if (date != null) {
                            MonthDayCell(
                                date       = date,
                                progress   = progressByDate[date],
                                isSelected = date == selectedDate,
                                onClick    = { onDateSelected(date) }
                            )
                        } else {
                            Spacer(Modifier.aspectRatio(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun MonthDayCell(
    date: LocalDate,
    progress: DailyProgress?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isToday = date == LocalDate.now()
    val isFuture = date.isAfter(LocalDate.now())

    val workoutDone = progress?.workoutCompleted == true
    val stepsDone   = (progress?.stepsProgress ?: 0f) >= 0.7f
    val sleepDone   = (progress?.sleepProgress ?: 0f) >= 0.8f

    val backgroundColor = when {
        isSelected               -> Indigo600
        workoutDone && stepsDone -> Indigo700
        workoutDone              -> Indigo700.copy(alpha = 0.85f)
        stepsDone                -> Color(0xFF1E2652)
        else                     -> Indigo900.copy(alpha = 0.5f)
    }

    val contentColor = when {
        workoutDone || stepsDone || isSelected || isToday -> White
        isFuture -> Gray500
        else     -> Gray300
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .border(
                width = if (isToday) 1.5.dp else if (isSelected) 1.dp else 0.dp,
                color = if (isToday) Violet300 else if (isSelected) Violet400 else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(2.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text       = date.dayOfMonth.toString(),
                style      = MaterialTheme.typography.labelSmall,
                color      = contentColor,
                fontWeight = if (workoutDone || stepsDone || isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize   = 11.sp
            )

            if (progress != null && !isFuture) {
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Dot Pasos
                    if (stepsDone) {
                        Box(
                            Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(Cyan400)
                        )
                    }
                    // Dot Sueño
                    if (sleepDone) {
                        Box(
                            Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(Teal400)
                        )
                    }
                    // Dot Entreno
                    if (workoutDone) {
                        Box(
                            Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(Violet400)
                        )
                    }
                }
            }
        }
    }
}

// ─── Vista Anual (Heatmap Seguro sin Crash) ───────────────────────────────────

@Composable
private fun YearView(
    year: Int,
    progressByDate: Map<LocalDate, DailyProgress>,
    onDateSelected: (LocalDate) -> Unit
) {
    val monthNames = listOf("Ene","Feb","Mar","Abr","May","Jun",
                            "Jul","Ago","Sep","Oct","Nov","Dic")

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(6) { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                repeat(2) { col ->
                    val monthIdx = row * 2 + col
                    val month = YearMonth.of(year, monthIdx + 1)
                    Box(modifier = Modifier.weight(1f)) {
                        MiniMonthHeatmap(
                            month          = month,
                            label          = monthNames[monthIdx],
                            progressByDate = progressByDate,
                            onDateSelected = onDateSelected
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniMonthHeatmap(
    month: YearMonth,
    label: String,
    progressByDate: Map<LocalDate, DailyProgress>,
    onDateSelected: (LocalDate) -> Unit
) {
    Column {
        Text(
            text       = label,
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color      = Gray300
        )
        Spacer(Modifier.height(4.dp))

        val firstDayOffset = month.atDay(1).dayOfWeek.value - 1
        val daysInMonth    = month.lengthOfMonth()

        val totalCells = firstDayOffset + daysInMonth
        val rows = (totalCells + 6) / 7

        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(7) { col ->
                    val cellIndex = row * 7 + col
                    val dayNum = cellIndex - firstDayOffset + 1
                    if (dayNum in 1..daysInMonth) {
                        val date = month.atDay(dayNum)
                        val progress = progressByDate[date]
                        val activityColor = when {
                            date.isAfter(LocalDate.now()) -> RingTrack.copy(alpha = 0.25f)
                            progress == null              -> RingTrack.copy(alpha = 0.25f)
                            progress.workoutCompleted && progress.stepsProgress >= 0.7f -> Violet400
                            progress.stepsProgress >= 0.7f                              -> Cyan400
                            progress.workoutCompleted                                   -> Violet300
                            else                                                         -> RingTrack.copy(alpha = 0.5f)
                        }
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(activityColor)
                                .clickable { onDateSelected(date) }
                        )
                    } else {
                        Spacer(Modifier.size(9.dp))
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
