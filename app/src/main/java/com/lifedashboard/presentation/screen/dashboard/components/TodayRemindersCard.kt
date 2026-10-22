package com.lifedashboard.presentation.screen.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifedashboard.presentation.screen.dashboard.DashboardReminderItem
import com.lifedashboard.presentation.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayRemindersCard(
    reminders: List<DashboardReminderItem>,
    selectedDate: LocalDate,
    onToggleReminder: (Long, Boolean) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isToday = selectedDate == LocalDate.now()
    val headerTitle = if (isToday) {
        "Tomas y Recordatorios de Hoy"
    } else {
        val formatted = selectedDate.format(DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("es")))
        "Tomas y Recordatorios ($formatted)"
    }

    val doneCount = reminders.count { it.isCompleted }
    val allDone = reminders.isNotEmpty() && doneCount == reminders.size

    Box(
        modifier = modifier
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
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header Row
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
                            .clip(RoundedCornerShape(10.dp))
                            .background(Violet400.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AlarmOn,
                            contentDescription = null,
                            tint = Violet300,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = headerTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isToday) "Toca el interruptor para marcar la toma" else "Registro histórico del día",
                            style = MaterialTheme.typography.bodySmall,
                            color = Gray400,
                            fontSize = 11.sp
                        )
                    }
                }

                if (reminders.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (allDone) Emerald400.copy(alpha = 0.2f) else Violet400.copy(alpha = 0.15f)
                            )
                            .border(
                                1.dp,
                                if (allDone) Emerald400.copy(alpha = 0.6f) else Violet400.copy(alpha = 0.4f),
                                CircleShape
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (allDone) "$doneCount/${reminders.size} ✅" else "$doneCount/${reminders.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (allDone) Emerald400 else Violet300,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (reminders.isEmpty()) {
                // Empty state compacto y elegante
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Indigo900.copy(alpha = 0.5f))
                        .border(1.dp, Indigo700.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "💊", fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Sin tomas programadas para hoy",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Gray300,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Configura creatina, vitaminas o hábitos",
                                style = MaterialTheme.typography.bodySmall,
                                color = Gray500,
                                fontSize = 11.sp
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onNavigateToSettings,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Violet400),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Añadir", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // List of reminders
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    reminders.forEach { reminder ->
                        ReminderRow(
                            item = reminder,
                            onToggle = { isChecked -> onToggleReminder(reminder.id, isChecked) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(
    item: DashboardReminderItem,
    onToggle: (Boolean) -> Unit
) {
    val backgroundColor = if (item.isCompleted) {
        Indigo900.copy(alpha = 0.35f)
    } else {
        Indigo900.copy(alpha = 0.65f)
    }

    val borderColor = if (item.isCompleted) {
        Emerald400.copy(alpha = 0.3f)
    } else {
        Indigo700.copy(alpha = 0.6f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
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
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (item.isCompleted) Emerald400.copy(alpha = 0.15f) else Violet400.copy(alpha = 0.15f)
                        )
                ) {
                    Text(
                        text = item.iconEmoji.ifBlank { "💊" },
                        fontSize = 16.sp
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (item.isCompleted) Gray400 else White,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.isCompleted) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = if (item.isCompleted) "Completado hoy" else item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.isCompleted) Emerald400 else Gray400,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Interruptor interactivo compacto
            Switch(
                checked = item.isCompleted,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = White,
                    checkedTrackColor = Emerald400,
                    uncheckedThumbColor = Gray400,
                    uncheckedTrackColor = Indigo700
                ),
                modifier = Modifier.scale(0.85f)
            )
        }
    }
}
