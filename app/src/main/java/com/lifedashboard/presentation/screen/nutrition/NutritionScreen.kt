package com.lifedashboard.presentation.screen.nutrition

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.lifedashboard.data.local.entity.MealEntryEntity
import com.lifedashboard.data.remote.food.DetectedFoodItem
import com.lifedashboard.data.remote.food.FoodCatalogItem
import com.lifedashboard.data.remote.food.FoodSourceCategory
import com.lifedashboard.data.remote.food.RealFoodImageResolver
import com.lifedashboard.presentation.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun NutritionScreen(
    onBack: () -> Unit = {},
    viewModel: NutritionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Estado para diálogo de búsqueda y añadido manual
    var showSearchDialog by remember { mutableStateOf(false) }
    var selectedMealTypeForAdd by remember { mutableStateOf("COMIDA") }
    var editingMealItem by remember { mutableStateOf<MealEntryEntity?>(null) }
    var showQuickAddDialog by remember { mutableStateOf(false) }
    var showCustomTargetsDialog by remember { mutableStateOf(false) }
    var showApiKeyModal by remember { mutableStateOf(false) }

    // Launchers para tomar foto con la cámara y seleccionar de la galería
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            viewModel.setStagedPhoto(bitmap, selectedMealTypeForAdd)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        viewModel.setStagedPhoto(bitmap, selectedMealTypeForAdd)
                    }
                }
            } catch (e: Exception) {
                // error abriendo imagen
            }
        }
    }

    // Lanzador directo del escáner de códigos de barras y QR de Google Play Services
    val launchBarcodeScanner: (String) -> Unit = { mealType ->
        selectedMealTypeForAdd = mealType
        val scanner = GmsBarcodeScanning.getClient(context)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val code = barcode.rawValue
                if (!code.isNullOrBlank()) {
                    viewModel.onBarcodeDetected(code, mealType)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "No se pudo escanear: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Diálogo de foto y descripción con IA
    if (state.stagedBitmap != null) {
        PhotoAiDialog(
            bitmap = state.stagedBitmap!!,
            description = state.stagedDescription,
            mealType = state.activeMealType,
            isAnalyzing = state.isAiAnalyzing,
            detectedItems = state.aiResultItems,
            errorMessage = state.aiErrorMessage,
            explanation = state.aiExplanation,
            hasGeminiApiKey = state.hasGeminiApiKey,
            onOpenApiKeyDialog = { showApiKeyModal = true },
            onDescriptionChange = { viewModel.onStagedDescriptionChange(it) },
            onAnalyze = { viewModel.analyzeStagedPhoto() },
            onItemChange = { idx, item -> viewModel.updateAiResultItem(idx, item) },
            onItemDelete = { idx -> viewModel.removeAiResultItem(idx) },
            onConfirm = { items, type -> viewModel.confirmAiItems(items, type) },
            onDismiss = { viewModel.clearStagedPhoto() }
        )
    }

    // Diálogo de registro de comida con IA mediante Texto
    if (state.showTextAiDialog) {
        TextAiMealDialog(
            mealType = state.textAiMealType,
            inputText = state.textAiInput,
            isAnalyzing = state.isTextAiAnalyzing,
            detectedItems = state.textAiResultItems,
            errorMessage = state.textAiErrorMessage,
            explanation = state.textAiExplanation,
            hasGeminiApiKey = state.hasGeminiApiKey,
            onOpenApiKeyDialog = { showApiKeyModal = true },
            onTakePhoto = {
                viewModel.closeTextAiDialog()
                selectedMealTypeForAdd = state.textAiMealType
                cameraLauncher.launch(null)
            },
            onPickGallery = {
                viewModel.closeTextAiDialog()
                selectedMealTypeForAdd = state.textAiMealType
                galleryLauncher.launch("image/*")
            },
            onInputChange = { viewModel.onTextAiInputChange(it) },
            onAnalyze = { viewModel.analyzeTextAiMeal() },
            onItemChange = { idx, item -> viewModel.updateTextAiResultItem(idx, item) },
            onItemDelete = { idx -> viewModel.removeTextAiResultItem(idx) },
            onConfirm = { items, type -> viewModel.confirmTextAiItems(items, type) },
            onDismiss = { viewModel.closeTextAiDialog() }
        )
    }

    // Diálogo de confirmación de alimento detectado por QR o Código de Barras (1-tap add)
    state.scannedFoodItem?.let { food ->
        ScannedFoodDialog(
            food = food,
            initialMealType = state.activeMealType,
            onConfirm = { item, grams, mealType ->
                viewModel.addFoodFromCatalog(item, grams, mealType)
                viewModel.dismissScannedItem()
                Toast.makeText(context, "Añadido: ${item.name} (${grams.toInt()}g)", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { viewModel.dismissScannedItem() }
        )
    }

    // Diálogo para código escaneado desconocido (opción de buscar con IA o añadir manual)
    state.scannedUnknownBarcode?.let { rawCode ->
        UnknownBarcodeDialog(
            barcode = rawCode,
            onSearchAi = {
                viewModel.openTextAiDialog(selectedMealTypeForAdd)
                viewModel.onTextAiInputChange("Producto con código de barras $rawCode")
                viewModel.dismissScannedItem()
            },
            onQuickAdd = {
                showQuickAddDialog = true
                viewModel.dismissScannedItem()
            },
            onDismiss = { viewModel.dismissScannedItem() }
        )
    }

    // Diálogo de búsqueda y escaneo de alimentos
    if (showSearchDialog) {
        SearchFoodDialog(
            mealType = selectedMealTypeForAdd,
            query = state.searchQuery,
            selectedCategory = state.selectedCategory,
            results = state.searchResults,
            isSearching = state.isSearching,
            onQueryChange = { viewModel.onSearchQueryChange(it) },
            onCategorySelect = { viewModel.onCategorySelect(it) },
            onScanBarcode = {
                showSearchDialog = false
                launchBarcodeScanner(selectedMealTypeForAdd)
            },
            onAddFood = { item, grams ->
                viewModel.addFoodFromCatalog(item, grams, selectedMealTypeForAdd)
                showSearchDialog = false
            },
            onDismiss = { showSearchDialog = false }
        )
    }

    // Diálogo de cálculo de ingesta calórica según peso y objetivo (Mifflin-St Jeor)
    if (state.showCalculatorDialog) {
        CaloricCalculatorDialog(
            currentWeight = state.userWeightKg,
            currentHeight = state.userHeightCm,
            currentGoal = state.userGoal,
            onCalculate = { w, h, g -> viewModel.calculateIntake(w, h, g) },
            onApply = { intake, w, h, g ->
                viewModel.applyCalculatedIntake(intake, w, h, g)
                Toast.makeText(context, "Meta calórica actualizada: ${intake.calories.toInt()} kcal", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { viewModel.setCalculatorDialogVisible(false) }
        )
    }

    // Diálogo para escribir objetivos personalizados manualmente (Calorías y Macros)
    if (showCustomTargetsDialog) {
        CustomMacroTargetsDialog(
            currentTargetCalories = state.targetCalories,
            currentTargetProtein = state.targetProtein,
            currentTargetCarbs = state.targetCarbs,
            currentTargetFat = state.targetFat,
            userHeightCm = state.userHeightCm,
            userWeightKg = state.userWeightKg,
            userGoal = state.userGoal,
            onRecalculateFromProfile = {
                viewModel.recalculateFromProfile()
                showCustomTargetsDialog = false
                Toast.makeText(context, "Objetivos recalculados según tu perfil", Toast.LENGTH_SHORT).show()
            },
            onSaveTargets = { cal, p, c, f ->
                viewModel.updateCustomTargets(cal, p, c, f)
                showCustomTargetsDialog = false
                Toast.makeText(context, "Objetivos guardados: ${cal.toInt()} kcal", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showCustomTargetsDialog = false }
        )
    }

    // Diálogo para ajustar porción (gramos) de un alimento ya registrado
    editingMealItem?.let { meal ->
        EditPortionDialog(
            meal = meal,
            onSaveGrams = { newGrams ->
                viewModel.updateMealGrams(meal, newGrams)
                editingMealItem = null
            },
            onDelete = {
                viewModel.deleteMeal(meal.id)
                editingMealItem = null
            },
            onDismiss = { editingMealItem = null }
        )
    }

    // Diálogo de añadido rápido de calorías o comida manual
    if (showQuickAddDialog) {
        QuickAddMealDialog(
            mealType = selectedMealTypeForAdd,
            onAdd = { name, kcal, p, c, f ->
                viewModel.addQuickMeal(name, kcal, p, c, f, selectedMealTypeForAdd)
                showQuickAddDialog = false
            },
            onDismiss = { showQuickAddDialog = false }
        )
    }

    // Diálogo para configurar clave de Gemini API directamente desde Comida
    if (showApiKeyModal) {
        NutritionApiKeyDialog(
            currentKey = state.geminiApiKey,
            currentModel = state.geminiModel,
            onDismiss  = { showApiKeyModal = false },
            onTest     = { key, model -> viewModel.testApiKey(key, model) },
            onSave     = { newKey, newModel ->
                viewModel.updateGeminiApiKey(newKey)
                viewModel.updateGeminiModel(newModel)
                showApiKeyModal = false
                Toast.makeText(context, "Clave y modelo ($newModel) guardados", Toast.LENGTH_SHORT).show()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GradientStart, GradientEnd)))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ─── Header con selector de fecha y estado de IA ─────────────
            item {
                NutritionHeader(
                    selectedDate = state.selectedDate,
                    hasGeminiApiKey = state.hasGeminiApiKey,
                    onPreviousDay = { viewModel.selectDate(state.selectedDate.minusDays(1)) },
                    onNextDay = { viewModel.selectDate(state.selectedDate.plusDays(1)) },
                    onTodayClick = { viewModel.selectDate(LocalDate.now()) },
                    onApiKeyClick = { showApiKeyModal = true }
                )
            }

            // ─── Resumen Hero de Macros y Balance Calórico ────────────────
            item {
                NutritionHeroCard(
                    state = state,
                    onOpenCustomTargets = { showCustomTargetsDialog = true },
                    onOpenCalculator = { viewModel.setCalculatorDialogVisible(true) }
                )
            }

            // ─── Coach Nutricional IA ─────────────────────────────────────
            item {
                NutritionCoachCard(
                    advice = state.nutritionAdvice,
                    isLoading = state.isAdviceLoading,
                    onRefresh = { viewModel.requestNutritionAdvice() }
                )
            }

            // ─── Secciones de Comida ──────────────────────────────────────
            items(MealSectionType.entries.toTypedArray()) { section ->
                MealSectionCard(
                    section = section,
                    meals = state.mealsForType(section.key),
                    onAddClick = {
                        selectedMealTypeForAdd = section.key
                        showSearchDialog = true
                    },
                    onScanBarcodeClick = {
                        launchBarcodeScanner(section.key)
                    },
                    onAiClick = {
                        selectedMealTypeForAdd = section.key
                        viewModel.openTextAiDialog(section.key)
                    },
                    onQuickAddClick = {
                        selectedMealTypeForAdd = section.key
                        showQuickAddDialog = true
                    },
                    onEditMeal = { meal ->
                        editingMealItem = meal
                    },
                    onDeleteMeal = { mealId ->
                        viewModel.deleteMeal(mealId)
                    }
                )
            }

            item {
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

// ─── Header con selector de fecha y estado de IA ─────────────────────────────

@Composable
private fun NutritionHeader(
    selectedDate: LocalDate,
    hasGeminiApiKey: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onTodayClick: () -> Unit,
    onApiKeyClick: () -> Unit
) {
    val today = LocalDate.now()
    val isToday = selectedDate == today

    val dayPillText = when {
        selectedDate == today -> "Hoy"
        selectedDate == today.minusDays(1) -> "Ayer"
        selectedDate == today.plusDays(1) -> "Mañana"
        else -> selectedDate.format(DateTimeFormatter.ofPattern("d MMM", Locale("es"))).replaceFirstChar { it.uppercase() }
    }

    val fullDateStr = selectedDate.format(
        DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("es"))
    ).replaceFirstChar { it.uppercase() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier              = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = "Comida & Nutrición",
                    style      = MaterialTheme.typography.headlineSmall,
                    color      = White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = fullDateStr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray400
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!isToday) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Violet400.copy(alpha = 0.2f))
                            .clickable(onClick = onTodayClick)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("↻ Hoy", style = MaterialTheme.typography.labelSmall, color = Violet300, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(2.dp))
                }

                IconButton(
                    onClick  = onPreviousDay,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Indigo800)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Día anterior", tint = White, modifier = Modifier.size(16.dp))
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Indigo800)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = dayPillText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) Cyan400 else White,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick  = onNextDay,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Indigo800)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Día siguiente", tint = White, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Píldora interactiva de Estado de la Inteligencia Artificial (Google Gemini)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (hasGeminiApiKey) Violet400.copy(alpha = 0.12f) else Indigo800.copy(alpha = 0.7f))
                .border(
                    1.dp,
                    if (hasGeminiApiKey) Violet400.copy(alpha = 0.35f) else Amber400.copy(alpha = 0.35f),
                    RoundedCornerShape(12.dp)
                )
                .clickable(onClick = onApiKeyClick)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (hasGeminiApiKey) Icons.Default.AutoAwesome else Icons.Default.Key,
                        contentDescription = null,
                        tint = if (hasGeminiApiKey) Violet300 else Amber400,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (hasGeminiApiKey) "Gemini 3.8 Flash · Activo" else "Modo Local · Activar Gemini 3.8 IA",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasGeminiApiKey) Violet200 else Amber400,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (hasGeminiApiKey) "Configurar" else "Activar",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasGeminiApiKey) Violet300 else Amber400,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ─── Hero Card de Macros y Balance Calórico Rediseñado ────────────────────────

@Composable
private fun NutritionHeroCard(
    state: NutritionUiState,
    onOpenCustomTargets: () -> Unit,
    onOpenCalculator: () -> Unit
) {
    val remaining = state.remainingCalories.toInt()
    val animatedCaloriesProgress by animateFloatAsState(
        targetValue = state.caloriesProgress,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "calories_progress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Indigo800,
                        Color(0xFF221650),
                        Indigo900
                    )
                )
            )
            .border(1.dp, Violet400.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // ─── Fila 1: Resumen de Calorías y Botones Compactos de Metas ───
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (remaining >= 0) "$remaining" else "+${-remaining}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (remaining >= 0) Emerald400 else Rose400
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (remaining >= 0) "kcal restantes" else "kcal superadas",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (remaining >= 0) Emerald400 else Rose400,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${state.totalCalories.toInt()} de ${state.targetCalories.toInt()} kcal · ${state.activeBurnedCalories.toInt()} quemadas",
                        style = MaterialTheme.typography.labelSmall,
                        color = Gray400,
                        fontSize = 11.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Violet400.copy(alpha = 0.18f))
                            .border(1.dp, Violet400.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .clickable(onClick = onOpenCustomTargets)
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = Violet300, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Metas", style = MaterialTheme.typography.labelSmall, color = Violet300, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Indigo700)
                            .clickable(onClick = onOpenCalculator),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Calculate, contentDescription = "Calcular según perfil", tint = Gray300, modifier = Modifier.size(15.dp))
                    }
                }
            }

            // ─── Fila 2: Barra Fina de Progreso Calórico ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Indigo700)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedCaloriesProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(Violet400, Cyan400)))
                )
            }

            // ─── Fila 3: 3 Tarjetas Compactas de Macronutrientes ───
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CompactMacroCard(
                    title = "Proteína",
                    emoji = "🥩",
                    currentVal = state.totalProtein,
                    targetVal = state.targetProtein,
                    progress = state.proteinProgress,
                    color = Violet400,
                    trackColor = RingTrack,
                    modifier = Modifier.weight(1f)
                )
                CompactMacroCard(
                    title = "Carbos",
                    emoji = "🍞",
                    currentVal = state.totalCarbs,
                    targetVal = state.targetCarbs,
                    progress = state.carbsProgress,
                    color = Cyan400,
                    trackColor = RingTrack,
                    modifier = Modifier.weight(1f)
                )
                CompactMacroCard(
                    title = "Grasas",
                    emoji = "🥑",
                    currentVal = state.totalFat,
                    targetVal = state.targetFat,
                    progress = state.fatProgress,
                    color = Amber400,
                    trackColor = RingTrack,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CompactMacroCard(
    title: String,
    emoji: String,
    currentVal: Float,
    targetVal: Float,
    progress: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "macro_progress"
    )
    val remainingGrams = (targetVal - currentVal).coerceAtLeast(0f).toInt()
    val isComplete = currentVal >= targetVal && targetVal > 0f

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Indigo900.copy(alpha = 0.75f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Fila de Encabezado: Emoji + Título
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(text = emoji, fontSize = 11.sp)
                Spacer(Modifier.width(3.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Anillo Canvas circular de 34dp con % animado
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(34.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = 3.5.dp.toPx()
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = animatedProgress * 360f,
                        useCenter = false,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
            }

            // Gramos: ${current} / ${target}g
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${currentVal.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = White,
                    fontSize = 12.sp
                )
                Text(
                    text = "/${targetVal.toInt()}g",
                    style = MaterialTheme.typography.labelSmall,
                    color = Gray400,
                    fontSize = 9.sp
                )
            }

            // Estado restante
            Text(
                text = if (isComplete) "✓ Listo" else "${remainingGrams}g rest.",
                style = MaterialTheme.typography.labelSmall,
                color = if (isComplete) Emerald400 else Gray400,
                fontSize = 9.sp,
                maxLines = 1
            )
        }
    }
}

