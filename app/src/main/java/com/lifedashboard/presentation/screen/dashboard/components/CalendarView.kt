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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * CalendarView
 *
 * Componente principal de calendario con 3 vistas:
 * - WEEK:  7 días con tarjeta y anillos
 * - MONTH: Grid mensual con código de colores según actividad (azul / azul oscuro)
 * - YEAR:  Heatmap anual por meses sin scroll anidado para evitar cierres
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

    Column(modifier = modifier) {
        // ─── Selector de vista ────────────────────────────────────────────
        ViewModeSelector(
            selected = viewMode,
            onSelect = { viewMode = it }
        )

        Spacer(Modifier.height(12.dp))

        // ─── Header de navegación ─────────────────────────────────────────
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

        Spacer(Modifier.height(8.dp))

        // ─── Contenido según vista ────────────────────────────────────────
        AnimatedContent(
            targetState = viewMode,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(150))
            },
            label = "calendar_view"
        ) { mode ->
            when (mode) {
                CalendarViewMode.WEEK  -> WeekView(
                    currentMonth    = currentMonth,
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

// ─── Selector de vista (chips) ────────────────────────────────────────────────

@Composable
private fun ViewModeSelector(
    selected: CalendarViewMode,
    onSelect: (CalendarViewMode) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        CalendarViewMode.entries.forEach { mode ->
            val isSelected = mode == selected
            val label = when (mode) {
                CalendarViewMode.WEEK  -> "Semana"
                CalendarViewMode.MONTH -> "Mes"
                CalendarViewMode.YEAR  -> "Año"
            }
            FilterChip(
                selected = isSelected,
                onClick  = { onSelect(mode) },
                label    = { Text(label, fontSize = 12.sp) },
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Violet400,
                    selectedLabelColor     = Indigo900,
                    containerColor         = Indigo700,
                    labelColor             = Gray300
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled             = true,
                    selected            = isSelected,
                    borderColor         = Color.Transparent,
                    selectedBorderColor = Color.Transparent
                )
            )
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
        IconButton(onClick = onPrev) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Anterior", tint = Gray300)
        }
        Text(
            text  = displayDate,
            style = MaterialTheme.typography.titleMedium,
            color = White
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Siguiente", tint = Gray300)
        }
    }
}

// ─── Vista Semanal ────────────────────────────────────────────────────────────

@Composable
private fun WeekView(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    progressByDate: Map<LocalDate, DailyProgress>,
    onDateSelected: (LocalDate) -> Unit
) {
    val weekStart = selectedDate.with(java.time.DayOfWeek.MONDAY)
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items((0..6).toList()) { dayOffset ->
            val date = weekStart.plusDays(dayOffset.toLong())
            val progress = progressByDate[date]
            WeekDayCell(
                date       = date,
                progress   = progress,
                isSelected = date == selectedDate,
                onClick    = { onDateSelected(date) }
            )
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
        .replaceFirstChar { it.uppercase() }
    val isToday = date == LocalDate.now()

    val workoutDone = progress?.workoutCompleted == true
    val stepsDone   = (progress?.stepsProgress ?: 0f) >= 0.7f

    val bg = when {
        isSelected               -> Indigo600
        workoutDone && stepsDone -> Color(0xFF1E3A8A)
        stepsDone                -> Color(0xFF1D4ED8)
        workoutDone              -> Color(0xFF3B82F6)
        else                     -> Indigo800
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(62.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(
                width = if (isToday) 1.5.dp else 0.dp,
                color = if (isToday) Violet300 else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
        Text(
            text  = dayName,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) Violet300 else Gray400
        )
        Spacer(Modifier.height(8.dp))

        Box(contentAlignment = Alignment.Center) {
            if (isToday) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Violet400.copy(alpha = 0.25f))
                )
            }
            Text(
                text       = date.dayOfMonth.toString(),
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = if (isToday || workoutDone || stepsDone) FontWeight.Bold else FontWeight.Normal,
                color      = White
            )
        }

        Spacer(Modifier.height(10.dp))

        if (progress != null) {
            DayProgressRings(
                stepsProgress    = progress.stepsProgress,
                caloriesProgress = if (progress.workoutCompleted) 1f else 0f,
                sleepProgress    = progress.sleepProgress,
                size             = 40.dp,
                strokeWidth      = 4.dp,
                gap              = 2.5.dp
            )
        } else {
            Box(Modifier.size(40.dp))
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
                    text      = day,
                    modifier  = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style     = MaterialTheme.typography.labelSmall,
                    color     = Gray400
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

    val backgroundColor = when {
        isSelected               -> Indigo600
        workoutDone && stepsDone -> Color(0xFF1E3A8A) // Azul muy oscuro/intenso (Ambos)
        stepsDone                -> Color(0xFF1D4ED8) // Azul oscuro (Pasos)
        workoutDone              -> Color(0xFF3B82F6) // Azul claro (Entreno)
        else                     -> Indigo800
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
            .aspectRatio(0.95f)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .border(
                width = if (isToday) 1.5.dp else if (isSelected) 1.dp else 0.dp,
                color = if (isToday) Violet300 else if (isSelected) Color(0xFF93C5FD) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(2.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = date.dayOfMonth.toString(),
                style      = MaterialTheme.typography.labelSmall,
                color      = contentColor,
                fontWeight = if (workoutDone || stepsDone || isToday) FontWeight.Bold else FontWeight.Normal,
                fontSize   = 11.sp
            )
            if (progress != null && !isFuture) {
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (workoutDone) {
                        Box(
                            Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF60A5FA))
                        )
                    }
                    if (stepsDone) {
                        Box(
                            Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF93C5FD))
                        )
                    }
                }
            }
        }
    }
}

// ─── Vista Anual (Sin LazyGrid para evitar crash) ─────────────────────────────

@Composable
private fun YearView(
    year: Int,
    progressByDate: Map<LocalDate, DailyProgress>,
    onDateSelected: (LocalDate) -> Unit
) {
    val monthNames = listOf("Ene","Feb","Mar","Abr","May","Jun",
                            "Jul","Ago","Sep","Oct","Nov","Dic")

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
            text  = label,
            style = MaterialTheme.typography.labelMedium,
            color = Gray400
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
                            date.isAfter(LocalDate.now()) -> RingTrack.copy(alpha = 0.3f)
                            progress == null              -> RingTrack.copy(alpha = 0.3f)
                            progress.workoutCompleted && progress.stepsProgress >= 0.7f -> Color(0xFF1E3A8A) // Azul muy oscuro
                            progress.stepsProgress >= 0.7f                              -> Color(0xFF1D4ED8) // Azul oscuro
                            progress.workoutCompleted                                   -> Color(0xFF3B82F6) // Azul claro
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
