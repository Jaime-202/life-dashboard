package com.lifedashboard.presentation.screen.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifedashboard.core.worker.WorkManagerScheduler
import com.lifedashboard.data.repository.UserPreferencesRepository
import com.lifedashboard.domain.repository.CalendarRepository
import com.lifedashboard.presentation.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ThemeMode { OSCURO, CLARO, SISTEMA }

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class SettingsState(
    val userName: String = "Jaime",
    val themeMode: ThemeMode = ThemeMode.OSCURO,
    val showHeroRings: Boolean = true,
    val showAiInsight: Boolean = true,
    val showUpcomingEvents: Boolean = true,
    val dailyReminders: Boolean = true,
    val geminiApiKey: String = "",
    val symmetryFolderUri: String? = null,
    val healthConnectGranted: Boolean = false,
    val calendarGranted: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val scheduler: WorkManagerScheduler,
    private val calendarRepository: CalendarRepository,
    private val userPreferences: UserPreferencesRepository
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        userPreferences.userGoals
            .onEach { goals ->
                val currentTheme = when (goals.themeMode) {
                    "CLARO"   -> ThemeMode.CLARO
                    "SISTEMA" -> ThemeMode.SISTEMA
                    else      -> ThemeMode.OSCURO
                }
                _state.value = _state.value.copy(
                    userName           = goals.userName,
                    themeMode          = currentTheme,
                    showHeroRings      = goals.showHeroRings,
                    showAiInsight      = goals.showAiInsight,
                    showUpcomingEvents = goals.showUpcomingEvents,
                    dailyReminders     = goals.dailyReminders,
                    geminiApiKey       = goals.geminiApiKey
                )
            }
            .launchIn(viewModelScope)
    }

    fun updateUserName(name: String) {
        if (name.isNotBlank()) {
            viewModelScope.launch { userPreferences.updateUserName(name.trim()) }
        }
    }

    fun updateGeminiApiKey(apiKey: String) {
        viewModelScope.launch { userPreferences.updateGeminiApiKey(apiKey.trim()) }
    }

    fun checkPermissions(context: Context) {
        val calendarPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        val healthStatus = try {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            false
        }

        _state.value = _state.value.copy(
            calendarGranted = calendarPermission,
            healthConnectGranted = healthStatus
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            userPreferences.updateThemeMode(mode.name)
        }
    }

    fun toggleShowHeroRings(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.updateShowHeroRings(enabled)
        }
    }

    fun toggleShowAiInsight(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.updateShowAiInsight(enabled)
        }
    }

    fun toggleShowUpcomingEvents(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.updateShowUpcomingEvents(enabled)
        }
    }

    fun toggleDailyReminders(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.updateDailyReminders(enabled)
        }
    }

    fun onSymmetryFolderSelected(uri: Uri, context: Context) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val uriString = uri.toString()
            _state.value = _state.value.copy(symmetryFolderUri = uriString)
            scheduler.scheduleSymmetrySync(uriString)
            Toast.makeText(context, "Carpeta de Symmetry configurada", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error al acceder a la carpeta", Toast.LENGTH_SHORT).show()
        }
    }

    fun onHealthConnectConnect(context: Context) {
        _state.value = _state.value.copy(healthConnectGranted = true)
        scheduler.runHealthSyncNow()
        Toast.makeText(context, "Sincronizando con Health Connect / Salud", Toast.LENGTH_SHORT).show()
    }

    fun onCalendarPermissionGranted(context: Context) {
        _state.value = _state.value.copy(calendarGranted = true)
        CoroutineScope(Dispatchers.IO).launch {
            calendarRepository.syncCalendarEvents()
        }
        Toast.makeText(context, "Calendario sincronizado", Toast.LENGTH_SHORT).show()
    }

    fun forceSyncAll(context: Context) {
        scheduler.runHealthSyncNow()
        CoroutineScope(Dispatchers.IO).launch {
            calendarRepository.syncCalendarEvents()
        }
        Toast.makeText(context, "Sincronizando fuentes de datos…", Toast.LENGTH_SHORT).show()
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showUserModal by remember { mutableStateOf(false) }
    var showApiKeyModal by remember { mutableStateOf(false) }

    val healthPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class)
    )

    val healthPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { _ ->
        viewModel.onHealthConnectConnect(context)
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermissions(context)
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onCalendarPermissionGranted(context)
        } else {
            Toast.makeText(context, "Permiso de calendario denegado", Toast.LENGTH_SHORT).show()
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.onSymmetryFolderSelected(it, context) }
    }

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
            text       = "Personalización & Ajustes",
            style      = MaterialTheme.typography.headlineSmall,
            color      = White,
            fontWeight = FontWeight.Bold
        )

        UserProfileCard(
            userName = state.userName,
            onOpenProfile = { showUserModal = true },
            onSyncAll = { viewModel.forceSyncAll(context) }
        )

        // ─── Apariencia & Tema ─────────────────────────────────────────────
        SectionHeader("Apariencia & Tema")
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Modo de Tema", style = MaterialTheme.typography.labelLarge, color = White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ThemeOptionChip("Oscuro", Icons.Default.DarkMode, state.themeMode == ThemeMode.OSCURO, Modifier.weight(1f)) {
                        viewModel.setThemeMode(ThemeMode.OSCURO)
                    }
                    ThemeOptionChip("Claro", Icons.Default.LightMode, state.themeMode == ThemeMode.CLARO, Modifier.weight(1f)) {
                        viewModel.setThemeMode(ThemeMode.CLARO)
                    }
                    ThemeOptionChip("Sistema", Icons.Default.SettingsSuggest, state.themeMode == ThemeMode.SISTEMA, Modifier.weight(1f)) {
                        viewModel.setThemeMode(ThemeMode.SISTEMA)
                    }
                }
            }
        }

        // ─── Personalización del Panel ─────────────────────────────────────
        SectionHeader("Personalización del Panel")
        SettingsCard {
            SwitchSettingItem(
                title    = "Mostrar Anillos de Progreso",
                subtitle = "Resumen de pasos, calorías y sueño en la cabecera",
                checked  = state.showHeroRings,
                onCheckedChange = { viewModel.toggleShowHeroRings(it) }
            )
            HorizontalDivider(color = Indigo700)
            SwitchSettingItem(
                title    = "Mostrar Análisis de IA",
                subtitle = "Tarjeta con consejos inteligentes y accionables",
                checked  = state.showAiInsight,
                onCheckedChange = { viewModel.toggleShowAiInsight(it) }
            )
            HorizontalDivider(color = Indigo700)
            SwitchSettingItem(
                title    = "Mostrar Próximos Eventos",
                subtitle = "Tarjeta con los eventos próximos de tu agenda",
                checked  = state.showUpcomingEvents,
                onCheckedChange = { viewModel.toggleShowUpcomingEvents(it) }
            )
        }

        // ─── Inteligencia Artificial (Gemini) ──────────────────────────────
        SectionHeader("Inteligencia Artificial (Gemini)")
        SettingsCard {
            SettingsItem(
                icon      = Icons.Default.AutoAwesome,
                iconColor = Violet400,
                title     = "Google Gemini API Key",
                subtitle  = if (state.geminiApiKey.isNotBlank()) "Clave personalizada activa ✓"
                            else "Motor inteligente local activo (toca para añadir clave)",
                trailing  = {
                    OutlinedButton(
                        onClick = { showApiKeyModal = true },
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                        border  = BorderStroke(1.dp, Violet400)
                    ) {
                        Text(
                            if (state.geminiApiKey.isNotBlank()) "Modificar" else "Configurar",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            )
        }

        // ─── Notificaciones ────────────────────────────────────────────────
        SectionHeader("Notificaciones & Recordatorios")
        SettingsCard {
            SwitchSettingItem(
                title    = "Recordatorios Diarios",
                subtitle = "Avisos para registrar entreno y revisar objetivos",
                checked  = state.dailyReminders,
                onCheckedChange = { viewModel.toggleDailyReminders(it) }
            )
        }

        // ─── Fuentes de Datos ──────────────────────────────────────────────
        SectionHeader("Fuentes de Datos")
        SettingsCard {
            SettingsItem(
                icon      = Icons.Default.Favorite,
                iconColor = Rose400,
                title     = "Health Connect / Samsung Health",
                subtitle  = if (state.healthConnectGranted) "Sincronizado — pasos, sueño, calorías"
                            else "Toca para conectar o abrir ajustes de Salud",
                trailing  = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    healthPermissionLauncher.launch(healthPermissions)
                                } catch (e: Exception) {
                                    openHealthConnectSettings(context)
                                }
                            },
                            colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                            border  = BorderStroke(1.dp, Violet400)
                        ) { Text("Conectar", style = MaterialTheme.typography.labelMedium) }

                        IconButton(onClick = { openHealthConnectSettings(context) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Ajustes de Salud", tint = Gray400, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )

            HorizontalDivider(color = Indigo700)

            SettingsItem(
                icon      = Icons.Default.CalendarMonth,
                iconColor = Cyan400,
                title     = "Calendario Local",
                subtitle  = if (state.calendarGranted) "Sincronizado sin internet"
                            else "Permiso READ_CALENDAR requerido",
                trailing  = {
                    if (state.calendarGranted) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Concedido", tint = Emerald400, modifier = Modifier.size(20.dp))
                    } else {
                        OutlinedButton(
                            onClick = { calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                            colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                            border  = BorderStroke(1.dp, Violet400)
                        ) { Text("Permitir", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            )

            HorizontalDivider(color = Indigo700)

            SettingsItem(
                icon      = Icons.Default.FitnessCenter,
                iconColor = Violet400,
                title     = "Symmetry (Entrenamiento)",
                subtitle  = state.symmetryFolderUri?.let { "Carpeta configurada ✓" }
                            ?: "Selecciona carpeta de CSVs",
                trailing  = {
                    OutlinedButton(
                        onClick = { folderPickerLauncher.launch(null) },
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                        border  = BorderStroke(1.dp, Violet400)
                    ) {
                        Text(
                            if (state.symmetryFolderUri != null) "Cambiar" else "Seleccionar",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            )
        }

        // ─── Información ───────────────────────────────────────────────────
        SectionHeader("Información")
        SettingsCard {
            SettingsItem(
                icon      = Icons.Default.Info,
                iconColor = Gray400,
                title     = "Life Dashboard",
                subtitle  = "v1.2 · Panel de Vida Inteligente",
                trailing  = {}
            )
        }
    }

    if (showUserModal) {
        EditUserProfileModal(
            currentName = state.userName,
            onDismiss   = { showUserModal = false },
            onSave      = { newName ->
                viewModel.updateUserName(newName)
                showUserModal = false
            }
        )
    }

    if (showApiKeyModal) {
        EditApiKeyModal(
            currentKey = state.geminiApiKey,
            onDismiss  = { showApiKeyModal = false },
            onSave     = { newKey ->
                viewModel.updateGeminiApiKey(newKey)
                showApiKeyModal = false
                Toast.makeText(context, "Clave de Gemini guardada correctamente", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

private fun openHealthConnectSettings(context: Context) {
    try {
        val intent = Intent("android.health.connect.action.HEALTH_CONNECT_SETTINGS")
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
            context.startActivity(intent)
        } catch (e2: Exception) {
            Toast.makeText(context, "Abre los ajustes de Salud en tu dispositivo", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
private fun UserProfileCard(
    userName: String,
    onOpenProfile: () -> Unit,
    onSyncAll: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Indigo800)
            .border(1.dp, Indigo700, RoundedCornerShape(22.dp))
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier              = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenProfile)
                    .padding(vertical = 4.dp, horizontal = 4.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Violet400.copy(alpha = 0.2f))
                        .border(1.5.dp, Violet300, CircleShape)
                ) {
                    Icon(Icons.Default.Person, contentDescription = "Avatar de usuario", tint = Violet300, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(userName, style = MaterialTheme.typography.titleMedium, color = White, fontWeight = FontWeight.Bold)
                    Text("Toca para editar perfil", style = MaterialTheme.typography.bodySmall, color = Emerald400, fontSize = 11.sp)
                }
            }

            FilledTonalButton(
                onClick = onSyncAll,
                colors  = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Violet400.copy(alpha = 0.2f),
                    contentColor   = Violet300
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape          = RoundedCornerShape(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sync, contentDescription = "Sincronizar", modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Sincronizar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EditUserProfileModal(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var nameInput by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        shape            = RoundedCornerShape(22.dp),
        title = { Text("Perfil de Usuario", color = White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = nameInput,
                    onValueChange = { nameInput = it },
                    label         = { Text("Tu nombre") },
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Violet400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor     = White,
                        unfocusedTextColor   = White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(nameInput) },
                colors  = ButtonDefaults.buttonColors(containerColor = Violet400, contentColor = Indigo900)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Gray400) }
        }
    )
}

@Composable
private fun EditApiKeyModal(
    currentKey: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var keyInput by remember { mutableStateOf(currentKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        shape            = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, contentDescription = null, tint = Violet300, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Google Gemini API Key", color = White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text  = "Puedes conseguir una clave de API gratis en Google AI Studio (aistudio.google.com). Si la dejas en blanco, la app usará el motor de análisis local.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray300
                )

                OutlinedTextField(
                    value         = keyInput,
                    onValueChange = { keyInput = it },
                    label         = { Text("Clave API (AIzaSy...)") },
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Violet400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor     = White,
                        unfocusedTextColor   = White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(keyInput) },
                colors  = ButtonDefaults.buttonColors(containerColor = Violet400, contentColor = Indigo900)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Gray400) }
        }
    )
}

@Composable
private fun ThemeOptionChip(label: String, icon: ImageVector, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Violet400 else Indigo700)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (isSelected) Indigo900 else White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (isSelected) Indigo900 else White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SwitchSettingItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Gray500)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor   = Indigo900,
                checkedTrackColor   = Violet400,
                uncheckedThumbColor = Gray400,
                uncheckedTrackColor = Indigo700
            )
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text  = title,
        style = MaterialTheme.typography.labelLarge,
        color = Gray500
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Indigo800)
            .border(1.dp, Indigo700, RoundedCornerShape(20.dp)),
        content  = content
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconColor.copy(alpha = 0.15f))
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = title,
                style    = MaterialTheme.typography.labelLarge,
                color    = White,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text     = subtitle,
                style    = MaterialTheme.typography.bodySmall,
                color    = Gray500,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}
