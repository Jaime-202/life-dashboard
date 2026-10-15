package com.lifedashboard.presentation.screen.health

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lifedashboard.data.local.dao.HealthDao
import com.lifedashboard.data.local.entity.HealthDataEntity
import com.lifedashboard.data.repository.UserPreferencesRepository
import com.lifedashboard.presentation.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

enum class HealthTab { PASOS, SUENO, CALORIAS }

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class HealthUiState(
    val last7Days: List<HealthDataEntity> = emptyList(),
    val stepsTarget: Int = 10000,
    val sleepTarget: Float = 8.0f,
    val caloriesTarget: Int = 2200
)

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val healthDao: HealthDao,
    private val userPreferences: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthUiState())
    val uiState: StateFlow<HealthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val list = healthDao.getLastNDays(14)
            _uiState.update { it.copy(last7Days = list) }
        }

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

    fun updateStepsTarget(newTarget: Int) {
        if (newTarget > 0) {
            viewModelScope.launch { userPreferences.updateStepsTarget(newTarget) }
        }
    }

    fun updateSleepTarget(newTarget: Float) {
        if (newTarget > 0f) {
            viewModelScope.launch { userPreferences.updateSleepTarget(newTarget) }
        }
    }

    fun updateCaloriesTarget(newTarget: Int) {
        if (newTarget > 0) {
            viewModelScope.launch { userPreferences.updateCaloriesTarget(newTarget.toFloat()) }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun HealthScreen(viewModel: HealthViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(HealthTab.PASOS) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GradientStart, GradientEnd)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 56.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text       = "Salud & Metas",
            style      = MaterialTheme.typography.headlineSmall,
            color      = White,
            fontWeight = FontWeight.Bold
        )

        // ─── Selector de Sub-apartados ────────────────────────────────────
        TabSelector(selectedTab = selectedTab, onTabSelected = { selectedTab = it })

        // ─── Contenido según sub-apartado ────────────────────────────────
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "health_tab_content"
        ) { tab ->
            when (tab) {
                HealthTab.PASOS -> PasosSubSection(
                    state = state,
                    onTargetChange = { viewModel.updateStepsTarget(it) }
                )
                HealthTab.SUENO -> SuenoSubSection(
                    state = state,
                    onTargetChange = { viewModel.updateSleepTarget(it) }
                )
                HealthTab.CALORIAS -> CaloriasSubSection(
                    state = state,
                    onTargetChange = { viewModel.updateCaloriesTarget(it) }
                )
            }
        }
    }
}

// ─── Selector de pestañas ─────────────────────────────────────────────────────