// ─── Tarjeta de Sección de Comida Rediseñada ──────────────────────────────────

@Composable
private fun MealSectionCard(
    section: MealSectionType,
    meals: List<MealEntryEntity>,
    onAddClick: () -> Unit,
    onScanBarcodeClick: () -> Unit,
    onAiClick: () -> Unit,
    onQuickAddClick: () -> Unit,
    onEditMeal: (MealEntryEntity) -> Unit,
    onDeleteMeal: (Long) -> Unit
) {
    val sectionKcal = meals.sumOf { it.calories.toDouble() }.toInt()
    val sectionP = meals.sumOf { it.protein.toDouble() }.toInt()
    val sectionC = meals.sumOf { it.carbs.toDouble() }.toInt()
    val sectionG = meals.sumOf { it.fat.toDouble() }.toInt()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Indigo800)
            .border(1.dp, Indigo700, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Cabecera elegante de la sección
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Indigo700),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = section.iconEmoji, fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text       = section.title,
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color      = White,
                            maxLines   = 1
                        )
                        val mealRatio = when (section) {
                            MealSectionType.DESAYUNO -> "~25% ingesta diaria"
                            MealSectionType.COMIDA -> "~35% ingesta diaria"
                            MealSectionType.MERIENDA -> "~15% ingesta diaria"
                            MealSectionType.CENA -> "~25% ingesta diaria"
                            MealSectionType.SNACK -> "Snacks y complementos"
                        }
                        Text(
                            text = mealRatio,
                            style = MaterialTheme.typography.labelSmall,
                            color = Gray400,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }

                if (sectionKcal > 0) {
                    Spacer(Modifier.width(8.dp))
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Indigo700)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text  = "$sectionKcal kcal",
                                style = MaterialTheme.typography.labelSmall,
                                color = Amber400,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Text(
                            text = "P:${sectionP}g · C:${sectionC}g · G:${sectionG}g",
                            style = MaterialTheme.typography.labelSmall,
                            color = Gray400,
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Lista de alimentos registrados en esta comida
            if (meals.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    meals.forEach { meal ->
                        MealItemRow(
                            meal = meal,
                            onEdit = { onEditMeal(meal) },
                            onDelete = { onDeleteMeal(meal.id) }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Indigo900.copy(alpha = 0.4f))
                        .padding(vertical = 12.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "Sin alimentos registrados en ${section.title.lowercase()} aún.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Gray400
                    )
                }
            }

            // ─── Barra de Acciones Rediseñada y Unificada en 1 Sola Línea ───
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Añadir Alimento desde Buscador / Supermercados
                Button(
                    onClick = onAddClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo700),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Violet300, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Añadir",
                        style = MaterialTheme.typography.labelMedium,
                        color = White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // 2. Escanear Código de Barras / QR
                IconButton(
                    onClick = onScanBarcodeClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Cyan400.copy(alpha = 0.15f))
                        .border(1.dp, Cyan400.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.CropFree, contentDescription = "Escanear QR o código de barras", tint = Cyan400, modifier = Modifier.size(18.dp))
                }

                // 3. Registrar con IA (Cámara o Texto)
                IconButton(
                    onClick = onAiClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Violet400.copy(alpha = 0.15f))
                        .border(1.dp, Violet400.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Registrar comida con IA", tint = Violet300, modifier = Modifier.size(18.dp))
                }

                // 4. Añadido Rápido de Calorías
                IconButton(
                    onClick = onQuickAddClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Amber400.copy(alpha = 0.15f))
                        .border(1.dp, Amber400.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = "Añadido rápido de calorías", tint = Amber400, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun MealItemRow(
    meal: MealEntryEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Indigo900.copy(alpha = 0.6f))
            .clickable(onClick = onEdit)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        // Thumbnail visual real con fallback limpio
        FoodThumbnail(name = meal.name, brand = meal.brand, imageUrl = meal.imageUrl, size = 42.dp)
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Línea 1: Nombre del alimento
            Text(
                text       = meal.name,
                style      = MaterialTheme.typography.bodyMedium,
                color      = White,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                softWrap   = false,
                overflow   = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))

            // Línea 2: Marca (badge sutil), gramos y macronutrientes
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (meal.brand.isNotBlank() && meal.brand != "Básico" && meal.brand != "Manual" && meal.brand != "Genérico" && meal.brand != "Casero") {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Violet400.copy(alpha = 0.22f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text       = meal.brand,
                            style      = MaterialTheme.typography.labelSmall,
                            color      = Violet300,
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines   = 1,
                            softWrap   = false
                        )
                    }
                    Text("·", color = Gray600, fontSize = 10.sp)
                }
                Text("${meal.grams.toInt()}g", style = MaterialTheme.typography.labelSmall, color = Gray400, fontSize = 11.sp, maxLines = 1, softWrap = false)
                Text("·", color = Gray600, fontSize = 10.sp)
                Text(
                    text = "P:${meal.protein.toInt()}g C:${meal.carbs.toInt()}g G:${meal.fat.toInt()}g",
                    style = MaterialTheme.typography.labelSmall,
                    color = Gray500,
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Calorías a la derecha, destacadas con botones de acción compactos debajo
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text       = "${meal.calories.toInt()} kcal",
                style      = MaterialTheme.typography.labelMedium,
                color      = Amber400,
                fontWeight = FontWeight.Bold,
                maxLines   = 1,
                softWrap   = false
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick  = onEdit,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar porción", tint = Gray400, modifier = Modifier.size(13.dp))
                }
                IconButton(
                    onClick  = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = Gray500, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

// ─── Diálogo para Editar la Porción en Gramos ──────────────────────────────────

@Composable
private fun EditPortionDialog(
    meal: MealEntryEntity,
    onSaveGrams: (Float) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var gramsInput by remember(meal.grams) { mutableStateOf("${meal.grams.toInt()}") }
    val parsedGrams = gramsInput.toFloatOrNull() ?: meal.grams
    val ratio = if (meal.grams > 0f) parsedGrams / meal.grams else 1f

    val previewKcal = (meal.calories * ratio).coerceAtLeast(0f).toInt()
    val previewProtein = "%.1f".format(Locale.US, (meal.protein * ratio).coerceAtLeast(0f))
    val previewCarbs = "%.1f".format(Locale.US, (meal.carbs * ratio).coerceAtLeast(0f))
    val previewFat = "%.1f".format(Locale.US, (meal.fat * ratio).coerceAtLeast(0f))

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Indigo800)
                .border(1.dp, Violet400.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Header
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        FoodThumbnail(name = meal.name, brand = meal.brand, imageUrl = meal.imageUrl, size = 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = meal.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (meal.brand.isNotBlank() && meal.brand != "Básico") {
                                Text(text = meal.brand, style = MaterialTheme.typography.labelSmall, color = Violet300)
                            }
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Gray400, modifier = Modifier.size(18.dp))
                    }
                }

                Text(
                    text = "Ajusta la cantidad en gramos consumida para recalcular las calorías y macros automáticamente:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray300
                )

                // Input de gramos con teclado numérico
                OutlinedTextField(
                    value = gramsInput,
                    onValueChange = { gramsInput = it },
                    label = { Text("Porción consumida (gramos)", color = Gray400) },
                    trailingIcon = { Text("g", color = Gray400, modifier = Modifier.padding(end = 12.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Chips de ajuste rápido
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("-50", "-20", "+20", "+50").forEach { diffStr ->
                        val diff = diffStr.toInt()
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Indigo700)
                                .clickable {
                                    val current = gramsInput.toIntOrNull() ?: meal.grams.toInt()
                                    val updated = (current + diff).coerceAtLeast(5)
                                    gramsInput = "$updated"
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Text("${diffStr}g", style = MaterialTheme.typography.labelSmall, color = Gray100, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Resumen en vivo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Indigo900)
                        .border(1.dp, Indigo700, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Calorías recalculadas:", style = MaterialTheme.typography.bodySmall, color = Gray300)
                            Text("$previewKcal kcal", style = MaterialTheme.typography.titleMedium, color = Amber400, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(color = Indigo700, thickness = 1.dp)
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Proteínas: ${previewProtein}g", style = MaterialTheme.typography.labelSmall, color = Violet300)
                            Text("Carbos: ${previewCarbs}g", style = MaterialTheme.typography.labelSmall, color = Cyan400)
                            Text("Grasas: ${previewFat}g", style = MaterialTheme.typography.labelSmall, color = Amber400)
                        }
                    }
                }

                // Acciones
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose400),
                        border = BorderStroke(1.dp, Rose400.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Eliminar", color = Rose400)
                    }

                    Button(
                        onClick = {
                            val finalGrams = gramsInput.toFloatOrNull() ?: meal.grams
                            if (finalGrams > 0f) {
                                onSaveGrams(finalGrams)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Violet400),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text("Guardar", color = Indigo900, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── Diálogo de Añadido Rápido de Comida ───────────────────────────────────────

@Composable
private fun QuickAddMealDialog(
    mealType: String,
    onAdd: (name: String, calories: Float, protein: Float, carbs: Float, fat: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    var kcalInput by remember { mutableStateOf("") }
    var proteinInput by remember { mutableStateOf("") }
    var carbsInput by remember { mutableStateOf("") }
    var fatInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Indigo800)
                .border(1.dp, Violet400.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, tint = Amber400, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Añadido Rápido",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Gray400, modifier = Modifier.size(18.dp))
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Violet400.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Destino: $mealType", style = MaterialTheme.typography.labelSmall, color = Violet300, fontWeight = FontWeight.SemiBold)
                }

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Nombre o descripción", color = Gray400) },
                    placeholder = { Text("Ej: Merienda o comida rápida", color = Gray500) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = kcalInput,
                    onValueChange = { kcalInput = it },
                    label = { Text("Calorías (kcal) *", color = Gray400) },
                    trailingIcon = { Text("kcal", color = Amber400, modifier = Modifier.padding(end = 12.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = proteinInput,
                        onValueChange = { proteinInput = it },
                        label = { Text("Proteína", color = Gray400, fontSize = 11.sp) },
                        trailingIcon = { Text("g", color = Violet300, fontSize = 11.sp, modifier = Modifier.padding(end = 6.dp)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Violet400,
                            unfocusedBorderColor = Indigo600,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = carbsInput,
                        onValueChange = { carbsInput = it },
                        label = { Text("Carbos", color = Gray400, fontSize = 11.sp) },
                        trailingIcon = { Text("g", color = Cyan400, fontSize = 11.sp, modifier = Modifier.padding(end = 6.dp)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Violet400,
                            unfocusedBorderColor = Indigo600,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fatInput,
                        onValueChange = { fatInput = it },
                        label = { Text("Grasas", color = Gray400, fontSize = 11.sp) },
                        trailingIcon = { Text("g", color = Amber400, fontSize = 11.sp, modifier = Modifier.padding(end = 6.dp)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Violet400,
                            unfocusedBorderColor = Indigo600,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Gray400),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", color = Gray400)
                    }
                    Button(
                        onClick = {
                            val kcal = kcalInput.toFloatOrNull() ?: 0f
                            val p = proteinInput.toFloatOrNull() ?: 0f
                            val c = carbsInput.toFloatOrNull() ?: 0f
                            val f = fatInput.toFloatOrNull() ?: 0f
                            if (kcal > 0f || p > 0f || c > 0f || f > 0f) {
                                onAdd(nameInput.ifBlank { "Comida rápida" }, kcal, p, c, f)
                            }
                        },
                        enabled = (kcalInput.toFloatOrNull() ?: 0f) > 0f || (proteinInput.toFloatOrNull() ?: 0f) > 0f,
                        colors = ButtonDefaults.buttonColors(containerColor = Violet400),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text("Añadir", color = Indigo900, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── Diálogo para Escribir Objetivos Personalizados ───────────────────────────

@Composable
private fun CustomMacroTargetsDialog(
    currentTargetCalories: Float,
    currentTargetProtein: Float,
    currentTargetCarbs: Float,
    currentTargetFat: Float,
    userHeightCm: Int,
    userWeightKg: Float,
    userGoal: String,
    onRecalculateFromProfile: () -> Unit,
    onSaveTargets: (calories: Float, protein: Float, carbs: Float, fat: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var kcalInput by remember { mutableStateOf("${currentTargetCalories.toInt()}") }
    var proteinInput by remember { mutableStateOf("${currentTargetProtein.toInt()}") }
    var carbsInput by remember { mutableStateOf("${currentTargetCarbs.toInt()}") }
    var fatInput by remember { mutableStateOf("${currentTargetFat.toInt()}") }

    val kcal = kcalInput.toFloatOrNull() ?: currentTargetCalories
    val p = proteinInput.toFloatOrNull() ?: currentTargetProtein
    val c = carbsInput.toFloatOrNull() ?: currentTargetCarbs
    val f = fatInput.toFloatOrNull() ?: currentTargetFat
    val macroKcalSum = (p * 4f) + (c * 4f) + (f * 9f)

    val goalLabel = when (userGoal) {
        "PERDIDA_PESO", "GRASA" -> "Perder grasa"
        "VOLUMEN", "MUSCULO" -> "Ganar músculo"
        else -> "Mantenimiento"
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Indigo800)
                .border(1.dp, Violet400.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Violet300, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Tus Objetivos Diarios",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Gray400, modifier = Modifier.size(18.dp))
                    }
                }

                // ⚡ Botón de 1-tap para auto-calcular según perfil físico
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Violet400.copy(alpha = 0.15f))
                        .border(1.dp, Violet400.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .clickable {
                            val validW = if (userWeightKg > 30f) userWeightKg else 75f
                            val validH = if (userHeightCm > 100) userHeightCm else 175
                            val bmr = (10f * validW) + (6.25f * validH) - (5f * 25f) + 5f
                            val tdee = bmr * 1.35f
                            val calcKcal = when (userGoal) {
                                "PERDIDA_PESO", "GRASA" -> (tdee - 450f).coerceAtLeast(1400f)
                                "VOLUMEN", "MUSCULO" -> tdee + 300f
                                else -> tdee
                            }
                            val calculatedP = (validW * 2.0f).coerceIn(90f, 240f)
                            val calculatedF = (validW * 0.85f).coerceIn(40f, 110f)
                            val rem = calcKcal - (calculatedP * 4f + calculatedF * 9f)
                            val calculatedC = (rem / 4f).coerceAtLeast(50f)

                            kcalInput = "${calcKcal.toInt()}"
                            proteinInput = "${calculatedP.toInt()}"
                            carbsInput = "${calculatedC.toInt()}"
                            fatInput = "${calculatedF.toInt()}"
                        }
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Violet400.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Violet300, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-calcular con mi perfil",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = White
                            )
                            Text(
                                text = "${userHeightCm} cm · ${userWeightKg.toInt()} kg · $goalLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = Violet200,
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            text = "Rellenar",
                            style = MaterialTheme.typography.labelSmall,
                            color = Violet300,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "O escribe directamente tus metas diarias de calorías y macronutrientes:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray300
                )

                // Meta Calórica
                OutlinedTextField(
                    value = kcalInput,
                    onValueChange = { kcalInput = it },
                    label = { Text("Meta Calórica Diaria (kcal)", color = Gray400) },
                    trailingIcon = { Text("kcal", color = Amber400, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Botones rápidos para calorías
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(1800, 2000, 2200, 2500).forEach { preset ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (kcalInput == "$preset") Violet400.copy(alpha = 0.3f) else Indigo700)
                                .border(1.dp, if (kcalInput == "$preset") Violet400 else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable { kcalInput = "$preset" }
                                .padding(vertical = 6.dp)
                        ) {
                            Text("$preset", style = MaterialTheme.typography.labelSmall, color = White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // 3 Macros
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = proteinInput,
                        onValueChange = { proteinInput = it },
                        label = { Text("Proteína (g)", color = Gray400, fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Violet400,
                            unfocusedBorderColor = Indigo600,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = carbsInput,
                        onValueChange = { carbsInput = it },
                        label = { Text("Carbos (g)", color = Gray400, fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Cyan400,
                            unfocusedBorderColor = Indigo600,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fatInput,
                        onValueChange = { fatInput = it },
                        label = { Text("Grasas (g)", color = Gray400, fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Amber400,
                            unfocusedBorderColor = Indigo600,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Validación de equivalencia calórica
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Indigo900)
                        .border(1.dp, Indigo700, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Suma de macros:", style = MaterialTheme.typography.bodySmall, color = Gray400)
                            Text("${macroKcalSum.toInt()} kcal", style = MaterialTheme.typography.bodySmall, color = White, fontWeight = FontWeight.Bold)
                        }
                        val diff = (macroKcalSum - kcal).toInt()
                        Text(
                            text = if (kotlin.math.abs(diff) <= 30) "✓ Los macros cuadran perfectamente con tu meta calórica." else "Nota: La suma de macros difiere en ${kotlin.math.abs(diff)} kcal respecto a tu meta de ${kcal.toInt()} kcal.",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (kotlin.math.abs(diff) <= 30) Emerald400 else Gray400
                        )
                    }
                }

                // Botones
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Gray400),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", color = Gray400)
                    }
                    Button(
                        onClick = {
                            val finalKcal = kcalInput.toFloatOrNull() ?: currentTargetCalories
                            val finalP = proteinInput.toFloatOrNull() ?: currentTargetProtein
                            val finalC = carbsInput.toFloatOrNull() ?: currentTargetCarbs
                            val finalF = fatInput.toFloatOrNull() ?: currentTargetFat
                            onSaveTargets(finalKcal, finalP, finalC, finalF)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Violet400),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text("Guardar Metas", color = Indigo900, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── Diálogo de Foto con IA y Descripción ─────────────────────────────────────

@Composable
private fun PhotoAiDialog(
    bitmap: Bitmap,
    description: String,
    mealType: String,
    isAnalyzing: Boolean,
    detectedItems: List<DetectedFoodItem>?,
    errorMessage: String?,
    explanation: String?,
    hasGeminiApiKey: Boolean,
    onOpenApiKeyDialog: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onItemChange: (Int, DetectedFoodItem) -> Unit,
    onItemDelete: (Int) -> Unit,
    onConfirm: (List<DetectedFoodItem>, String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Indigo800)
                .border(1.dp, Violet400.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Título y botón cerrar
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Violet300, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text       = "Análisis con IA Gemini",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color      = White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Gray400, modifier = Modifier.size(18.dp))
                    }
                }

                // Banner de estado de Gemini
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (hasGeminiApiKey) Violet400.copy(alpha = 0.15f) else Amber400.copy(alpha = 0.15f))
                        .border(1.dp, if (hasGeminiApiKey) Violet400.copy(alpha = 0.35f) else Amber400.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenApiKeyDialog)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = if (hasGeminiApiKey) Violet300 else Amber400, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (hasGeminiApiKey) "Gemini 3.8 Flash Vision Activo" else "Modo Básico · Toca para vincular tu Gemini API Key gratis",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasGeminiApiKey) Violet200 else Amber400,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Vista previa de la foto tomada
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Foto de comida",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                )

                // Campo de descripción opcional
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Descripción opcional (para que la IA acierte mejor):",
                        style = MaterialTheme.typography.labelSmall,
                        color = Gray300
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = onDescriptionChange,
                        placeholder = {
                            Text("Ej: Pollo con arroz y 2 cucharadas de aceite de oliva...", color = Gray500, fontSize = 12.sp)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Violet400,
                            unfocusedBorderColor = Indigo600,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Botón de análisis si aún no se ha analizado
                if (detectedItems == null && !isAnalyzing) {
                    Button(
                        onClick = onAnalyze,
                        colors = ButtonDefaults.buttonColors(containerColor = Violet400),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Indigo900)
                        Spacer(Modifier.width(8.dp))
                        Text("Analizar Plato Ahora", color = Indigo900, fontWeight = FontWeight.Bold)
                    }
                }

                // Indicador de carga
                if (isAnalyzing) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(color = Violet400, strokeWidth = 3.dp)
                        Text(
                            text = "Gemini Vision está identificando los alimentos y macros...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Violet300,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Mensaje de error
                if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Rose400.copy(alpha = 0.2f))
                            .padding(12.dp)
                    ) {
                        Text(text = errorMessage, color = Rose400, style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Lista de alimentos detectados
                if (detectedItems != null) {
                    if (explanation != null) {
                        Text(
                            text = "💡 $explanation",
                            style = MaterialTheme.typography.bodySmall,
                            color = Gray300
                        )
                    }

                    Text(
                        text = "Alimentos detectados (ajusta gramos si es necesario):",
                        style = MaterialTheme.typography.labelMedium,
                        color = White,
                        fontWeight = FontWeight.Bold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        detectedItems.forEachIndexed { idx, item ->
                            DetectedItemEditor(
                                item = item,
                                onChange = { updated -> onItemChange(idx, updated) },
                                onDelete = { onItemDelete(idx) }
                            )
                        }
                    }

                    // Resumen calórico detectado
                    val totalDetectedKcal = detectedItems.sumOf { it.calories.toDouble() }.toInt()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Text("Total estimado:", style = MaterialTheme.typography.bodyMedium, color = Gray300)
                        Text("$totalDetectedKcal kcal", style = MaterialTheme.typography.titleMedium, color = Amber400, fontWeight = FontWeight.Bold)
                    }

                    // Botón de confirmación
                    Button(
                        onClick = { onConfirm(detectedItems, mealType) },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Indigo900)
                        Spacer(Modifier.width(8.dp))
                        Text("Añadir a $mealType", color = Indigo900, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetectedItemEditor(
    item: DetectedFoodItem,
    onChange: (DetectedFoodItem) -> Unit,
    onDelete: () -> Unit
) {
    var gramsText by remember(item.grams) { mutableStateOf("${item.grams.toInt()}") }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Indigo900)
            .border(1.dp, Indigo700, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!item.isGeneric && item.brand.isNotBlank() && !item.brand.equals("genérico", ignoreCase = true) && !item.brand.equals("casero", ignoreCase = true)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Violet400.copy(alpha = 0.22f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = item.brand,
                                style = MaterialTheme.typography.labelSmall,
                                color = Violet300,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    Text("${item.calories.toInt()} kcal", style = MaterialTheme.typography.labelSmall, color = Amber400)
                    Text("·", color = Gray500)
                    Text("P:${item.protein.toInt()}g C:${item.carbs.toInt()}g G:${item.fat.toInt()}g", style = MaterialTheme.typography.labelSmall, color = Gray400)
                }
            }

            Spacer(Modifier.width(8.dp))

            // Campo de gramos
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = gramsText,
                    onValueChange = { newText ->
                        gramsText = newText
                        val newGrams = newText.toFloatOrNull() ?: item.grams
                        if (newGrams > 0 && item.grams > 0) {
                            val ratio = newGrams / item.grams
                            onChange(
                                item.copy(
                                    grams = newGrams,
                                    calories = item.calories * ratio,
                                    protein = item.protein * ratio,
                                    carbs = item.carbs * ratio,
                                    fat = item.fat * ratio
                                )
                            )
                        }
                    },
                    textStyle = MaterialTheme.typography.labelMedium.copy(color = White, textAlign = TextAlign.Center),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    modifier = Modifier.width(65.dp).height(42.dp),
                    singleLine = true
                )
                Text(" g", style = MaterialTheme.typography.labelSmall, color = Gray400)
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Eliminar", tint = Gray500, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ─── Diálogo de Búsqueda de Alimentos, Supermercados y Escáner de Códigos ─────

@Composable
private fun SearchFoodDialog(
    mealType: String,
    query: String,
    selectedCategory: FoodSourceCategory,
    results: List<FoodCatalogItem>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onCategorySelect: (FoodSourceCategory) -> Unit,
    onScanBarcode: () -> Unit,
    onAddFood: (FoodCatalogItem, Float) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedItem by remember { mutableStateOf<FoodCatalogItem?>(null) }
    var inputGrams by remember { mutableStateOf("100") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp))
                .background(Indigo800)
                .border(1.dp, Indigo700, RoundedCornerShape(24.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Header
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text       = "Añadir a $mealType",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = White
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Gray400, modifier = Modifier.size(18.dp))
                    }
                }

                // Buscador con botón de Escáner de Código de Barras
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = { Text("Buscar pan, pechuga, avena...", color = Gray500, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Violet300, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Violet400, strokeWidth = 2.dp)
                            } else if (query.isNotBlank()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Borrar", tint = Gray400, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Violet400,
                            unfocusedBorderColor = Indigo600,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    // Botón para escanear código de barras con la cámara
                    IconButton(
                        onClick = onScanBarcode,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Violet400.copy(alpha = 0.2f))
                            .border(1.dp, Violet400.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    ) {
                        Icon(
                            Icons.Default.CropFree,
                            contentDescription = "Escanear código de barras",
                            tint = Violet300,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Chips de filtro de supermercado
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(FoodSourceCategory.entries.toTypedArray()) { cat ->
                        val isSelected = cat == selectedCategory
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Violet400 else Indigo700)
                                .clickable { onCategorySelect(cat) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) Indigo900 else Gray300,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                HorizontalDivider(color = Indigo700, thickness = 1.dp)

                // Si hay un alimento seleccionado para especificar gramos
                if (selectedItem != null) {
                    val item = selectedItem!!
                    val grams = inputGrams.toFloatOrNull() ?: 100f
                    val factor = grams / 100f
                    val calcKcal = (item.caloriesPer100g * factor).toInt()
                    val calcP = (item.proteinPer100g * factor).toInt()
                    val calcC = (item.carbsPer100g * factor).toInt()
                    val calcG = (item.fatPer100g * factor).toInt()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Indigo900)
                            .border(1.dp, Violet400, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FoodThumbnail(name = item.name, brand = item.brand, imageUrl = item.imageUrl, size = 56.dp)
                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = item.name, style = MaterialTheme.typography.titleSmall, color = White, fontWeight = FontWeight.Bold)
                                    Text("Marca: ${item.brand}", style = MaterialTheme.typography.labelSmall, color = Violet300)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Cantidad:", style = MaterialTheme.typography.bodyMedium, color = Gray300)
                                Spacer(Modifier.width(10.dp))
                                OutlinedTextField(
                                    value = inputGrams,
                                    onValueChange = { inputGrams = it },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Violet400,
                                        unfocusedBorderColor = Indigo600,
                                        focusedTextColor = White,
                                        unfocusedTextColor = White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    modifier = Modifier.width(90.dp).height(46.dp)
                                )
                                Text(" gramos", style = MaterialTheme.typography.bodyMedium, color = Gray400)
                            }

                            // Macros calculados
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("$calcKcal kcal", color = Amber400, fontWeight = FontWeight.Bold)
                                Text("P: ${calcP}g", color = Violet300)
                                Text("C: ${calcC}g", color = Cyan400)
                                Text("G: ${calcG}g", color = Amber400)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { selectedItem = null },
                                    colors = ButtonDefaults.buttonColors(containerColor = Indigo700),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Volver", color = Gray300)
                                }
                                Button(
                                    onClick = { onAddFood(item, grams) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Violet400),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Añadir", color = Indigo900, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    // Lista de resultados con fotos de todos los alimentos
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (results.isEmpty() && !isSearching) {
                            item {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp)
                                ) {
                                    Text(
                                        text = "No se encontraron alimentos.\nPrueba a buscar pan, avena, pechuga o escanear el código de barras.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Gray500,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        items(results) { food ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Indigo900.copy(alpha = 0.7f))
                                    .clickable {
                                        selectedItem = food
                                        inputGrams = food.servingSizeGrams.toInt().toString()
                                    }
                                    .padding(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // FOTO DEL ALIMENTO / PRODUCTO CON FALLBACK INTELIGENTE
                                    FoodThumbnail(name = food.name, brand = food.brand, imageUrl = food.imageUrl, size = 46.dp)
                                    Spacer(Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = food.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = White,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Indigo700)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = food.brand,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Violet300,
                                                    fontSize = 9.sp,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text("${food.caloriesPer100g.toInt()} kcal/100g", style = MaterialTheme.typography.labelSmall, color = Amber400, fontSize = 11.sp, maxLines = 1, softWrap = false)
                                            Text("·", color = Gray500)
                                            Text("P:${food.proteinPer100g.toInt()}g", style = MaterialTheme.typography.labelSmall, color = Gray400, fontSize = 11.sp, maxLines = 1, softWrap = false)
                                        }
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    Icon(Icons.Default.AddCircleOutline, contentDescription = "Seleccionar", tint = Violet400, modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Diálogo de Confirmación de Alimento Detectado por QR / Código de Barras ──

@Composable
private fun ScannedFoodDialog(
    food: FoodCatalogItem,
    initialMealType: String,
    onConfirm: (item: FoodCatalogItem, grams: Float, mealType: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMeal by remember { mutableStateOf(initialMealType) }
    var inputGrams by remember { mutableStateOf(food.servingSizeGrams.toInt().toString()) }

    val parsedGrams = inputGrams.toFloatOrNull() ?: food.servingSizeGrams
    val factor = parsedGrams / 100f
    val calcKcal = (food.caloriesPer100g * factor).toInt()
    val calcP = "%.1f".format(Locale.US, food.proteinPer100g * factor)
    val calcC = "%.1f".format(Locale.US, food.carbsPer100g * factor)
    val calcF = "%.1f".format(Locale.US, food.fatPer100g * factor)

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Indigo800)
                .border(1.dp, Emerald400.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Emerald400.copy(alpha = 0.2f))
                                .padding(6.dp)
                        ) {
                            Icon(Icons.Default.CropFree, contentDescription = null, tint = Emerald400, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "¡Alimento Detectado!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = White
                            )
                            if (!food.barcode.isNullOrBlank()) {
                                Text(
                                    text = "Código: ${food.barcode}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Gray400,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Gray400, modifier = Modifier.size(18.dp))
                    }
                }

                // Info del Producto con Imagen Real
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Indigo900.copy(alpha = 0.7f))
                        .padding(12.dp)
                ) {
                    FoodThumbnail(name = food.name, brand = food.brand, imageUrl = food.imageUrl, size = 60.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = food.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Violet400.copy(alpha = 0.25f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = food.brand,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Violet300,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                text = "${food.caloriesPer100g.toInt()} kcal / 100g",
                                style = MaterialTheme.typography.labelSmall,
                                color = Amber400,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Selector de Comida Destino
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Añadir a:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Gray300,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        MealSectionType.entries.forEach { section ->
                            val isSel = section.key == selectedMeal
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) Violet400 else Indigo700)
                                    .clickable { selectedMeal = section.key }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = section.title.take(4),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSel) Indigo900 else Gray300,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                // Cantidad en gramos
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Porción consumida:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Gray300,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Recomendada: ${food.servingDescription}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Gray400,
                            fontSize = 10.sp
                        )
                    }

                    OutlinedTextField(
                        value = inputGrams,
                        onValueChange = { inputGrams = it },
                        trailingIcon = { Text("g", color = Gray400, modifier = Modifier.padding(end = 12.dp)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Emerald400,
                            unfocusedBorderColor = Indigo600,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Chips de ajuste rápido
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("-50", "-20", "+20", "+50").forEach { diffStr ->
                            val diff = diffStr.toInt()
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Indigo700)
                                    .clickable {
                                        val cur = inputGrams.toIntOrNull() ?: 100
                                        inputGrams = (cur + diff).coerceAtLeast(5).toString()
                                    }
                                    .padding(vertical = 6.dp)
                            ) {
                                Text("${diffStr}g", style = MaterialTheme.typography.labelSmall, color = Gray200, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Resumen de macros calculados
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Indigo900)
                        .border(1.dp, Indigo700, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Aporte calórico:", style = MaterialTheme.typography.bodySmall, color = Gray300)
                            Text("$calcKcal kcal", style = MaterialTheme.typography.titleMedium, color = Amber400, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(color = Indigo700, thickness = 1.dp)
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("P: ${calcP}g", style = MaterialTheme.typography.labelSmall, color = Violet300, fontWeight = FontWeight.SemiBold)
                            Text("C: ${calcC}g", style = MaterialTheme.typography.labelSmall, color = Cyan400, fontWeight = FontWeight.SemiBold)
                            Text("G: ${calcF}g", style = MaterialTheme.typography.labelSmall, color = Amber400, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Botones
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Gray400),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", color = Gray400)
                    }
                    Button(
                        onClick = {
                            val finalGrams = inputGrams.toFloatOrNull() ?: food.servingSizeGrams
                            onConfirm(food, finalGrams, selectedMeal)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Indigo900, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Registrar", color = Indigo900, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── Diálogo para Código de Barras no Encontrado en Local ─────────────────────

@Composable
private fun UnknownBarcodeDialog(
    barcode: String,
    onSearchAi: () -> Unit,
    onQuickAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Indigo800)
                .border(1.dp, Amber400.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CropFree, contentDescription = null, tint = Amber400, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Código Escaneado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = White)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Gray400, modifier = Modifier.size(18.dp))
                    }
                }

                Text(
                    text = "Código: $barcode\n\nNo se encontró este código en la base local de Mercadona o supermercados. ¿Cómo prefieres registrarlo?",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray300
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onSearchAi,
                        colors = ButtonDefaults.buttonColors(containerColor = Violet400),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Indigo900, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Identificar con IA Gemini", color = Indigo900, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onQuickAdd,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber400),
                        border = BorderStroke(1.dp, Amber400.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Añadido Rápido Manual", color = Amber400, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── Modal Calculador de Ingesta Calórica y Macros ────────────────────────────

@Composable
private fun CaloricCalculatorDialog(
    currentWeight: Float,
    currentHeight: Int,
    currentGoal: String,
    onCalculate: (Float, Int, String) -> CalculatedIntake,
    onApply: (CalculatedIntake, Float, Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var weightInput by remember { mutableStateOf(if (currentWeight > 0) "%.1f".format(Locale.US, currentWeight) else "75.0") }
    var heightInput by remember { mutableStateOf(if (currentHeight > 0) "$currentHeight" else "175") }
    var selectedGoal by remember { mutableStateOf(if (currentGoal.isNotBlank()) currentGoal else "PERDIDA_PESO") }

    val parsedWeight = weightInput.toFloatOrNull() ?: 75f
    val parsedHeight = heightInput.toIntOrNull() ?: 175
    val calculated = remember(parsedWeight, parsedHeight, selectedGoal) {
        onCalculate(parsedWeight, parsedHeight, selectedGoal)
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Indigo800)
                .border(1.dp, Violet400.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Header
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Calculate, contentDescription = null, tint = Violet300, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text       = "Calculador de Ingesta",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color      = White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Gray400, modifier = Modifier.size(18.dp))
                    }
                }

                Text(
                    text = "Ajusta tus datos corporales y objetivo para calcular tu ingesta diaria óptima basada en la fórmula científica de Mifflin-St Jeor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray300
                )

                // Campos Peso y Altura
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text("Peso (kg)", color = Gray400, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Violet400,
                            unfocusedBorderColor = Indigo600,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = heightInput,
                        onValueChange = { heightInput = it },
                        label = { Text("Altura (cm)", color = Gray400, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Violet400,
                            unfocusedBorderColor = Indigo600,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Selector de Objetivo
                Text("Objetivo primario:", style = MaterialTheme.typography.labelSmall, color = Gray400)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    GoalChip(
                        title = "📉 Perder Grasa",
                        subtitle = "-500 kcal",
                        isSelected = selectedGoal == "PERDIDA_PESO" || selectedGoal == "GRASA",
                        onClick = { selectedGoal = "PERDIDA_PESO" },
                        modifier = Modifier.weight(1f)
                    )
                    GoalChip(
                        title = "⚖️ Mantenimiento",
                        subtitle = "Equilibrio",
                        isSelected = selectedGoal == "MANTENIMIENTO" || selectedGoal == "SALUD_GENERAL",
                        onClick = { selectedGoal = "MANTENIMIENTO" },
                        modifier = Modifier.weight(1f)
                    )
                    GoalChip(
                        title = "📈 Músculo",
                        subtitle = "+300 kcal",
                        isSelected = selectedGoal == "VOLUMEN" || selectedGoal == "MUSCULO",
                        onClick = { selectedGoal = "VOLUMEN" },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Resultado del cálculo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Indigo900)
                        .border(1.dp, Indigo700, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Ingesta diaria recomendada:", style = MaterialTheme.typography.bodySmall, color = Gray300)
                            Text(
                                text = "${calculated.calories.toInt()} kcal",
                                style = MaterialTheme.typography.titleLarge,
                                color = Amber400,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        HorizontalDivider(color = Indigo700, thickness = 1.dp)

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("P: ${calculated.protein.toInt()}g", color = Violet300, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("C: ${calculated.carbs.toInt()}g", color = Cyan400, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("G: ${calculated.fat.toInt()}g", color = Amber400, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Text(
                            text = "Metabolismo basal: ${calculated.bmr.toInt()} kcal · Gasto estimado: ${calculated.tdee.toInt()} kcal",
                            style = MaterialTheme.typography.labelSmall,
                            color = Gray500,
                            fontSize = 10.sp
                        )
                    }
                }

                // Botón aplicar
                Button(
                    onClick = {
                        onApply(calculated, parsedWeight, parsedHeight, selectedGoal)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Violet400),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Aplicar como mi Meta Diaria", color = Indigo900, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun GoalChip(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Violet400 else Indigo700)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) Indigo900 else White,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.sp
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) Indigo900.copy(alpha = 0.8f) else Gray400,
                fontSize = 9.sp
            )
        }
    }
}

// ─── Componente: Miniatura de Alimento con Fallback Inteligente ────────────────

@Composable
fun FoodThumbnail(
    name: String,
    brand: String? = null,
    imageUrl: String? = null,
    size: androidx.compose.ui.unit.Dp = 46.dp,
    modifier: Modifier = Modifier
) {
    val resolvedUrl = remember(name, brand, imageUrl) {
        RealFoodImageResolver.resolve(name, brand, imageUrl)
    }
    val emoji = remember(name) {
        RealFoodImageResolver.getAccurateEmoji(name)
    }
    val isPackaging = remember(resolvedUrl) {
        resolvedUrl?.contains("prod-mercadona.imgix.net") == true ||
        resolvedUrl?.contains("openfoodfacts") == true
    }

    var imageLoaded by remember(resolvedUrl) { mutableStateOf(false) }
    var imageFailed by remember(resolvedUrl) { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (imageLoaded && isPackaging) Color.White
                else Indigo700.copy(alpha = 0.6f)
            )
            .border(
                1.dp,
                if (imageLoaded && isPackaging) Color(0xFFE2E8F0) else Indigo600.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp)
            )
    ) {
        // Fallback emoji solo si no hay URL o si la carga falló
        if (resolvedUrl.isNullOrBlank() || imageFailed || !imageLoaded) {
            Text(text = emoji, fontSize = (size.value * 0.45f).sp)
        }

        if (!resolvedUrl.isNullOrBlank() && !imageFailed) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(resolvedUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = name,
                onSuccess = { imageLoaded = true },
                onError = { imageFailed = true },
                contentScale = if (isPackaging) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isPackaging) 3.dp else 0.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        }
    }
}

// ─── Componente: Coach Nutricional IA ──────────────────────────────────────────

@Composable
private fun NutritionCoachCard(
    advice: String?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Indigo800,
                        Color(0xFF231448)
                    )
                )
            )
            .border(1.dp, Violet400.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Violet400.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Violet300,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "Coach Nutricional IA",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = White
                        )
                        Text(
                            text = "¿Necesitas comer más o menos? ¿Qué comer?",
                            style = MaterialTheme.typography.labelSmall,
                            color = Gray400,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading,
                    modifier = Modifier.size(34.dp)
                ) {
                    val rotation by animateFloatAsState(
                        targetValue = if (isLoading) 360f else 0f,
                        animationSpec = if (isLoading)
                            infiniteRepeatable(tween(1000), RepeatMode.Restart)
                        else
                            snap(),
                        label = "coach_refresh_anim"
                    )
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Actualizar recomendación",
                        tint = if (isLoading) Violet300 else Gray400,
                        modifier = Modifier.size(18.dp).rotate(rotation)
                    )
                }
            }

            AnimatedContent(
                targetState = isLoading to advice,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label = "coach_advice_anim"
            ) { (loading, text) ->
                if (loading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Violet400, strokeWidth = 2.dp)
                        Text(
                            text = "Calculando tu balance calórico y recomendación...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Violet300
                        )
                    }
                } else if (!text.isNullOrBlank()) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Gray100,
                        lineHeight = 22.sp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "Toca para analizar tu ingesta y recibir consejo",
                            style = MaterialTheme.typography.bodySmall,
                            color = Gray400,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onRefresh,
                            colors = ButtonDefaults.buttonColors(containerColor = Violet400),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Consultar IA", color = Indigo900, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ─── Diálogo de Registro de Comida con IA mediante Texto ───────────────────────

@Composable
private fun TextAiMealDialog(
    mealType: String,
    inputText: String,
    isAnalyzing: Boolean,
    detectedItems: List<DetectedFoodItem>?,
    errorMessage: String?,
    explanation: String?,
    hasGeminiApiKey: Boolean,
    onOpenApiKeyDialog: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onInputChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onItemChange: (Int, DetectedFoodItem) -> Unit,
    onItemDelete: (Int) -> Unit,
    onConfirm: (List<DetectedFoodItem>, String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Indigo800)
                .border(1.dp, Violet400.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Cabecera
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Violet300, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Registrar con IA Texto",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Gray400, modifier = Modifier.size(18.dp))
                    }
                }

                // Banner de estado de Gemini
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (hasGeminiApiKey) Violet400.copy(alpha = 0.15f) else Amber400.copy(alpha = 0.15f))
                        .border(1.dp, if (hasGeminiApiKey) Violet400.copy(alpha = 0.35f) else Amber400.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenApiKeyDialog)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = if (hasGeminiApiKey) Violet300 else Amber400, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (hasGeminiApiKey) "Gemini 3.8 Flash Conectado · IA Activa" else "Motor Local Básico · Toca para vincular tu Gemini API Key gratis",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasGeminiApiKey) Violet200 else Amber400,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Violet400.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Destino: $mealType",
                            style = MaterialTheme.typography.labelSmall,
                            color = Violet300,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = onTakePhoto,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Gray300),
                            border = BorderStroke(1.dp, Indigo600),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Foto", style = MaterialTheme.typography.labelSmall)
                        }

                        OutlinedButton(
                            onClick = onPickGallery,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Gray300),
                            border = BorderStroke(1.dp, Indigo600),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Galería", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Campo de entrada de texto
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Describe lo que has comido en lenguaje natural:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Gray300
                    )
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        placeholder = {
                            Text(
                                "Ej: 2 tostadas de pan integral con aceite de oliva, 50g de jamón serrano y un café con leche",
                                color = Gray500,
                                fontSize = 12.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Violet400,
                            unfocusedBorderColor = Indigo600,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Botón de análisis
                if (detectedItems == null && !isAnalyzing) {
                    Button(
                        onClick = onAnalyze,
                        enabled = inputText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Violet400),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Indigo900)
                        Spacer(Modifier.width(8.dp))
                        Text("Calcular Alimentos y Macros", color = Indigo900, fontWeight = FontWeight.Bold)
                    }
                }

                // Indicador de análisis en curso
                if (isAnalyzing) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(color = Violet400, strokeWidth = 3.dp)
                        Text(
                            text = if (hasGeminiApiKey) "Gemini 3.8 Flash está calculando alimentos, porciones y calorías..." else "Analizando alimentos y macros con el motor local...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Violet300,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Mensaje de error
                if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Rose400.copy(alpha = 0.2f))
                            .padding(12.dp)
                    ) {
                        Text(text = errorMessage, color = Rose400, style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Lista de alimentos detectados
                if (detectedItems != null) {
                    if (explanation != null) {
                        Text(
                            text = "💡 $explanation",
                            style = MaterialTheme.typography.bodySmall,
                            color = Gray300
                        )
                    }

                    Text(
                        text = "Alimentos calculados (ajusta gramos si es necesario):",
                        style = MaterialTheme.typography.labelMedium,
                        color = White,
                        fontWeight = FontWeight.Bold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        detectedItems.forEachIndexed { idx, item ->
                            DetectedItemEditor(
                                item = item,
                                onChange = { updated -> onItemChange(idx, updated) },
                                onDelete = { onItemDelete(idx) }
                            )
                        }
                    }

                    // Resumen calórico
                    val totalDetectedKcal = detectedItems.sumOf { it.calories.toDouble() }.toInt()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Text("Total estimado:", style = MaterialTheme.typography.bodyMedium, color = Gray300)
                        Text("$totalDetectedKcal kcal", style = MaterialTheme.typography.titleMedium, color = Amber400, fontWeight = FontWeight.Bold)
                    }

                    // Botones de acción
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onAnalyze,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                            border = BorderStroke(1.dp, Violet400.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Recalcular", maxLines = 1)
                        }
                        Button(
                            onClick = { onConfirm(detectedItems, mealType) },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(2f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Indigo900)
                            Spacer(Modifier.width(6.dp))
                            Text("Añadir a $mealType", color = Indigo900, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// ─── Diálogo de Configuración de Clave Gemini desde Nutrición ──────────────────

@Composable
private fun NutritionApiKeyDialog(
    currentKey: String,
    currentModel: String = "gemini-3.8-flash",
    onDismiss: () -> Unit,
    onTest: (suspend (String, String) -> Result<String>),
    onSave: (String, String) -> Unit
) {
    var keyInput by remember { mutableStateOf(currentKey) }
    var selectedModel by remember { mutableStateOf(if (currentModel.isBlank()) "gemini-3.8-flash" else currentModel) }
    var customModelInput by remember { mutableStateOf(if (currentModel !in listOf("gemini-3.8-flash", "gemini-3.8-flash-lite", "gemini-3.7-flash", "auto")) currentModel else "") }
    var isCustom by remember { mutableStateOf(currentModel !in listOf("gemini-3.8-flash", "gemini-3.8-flash-lite", "gemini-3.7-flash", "auto") && currentModel.isNotBlank()) }
    var isTesting by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var testIsSuccess by remember { mutableStateOf<Boolean?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val activeModelToUse = if (isCustom && customModelInput.isNotBlank()) customModelInput.trim() else selectedModel

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Indigo800)
                .border(1.dp, Violet400.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = Violet300, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Google Gemini API Key",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Gray400, modifier = Modifier.size(18.dp))
                    }
                }

                Text(
                    text = "Consigue una clave gratis en Google AI Studio (aistudio.google.com). Compatible con Gemini 3.8 Flash, con selección de modelos y auto-detección dinámica para modelos futuros.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray300
                )

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Abre aistudio.google.com en tu navegador", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan400),
                    border = BorderStroke(1.dp, Cyan400.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Obtener Clave Gratis en Google AI Studio", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = {
                        keyInput = it
                        testResultText = null
                        testIsSuccess = null
                    },
                    label = { Text("Clave API de Gemini", color = Gray400) },
                    placeholder = { Text("AIzaSy...", color = Gray500) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet400, unfocusedBorderColor = Indigo600,
                        focusedTextColor = White, unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Selector de Modelo Gemini (Gemini 3.8 Flash, 3.8 Lite, 3.7, Auto, Otro)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Modelo de IA Gemini:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Gray300,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        listOf(
                            "gemini-3.8-flash" to "⚡ 3.8 Flash",
                            "gemini-3.8-flash-lite" to "🚀 3.8 Lite",
                            "gemini-3.7-flash" to "✨ 3.7",
                            "auto" to "🎯 Auto"
                        ).forEach { (modelKey, label) ->
                            val isSelected = !isCustom && selectedModel == modelKey
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Violet400.copy(alpha = 0.25f) else Indigo900.copy(alpha = 0.6f))
                                    .border(1.dp, if (isSelected) Violet400 else Indigo700, RoundedCornerShape(8.dp))
                                    .clickable {
                                        isCustom = false
                                        selectedModel = modelKey
                                        testResultText = null
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Violet200 else Gray400,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCustom) Violet400.copy(alpha = 0.25f) else Indigo900.copy(alpha = 0.6f))
                                .border(1.dp, if (isCustom) Violet400 else Indigo700, RoundedCornerShape(8.dp))
                                .clickable {
                                    isCustom = true
                                    testResultText = null
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "✍️ Otro",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = if (isCustom) Violet200 else Gray400,
                                fontWeight = if (isCustom) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                if (isCustom) {
                    OutlinedTextField(
                        value = customModelInput,
                        onValueChange = {
                            customModelInput = it
                            testResultText = null
                        },
                        label = { Text("Nombre del modelo (ej: gemini-3.9-flash)", color = Gray400, fontSize = 11.sp) },
                        placeholder = { Text("gemini-3.8-flash", color = Gray500) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Cyan400, unfocusedBorderColor = Indigo600,
                            focusedTextColor = White, unfocusedTextColor = White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            if (keyInput.isBlank()) {
                                Toast.makeText(context, "Escribe o pega una clave primero", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isTesting = true
                            testResultText = null
                            coroutineScope.launch {
                                val res = onTest(keyInput.trim(), activeModelToUse)
                                isTesting = false
                                if (res.isSuccess) {
                                    testIsSuccess = true
                                    testResultText = res.getOrNull() ?: "Conexión exitosa"
                                } else {
                                    testIsSuccess = false
                                    testResultText = res.exceptionOrNull()?.message ?: "Error al conectar"
                                }
                            }
                        },
                        enabled = !isTesting && keyInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo700, contentColor = White),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(color = Violet300, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Comprobando...", style = MaterialTheme.typography.labelSmall)
                        } else {
                            Icon(Icons.Default.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(14.dp), tint = Violet300)
                            Spacer(Modifier.width(6.dp))
                            Text("Probar Clave", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (keyInput.isNotBlank()) {
                        TextButton(
                            onClick = {
                                keyInput = ""
                                testResultText = null
                                testIsSuccess = null
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Borrar", style = MaterialTheme.typography.labelSmall, color = Rose400)
                        }
                    }
                }

                testResultText?.let { msg ->
                    val isSuccess = testIsSuccess == true
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSuccess) Emerald400.copy(alpha = 0.2f) else Rose400.copy(alpha = 0.2f))
                            .border(1.dp, if (isSuccess) Emerald400.copy(alpha = 0.5f) else Rose400.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = (if (isSuccess) "✅ " else "❌ ") + msg,
                            color = if (isSuccess) Emerald400 else Rose400,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, Indigo600),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", color = Gray400)
                    }

                    Button(
                        onClick = { onSave(keyInput, activeModelToUse) },
                        colors = ButtonDefaults.buttonColors(containerColor = Violet400, contentColor = Indigo900),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
