package com.lifedashboard.presentation.screen.nutrition

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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.lifedashboard.presentation.theme.*
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
            onInputChange = { viewModel.onTextAiInputChange(it) },
            onAnalyze = { viewModel.analyzeTextAiMeal() },
            onItemChange = { idx, item -> viewModel.updateTextAiResultItem(idx, item) },
            onItemDelete = { idx -> viewModel.removeTextAiResultItem(idx) },
            onConfirm = { items, type -> viewModel.confirmTextAiItems(items, type) },
            onDismiss = { viewModel.closeTextAiDialog() }
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
                val scanner = GmsBarcodeScanning.getClient(context)
                scanner.startScan()
                    .addOnSuccessListener { barcode ->
                        val code = barcode.rawValue
                        if (!code.isNullOrBlank()) {
                            viewModel.onSearchQueryChange(code)
                            Toast.makeText(context, "Código escaneado: $code", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "No se pudo escanear: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            },
            onAddFood = { item, grams ->
                viewModel.addFoodFromCatalog(item, grams, selectedMealTypeForAdd)
                showSearchDialog = false
            },
            onDismiss = { showSearchDialog = false }
        )
    }

    // Diálogo de cálculo de ingesta calórica según peso y objetivo
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
            // ─── Header con selector de fecha ─────────────────────────────
            item {
                NutritionHeader(
                    selectedDate = state.selectedDate,
                    onPreviousDay = { viewModel.selectDate(state.selectedDate.minusDays(1)) },
                    onNextDay = { viewModel.selectDate(state.selectedDate.plusDays(1)) },
                    onTodayClick = { viewModel.selectDate(LocalDate.now()) }
                )
            }

            // ─── Resumen Hero de Macros y Balance Calórico ────────────────
            item {
                NutritionHeroCard(
                    state = state,
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
                    onTextAiClick = {
                        viewModel.openTextAiDialog(section.key)
                    },
                    onCameraClick = {
                        selectedMealTypeForAdd = section.key
                        cameraLauncher.launch(null)
                    },
                    onGalleryClick = {
                        selectedMealTypeForAdd = section.key
                        galleryLauncher.launch("image/*")
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

// ─── Header con selector de fecha ─────────────────────────────────────────────

@Composable
private fun NutritionHeader(
    selectedDate: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onTodayClick: () -> Unit
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

    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier
            .fillMaxWidth()
            .padding(top = 36.dp)
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
}

// ─── Hero Card de Macros y Balance Calórico ───────────────────────────────────

@Composable
private fun NutritionHeroCard(
    state: NutritionUiState,
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
            .clip(RoundedCornerShape(24.dp))
            .background(Indigo800)
            .border(1.dp, Indigo700, RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Cabecera con título y botón de cálculo automático
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Amber400, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text  = "Balance Calórico",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Violet400.copy(alpha = 0.2f))
                        .clickable(onClick = onOpenCalculator)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Calculate, contentDescription = null, tint = Violet300, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Calcular Meta",
                            style = MaterialTheme.typography.labelSmall,
                            color = Violet300,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 3 Columnas claras: Consumidas, Quemadas y Restantes
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                CalorieMetricBox(
                    label = "Consumidas",
                    value = "${state.totalCalories.toInt()} kcal",
                    color = White,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                CalorieMetricBox(
                    label = "Quemadas",
                    value = "${state.activeBurnedCalories.toInt()} kcal",
                    color = Cyan400,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                CalorieMetricBox(
                    label = if (remaining >= 0) "Restantes" else "Superado",
                    value = "${if (remaining >= 0) remaining else -remaining} kcal",
                    color = if (remaining >= 0) Emerald400 else Rose400,
                    modifier = Modifier.weight(1f)
                )
            }

            // Barra de progreso hacia la meta diaria
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Objetivo de ingesta:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Gray400
                    )
                    Text(
                        text = "${state.totalCalories.toInt()} / ${state.targetCalories.toInt()} kcal (${(state.caloriesProgress * 100).toInt()}%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Indigo700)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedCaloriesProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Brush.horizontalGradient(listOf(Violet400, Cyan400)))
                    )
                }
            }

            HorizontalDivider(color = Indigo700, thickness = 1.dp)

            // Desglose de 3 Macronutrientes Principales
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                MacroColumnItem(
                    label       = "Proteínas",
                    currentVal  = state.totalProtein,
                    targetVal   = state.targetProtein,
                    progress    = state.proteinProgress,
                    color       = Violet400,
                    modifier    = Modifier.weight(1f)
                )
                MacroColumnItem(
                    label       = "Carbohidratos",
                    currentVal  = state.totalCarbs,
                    targetVal   = state.targetCarbs,
                    progress    = state.carbsProgress,
                    color       = Cyan400,
                    modifier    = Modifier.weight(1f)
                )
                MacroColumnItem(
                    label       = "Grasas",
                    currentVal  = state.totalFat,
                    targetVal   = state.targetFat,
                    progress    = state.fatProgress,
                    color       = Amber400,
                    modifier    = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CalorieMetricBox(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Indigo900.copy(alpha = 0.7f))
            .padding(vertical = 10.dp, horizontal = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Gray400, fontSize = 10.sp)
            Spacer(Modifier.height(2.dp))
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MacroColumnItem(
    label: String,
    currentVal: Float,
    targetVal: Float,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "macro_progress"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.padding(horizontal = 4.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Gray400)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text       = "${currentVal.toInt()}",
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color      = White
            )
            Text(
                text     = "/${targetVal.toInt()}g",
                style    = MaterialTheme.typography.labelSmall,
                color    = Gray500,
                fontSize = 10.sp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Indigo700)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

// ─── Tarjeta de Sección de Comida ─────────────────────────────────────────────

@Composable
private fun MealSectionCard(
    section: MealSectionType,
    meals: List<MealEntryEntity>,
    onAddClick: () -> Unit,
    onTextAiClick: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onDeleteMeal: (Long) -> Unit
) {
    val sectionKcal = meals.sumOf { it.calories.toDouble() }.toInt()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Indigo800)
            .border(1.dp, Indigo700, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Cabecera de la sección
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = section.iconEmoji, fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text       = section.title,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = White
                    )
                }

                if (sectionKcal > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Indigo700)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text  = "$sectionKcal kcal",
                            style = MaterialTheme.typography.labelSmall,
                            color = Amber400,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Lista de alimentos registrados en esta comida
            if (meals.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    meals.forEach { meal ->
                        MealItemRow(meal = meal, onDelete = { onDeleteMeal(meal.id) })
                    }
                }
            }

            // Botones de acción: Buscar Alimento, Texto IA & Foto con IA
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onAddClick,
                    colors  = ButtonDefaults.buttonColors(containerColor = Indigo700),
                    shape   = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1.15f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Buscar", style = MaterialTheme.typography.labelMedium, color = White, maxLines = 1)
                }

                Button(
                    onClick = onTextAiClick,
                    colors  = ButtonDefaults.buttonColors(containerColor = Violet400.copy(alpha = 0.25f)),
                    shape   = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    border = BorderStroke(1.dp, Violet400.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1.25f)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Violet300, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Texto IA 💬", style = MaterialTheme.typography.labelMedium, color = Violet300, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                IconButton(
                    onClick = onCameraClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Violet400.copy(alpha = 0.25f))
                        .border(1.dp, Violet400.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Foto IA", tint = Violet300, modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = onGalleryClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Indigo700)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Elegir de galería", tint = Gray300, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun MealItemRow(meal: MealEntryEntity, onDelete: () -> Unit) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Indigo900.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        // Thumbnail visual categoría o foto con fallback limpio
        FoodThumbnail(name = meal.name, brand = meal.brand, imageUrl = null, size = 42.dp)
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text       = meal.name,
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f, fill = false)
                )
                if (meal.brand.isNotBlank() && meal.brand != "Básico") {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Violet400.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text     = meal.brand,
                            style    = MaterialTheme.typography.labelSmall,
                            color    = Violet300,
                            fontSize = 9.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${meal.grams.toInt()}g", style = MaterialTheme.typography.labelSmall, color = Gray400, fontSize = 11.sp)
                Text("·", color = Gray500)
                Text("${meal.calories.toInt()} kcal", style = MaterialTheme.typography.labelSmall, color = Amber400, fontSize = 11.sp)
                Text("·", color = Gray500)
                Text("P:${meal.protein.toInt()}g C:${meal.carbs.toInt()}g G:${meal.fat.toInt()}g", style = MaterialTheme.typography.labelSmall, color = Gray400, fontSize = 11.sp)
            }
        }

        IconButton(
            onClick  = onDelete,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = Gray500, modifier = Modifier.size(16.dp))
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
                Text(text = item.name, style = MaterialTheme.typography.bodyMedium, color = White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Text(text = food.brand, style = MaterialTheme.typography.labelSmall, color = Violet300, fontSize = 9.sp)
                                            }
                                            Text("${food.caloriesPer100g.toInt()} kcal/100g", style = MaterialTheme.typography.labelSmall, color = Amber400, fontSize = 11.sp)
                                            Text("·", color = Gray500)
                                            Text("P:${food.proteinPer100g.toInt()}g", style = MaterialTheme.typography.labelSmall, color = Gray400, fontSize = 11.sp)
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
    val emoji = remember(name) {
        val lower = name.lowercase(Locale.ROOT)
        when {
            lower.contains("pan") || lower.contains("tostad") || lower.contains("baguette") || lower.contains("hogaza") -> "🥖"
            lower.contains("huevo") || lower.contains("tortilla") -> "🥚"
            lower.contains("leche") || lower.contains("yogur") || lower.contains("queso") || lower.contains("lacteo") || lower.contains("kéfir") -> "🥛"
            lower.contains("pollo") || lower.contains("pechuga") || lower.contains("pavo") || lower.contains("carne") || lower.contains("ternera") || lower.contains("lomo") || lower.contains("jamon") || lower.contains("jamón") -> "🍗"
            lower.contains("pescado") || lower.contains("atun") || lower.contains("atún") || lower.contains("salmon") || lower.contains("salmón") || lower.contains("merluza") || lower.contains("bacalao") -> "🐟"
            lower.contains("arroz") || lower.contains("avena") || lower.contains("cereal") || lower.contains("pasta") || lower.contains("macarron") || lower.contains("espagueti") -> "🍚"
            lower.contains("manzana") || lower.contains("fruta") || lower.contains("platano") || lower.contains("plátano") || lower.contains("naranja") || lower.contains("fresa") || lower.contains("pera") || lower.contains("uva") -> "🍎"
            lower.contains("ensalada") || lower.contains("tomate") || lower.contains("lechuga") || lower.contains("verdura") || lower.contains("brocoli") || lower.contains("brócoli") || lower.contains("zanahoria") || lower.contains("espinaca") -> "🥗"
            lower.contains("cafe") || lower.contains("café") -> "☕"
            lower.contains("chocolate") || lower.contains("galleta") || lower.contains("dulce") || lower.contains("pastel") || lower.contains("cacao") -> "🍫"
            lower.contains("aceite") || lower.contains("oliva") -> "🫒"
            lower.contains("nuez") || lower.contains("nueces") || lower.contains("almendra") || lower.contains("cacahuete") || lower.contains("anacardo") -> "🥜"
            lower.contains("agua") || lower.contains("refresco") || lower.contains("bebida") || lower.contains("zumo") -> "🥤"
            else -> "🍽️"
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(Indigo700.copy(alpha = 0.6f))
            .border(1.dp, Indigo600.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    ) {
        // Icono o emoji representativo de la categoría (nunca se queda vacío ni roto)
        Text(text = emoji, fontSize = (size.value * 0.45f).sp)

        // Foto real del producto/supermercado encima si está disponible
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
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
                            text = "Gemini 2.0 Flash está calculando alimentos, porciones y calorías...",
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

                    // Botón de confirmación y guardado
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