@Composable
private fun TabSelector(selectedTab: HealthTab, onTabSelected: (HealthTab) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        HealthTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            val (label, icon, color) = when (tab) {
                HealthTab.PASOS    -> Triple("Pasos", Icons.Default.DirectionsWalk, RingSteps)
                HealthTab.SUENO    -> Triple("Sueño", Icons.Default.Bedtime, RingSleep)
                HealthTab.CALORIAS -> Triple("Calorías", Icons.Default.LocalFireDepartment, Amber400)
            }

            FilterChip(
                selected = isSelected,
                onClick  = { onTabSelected(tab) },
                leadingIcon = { Icon(icon, contentDescription = null, tint = if (isSelected) Indigo900 else color, modifier = Modifier.size(16.dp)) },
                label    = { Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color,
                    selectedLabelColor     = Indigo900,
                    containerColor         = Indigo800,
                    labelColor             = Gray300
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = isSelected,
                    borderColor = Color.Transparent, selectedBorderColor = Color.Transparent
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ─── Sub-apartado Pasos ───────────────────────────────────────────────────────

@Composable
private fun PasosSubSection(state: HealthUiState, onTargetChange: (Int) -> Unit) {
    val todaySteps = state.last7Days.firstOrNull()?.steps ?: 0
    val avgSteps   = if (state.last7Days.isNotEmpty()) state.last7Days.map { it.steps }.average().toInt() else 0
    val progress   = (todaySteps.toFloat() / state.stepsTarget).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GoalHeroCard(
            title      = "Pasos Diarios",
            currentVal = "$todaySteps",
            targetVal  = "${state.stepsTarget}",
            unit       = "pasos",
            progress   = progress,
            color      = RingSteps,
            icon       = Icons.Default.DirectionsWalk,
            subtitle   = "Promedio de 7 días: $avgSteps pasos"
        )

        CustomTargetInputCard(
            title      = "Ajustar Objetivo de Pasos",
            unitLabel  = "pasos",
            currentVal = state.stepsTarget.toString(),
            presets    = listOf(5000, 7000, 10000, 12000, 15000),
            formatPreset = { "${it / 1000}K" },
            onPresetSelect = { onTargetChange(it) },
            onSaveCustom   = { input ->
                input.toIntOrNull()?.let { onTargetChange(it) }
            }
        )

        HistoryMiniCalendar(
            data      = state.last7Days,
            getValue  = { it.steps.toFloat() },
            target    = state.stepsTarget.toFloat(),
            color     = RingSteps,
            formatVal = { "${it.toInt()}" }
        )
    }
}

// ─── Sub-apartado Sueño ───────────────────────────────────────────────────────

@Composable
private fun SuenoSubSection(state: HealthUiState, onTargetChange: (Float) -> Unit) {
    val todaySleep = state.last7Days.firstOrNull()?.sleepHours ?: 0f
    val avgSleep   = if (state.last7Days.isNotEmpty()) state.last7Days.map { it.sleepHours }.average() else 0.0
    val progress   = (todaySleep / state.sleepTarget).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GoalHeroCard(
            title      = "Sueño & Descanso",
            currentVal = "${(todaySleep * 10).toInt() / 10f}h",
            targetVal  = "${state.sleepTarget}h",
            unit       = "horas",
            progress   = progress,
            color      = RingSleep,
            icon       = Icons.Default.Bedtime,
            subtitle   = "Promedio de 7 días: ${(avgSleep * 10).toInt() / 10.0}h por noche"
        )

        CustomTargetInputCard(
            title      = "Ajustar Objetivo de Sueño",
            unitLabel  = "horas",
            currentVal = state.sleepTarget.toString(),
            presets    = listOf(6.5f, 7.0f, 7.5f, 8.0f, 8.5f),
            formatPreset = { "${it}h" },
            onPresetSelect = { onTargetChange(it) },
            onSaveCustom   = { input ->
                input.toFloatOrNull()?.let { onTargetChange(it) }
            }
        )

        HistoryMiniCalendar(
            data      = state.last7Days,
            getValue  = { it.sleepHours },
            target    = state.sleepTarget,
            color     = RingSleep,
            formatVal = { "${(it * 10).toInt() / 10f}h" }
        )
    }
}

// ─── Sub-apartado Calorías ────────────────────────────────────────────────────

@Composable
private fun CaloriasSubSection(state: HealthUiState, onTargetChange: (Int) -> Unit) {
    val todayCal = state.last7Days.firstOrNull()?.activeCalories?.toInt() ?: 0
    val avgCal   = if (state.last7Days.isNotEmpty()) state.last7Days.map { it.activeCalories }.average().toInt() else 0
    val progress = (todayCal.toFloat() / state.caloriesTarget).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GoalHeroCard(
            title      = "Calorías Activas",
            currentVal = "$todayCal",
            targetVal  = "${state.caloriesTarget}",
            unit       = "kcal",
            progress   = progress,
            color      = Amber400,
            icon       = Icons.Default.LocalFireDepartment,
            subtitle   = "Promedio de 7 días: $avgCal kcal / día"
        )

        CustomTargetInputCard(
            title      = "Ajustar Objetivo de Calorías",
            unitLabel  = "kcal",
            currentVal = state.caloriesTarget.toString(),
            presets    = listOf(1500, 1800, 2000, 2200, 2500),
            formatPreset = { "$it" },
            onPresetSelect = { onTargetChange(it) },
            onSaveCustom   = { input ->
                input.toIntOrNull()?.let { onTargetChange(it) }
            }
        )

        HistoryMiniCalendar(
            data      = state.last7Days,
            getValue  = { it.activeCalories },
            target    = state.caloriesTarget.toFloat(),
            color     = Amber400,
            formatVal = { "${it.toInt()}" }
        )
    }
}

