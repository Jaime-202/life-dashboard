package com.lifedashboard.presentation.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifedashboard.data.local.entity.WeightEntryEntity
import com.lifedashboard.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WeightHistoryModal(
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
                    Text("Historial de Pesajes", color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Violet400.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${history.size} reg",
                        style = MaterialTheme.typography.labelSmall,
                        color = Violet300,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            if (history.isEmpty()) {
                Text(
                    text = "No hay registros guardados en tu historial de pesaje.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray400,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    history.forEachIndexed { index, entry ->
                        val nextEntry = history.getOrNull(index + 1)
                        val diff = if (nextEntry != null) entry.weightKg - nextEntry.weightKg else null

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Indigo900.copy(alpha = 0.7f))
                                .border(1.dp, Indigo700.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${String.format(Locale.US, "%.1f", entry.weightKg)} kg",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (diff != null && Math.abs(diff) > 0.05f) {
                                        Spacer(Modifier.width(8.dp))
                                        val isLoss = diff < 0
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isLoss) Emerald400.copy(alpha = 0.18f) else Amber400.copy(alpha = 0.18f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (isLoss) String.format(Locale.US, "%.1f kg", diff) else String.format(Locale.US, "+%.1f kg", diff),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isLoss) Emerald400 else Amber400,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
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
                                    tint = Rose400.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = Violet300, fontWeight = FontWeight.Bold)
            }
        }
    )
}