// ─── Componente de Entrada de Número Personalizado ────────────────────────────

@Composable
private fun <T> CustomTargetInputCard(
    title: String,
    unitLabel: String,
    currentVal: String,
    presets: List<T>,
    formatPreset: (T) -> String,
    onPresetSelect: (T) -> Unit,
    onSaveCustom: (String) -> Unit
) {
    var textInput by remember(currentVal) { mutableStateOf(currentVal) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Indigo800)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = Gray300, fontWeight = FontWeight.Bold)

        // Entrada manual con caja de texto
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                label = { Text("Introducir número ($unitLabel)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Violet400,
                    unfocusedBorderColor = Indigo600,
                    focusedTextColor     = White,
                    unfocusedTextColor   = White,
                    focusedLabelColor    = Violet300,
                    unfocusedLabelColor  = Gray400
                ),
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = { onSaveCustom(textInput) },
                colors  = ButtonDefaults.buttonColors(containerColor = Violet400, contentColor = Indigo900),
                shape   = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        }

        // Acceso rápido / Presets
        Text("Acceso rápido:", style = MaterialTheme.typography.labelSmall, color = Gray400)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            presets.forEach { option ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Indigo700)
                        .clickable { onPresetSelect(option) }
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text       = formatPreset(option),
                        style      = MaterialTheme.typography.labelSmall,
                        color      = White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ─── Componentes Reutilizables ────────────────────────────────────────────────

@Composable
private fun GoalHeroCard(
    title: String,
    currentVal: String,
    targetVal: String,
    unit: String,
    progress: Float,
    color: Color,
    icon: ImageVector,
    subtitle: String
) {
    val animatedProgress by animateFloatAsState(
        targetValue  = progress,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "hero_progress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Indigo800)
            .border(1.dp, Indigo700, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(color.copy(alpha = 0.15f))
                    ) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium, color = White, fontWeight = FontWeight.Bold)
                }
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(currentVal, style = MaterialTheme.typography.headlineLarge, color = White, fontWeight = FontWeight.Bold)
                Text(" / $targetVal $unit", style = MaterialTheme.typography.bodyMedium, color = Gray400, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
            }

            LinearProgressIndicator(
                progress   = { animatedProgress },
                modifier   = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color      = color,
                trackColor = RingTrack
            )

            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Gray400)
        }
    }
}

@Composable
private fun HistoryMiniCalendar(
    data: List<HealthDataEntity>,
    getValue: (HealthDataEntity) -> Float,
    target: Float,
    color: Color,
    formatVal: (Float) -> String
) {
    val zone = ZoneId.systemDefault()
    val dayFmt = DateTimeFormatter.ofPattern("EEE d", Locale("es"))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Indigo800)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Histórico de Días Recientes", style = MaterialTheme.typography.labelLarge, color = Gray300, fontWeight = FontWeight.Bold)

        if (data.isNotEmpty()) {
            data.take(7).forEach { item ->
                val date = Instant.ofEpochMilli(item.dateMillis).atZone(zone).toLocalDate()
                val valNum = getValue(item)
                val isGoalMet = valNum >= target

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Indigo700)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        date.format(dayFmt).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                        color = Gray300,
                        modifier = Modifier.width(60.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        LinearProgressIndicator(
                            progress   = { (valNum / target).coerceIn(0f, 1f) },
                            modifier   = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color      = color,
                            trackColor = RingTrack
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        formatVal(valNum),
                        style = MaterialTheme.typography.labelMedium,
                        color = White,
                        fontWeight = FontWeight.Bold
                    )
                    if (isGoalMet) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.CheckCircle, null, tint = Emerald400, modifier = Modifier.size(16.dp))
                    }
                }
            }
        } else {
            Text("Sin historial registrado", style = MaterialTheme.typography.bodySmall, color = Gray500)
        }
    }
}
