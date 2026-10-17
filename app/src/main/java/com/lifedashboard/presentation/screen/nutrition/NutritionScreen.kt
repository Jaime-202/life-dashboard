package com.lifedashboard.presentation.screen.nutrition

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            onDismiss = { viewModel.dismissPhotoDialog() }
        )
    }

    // Diálogo de búsqueda de alimentos y supermercados
    if (showSearchDialog) {
        SearchFoodDialog(
            mealType = selectedMealTypeForAdd,
            query = state.searchQuery,
            selectedCategory = state.selectedCategory,
            results = state.searchResults,
            isSearching = state.isSearching,
            onQueryChange = { viewModel.onSearchQueryChange(it) },
            onCategorySelect = { viewModel.onCategorySelect(it) },
            onAddFood = { item, grams ->
                viewModel.addFoodFromCatalog(item, grams, selectedMealTypeForAdd)
                showSearchDialog = false
            },
            onDismiss = { showSearchDialog = false }
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

            // ─── Resumen Hero de Macros y Calorías ─────────────────────────
            item {
                NutritionHeroCard(state = state)
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
    val isToday = selectedDate == LocalDate.now()
    val dateStr = selectedDate.format(
        DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("es"))
    ).replaceFirstChar { it.uppercase() }

    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier
            .fillMaxWidth()
            .padding(top = 36.dp)
    ) {
        Column {
            Text(
                text       = "Comida & Macros",
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick  = onPreviousDay,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Indigo800)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Día anterior", tint = White, modifier = Modifier.size(16.dp))
            }

            if (!isToday) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Violet400.copy(alpha = 0.2f))
                        .clickable(onClick = onTodayClick)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("HOY", style = MaterialTheme.typography.labelSmall, color = Violet300, fontWeight = FontWeight.Bold)
                }
            }

            IconButton(
                onClick  = onNextDay,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Indigo800)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Día siguiente", tint = White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─── Hero Card de Macros y Calorías ───────────────────────────────────────────

@Composable
private fun NutritionHeroCard(state: NutritionUiState) {
    val remainingCalories = (state.targetCalories - state.totalCalories).toInt()
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
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text  = "Balance Calórico",
                        style = MaterialTheme.typography.labelMedium,
                        color = Gray400
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text       = "${state.totalCalories.toInt()}",
                            style      = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color      = White
                        )
                        Text(
                            text       = " / ${state.targetCalories.toInt()} kcal",
                            style      = MaterialTheme.typography.bodyMedium,
                            color      = Gray400,
                            modifier   = Modifier.padding(bottom = 3.dp, start = 4.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (remainingCalories >= 0) Emerald400.copy(alpha = 0.15f) else Rose400.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (remainingCalories >= 0) "Restan $remainingCalories kcal" else "Superado por ${-remainingCalories} kcal",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (remainingCalories >= 0) Emerald400 else Rose400,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Barra de calorías animada
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
private fun MacroColumnItem(
    label: String,
    currentVal: Float,
    targetVal: Float,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue   = progress,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label         = "${label}_prog"
    )

    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Gray400)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${currentVal.toInt()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = White
            )
            Text(
                text = " / ${targetVal.toInt()}g",
                style = MaterialTheme.typography.labelSmall,
                color = Gray500,
                fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 1.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
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

// ─── Tarjeta de Sección de Comida (Desayuno, Comida, etc.) ───────────────────

@Composable
private fun MealSectionCard(
    section: MealSectionType,
    meals: List<MealEntryEntity>,
    onAddClick: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onDeleteMeal: (Long) -> Unit
) {
    val sectionKcal = meals.sumOf { it.calories.toDouble() }.toInt()
    val sectionProtein = meals.sumOf { it.protein.toDouble() }.toInt()
    val sectionCarbs = meals.sumOf { it.carbs.toDouble() }.toInt()
    val sectionFat = meals.sumOf { it.fat.toDouble() }.toInt()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Indigo800)
            .border(1.dp, Indigo700, RoundedCornerShape(22.dp))
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

                if (meals.isNotEmpty()) {
                    Text(
                        text       = "$sectionKcal kcal",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = Amber400
                    )
                }
            }

            if (meals.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("P: ${sectionProtein}g", style = MaterialTheme.typography.labelSmall, color = Violet300, fontSize = 11.sp)
                    Text("·", color = Gray500)
                    Text("C: ${sectionCarbs}g", style = MaterialTheme.typography.labelSmall, color = Cyan400, fontSize = 11.sp)
                    Text("·", color = Gray500)
                    Text("G: ${sectionFat}g", style = MaterialTheme.typography.labelSmall, color = Amber400, fontSize = 11.sp)
                }
            }

            HorizontalDivider(color = Indigo700, thickness = 1.dp)

            // Lista de alimentos registrados
            if (meals.isEmpty()) {
                Text(
                    text  = "No hay alimentos registrados todavía.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray500
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    meals.forEach { meal ->
                        MealItemRow(meal = meal, onDelete = { onDeleteMeal(meal.id) })
                    }
                }
            }

            // Botones de acción: Buscar Alimento & Foto con IA
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onAddClick,
                    colors  = ButtonDefaults.buttonColors(containerColor = Indigo700),
                    shape   = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Buscar", style = MaterialTheme.typography.labelMedium, color = White)
                }

                Button(
                    onClick = onCameraClick,
                    colors  = ButtonDefaults.buttonColors(containerColor = Violet400.copy(alpha = 0.25f)),
                    shape   = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    border = BorderStroke(1.dp, Violet400.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Violet300, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Foto IA ✨", style = MaterialTheme.typography.labelMedium, color = Violet300, fontWeight = FontWeight.Bold)
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
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = meal.name,
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
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
                            fontSize = 9.sp
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

                // Previsualización de la foto capturada
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Indigo900)
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Foto del plato",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }

                // ─── CAMPO DE DESCRIPCIÓN SOLICITADO POR EL USUARIO ──────────
                if (detectedItems == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text  = "Descripción del plato (recomendado para mayor precisión):",
                            style = MaterialTheme.typography.labelSmall,
                            color = Gray300,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = onDescriptionChange,
                            placeholder = {
                                Text(
                                    "Ej: Pechuga a la plancha con arroz blanco y una cucharada de aceite de oliva...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Gray500
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = Violet400,
                                unfocusedBorderColor = Indigo600,
                                focusedTextColor     = White,
                                unfocusedTextColor   = White,
                                cursorColor          = Violet400
                            ),
                            shape = RoundedCornerShape(14.dp),
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (errorMessage != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Rose400.copy(alpha = 0.15f))
                                .padding(12.dp)
                        ) {
                            Text(text = errorMessage, style = MaterialTheme.typography.bodySmall, color = Rose400)
                        }
                    }

                    Button(
                        onClick = onAnalyze,
                        enabled = !isAnalyzing,
                        colors  = ButtonDefaults.buttonColors(containerColor = Violet400),
                        shape   = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(color = Indigo900, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Analizando plato con Gemini Flash...", color = Indigo900, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Indigo900, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Analizar Plato Ahora ✨", color = Indigo900, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Resultados detectados por la IA
                    if (explanation != null) {
                        Text(text = explanation, style = MaterialTheme.typography.bodySmall, color = Cyan400)
                    }

                    Text(
                        text = "Alimentos detectados (revisa o edita las cantidades):",
                        style = MaterialTheme.typography.labelMedium,
                        color = Gray300,
                        fontWeight = FontWeight.Bold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        detectedItems.forEachIndexed { index, item ->
                            DetectedItemEditor(
                                item = item,
                                onChange = { onItemChange(index, it) },
                                onDelete = { onItemDelete(index) }
                            )
                        }
                    }

                    val totalDetectedKcal = detectedItems.sumOf { it.calories.toDouble() }.toInt()
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Total estimado:", style = MaterialTheme.typography.labelMedium, color = Gray400)
                        Text("$totalDetectedKcal kcal", style = MaterialTheme.typography.titleMedium, color = Amber400, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { onConfirm(detectedItems, mealType) },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Indigo900, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Guardar en $mealType", color = Indigo900, fontWeight = FontWeight.Bold)
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

// ─── Diálogo de Búsqueda de Alimentos y Supermercados ─────────────────────────

@Composable
private fun SearchFoodDialog(
    mealType: String,
    query: String,
    selectedCategory: FoodSourceCategory,
    results: List<FoodCatalogItem>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onCategorySelect: (FoodSourceCategory) -> Unit,
    onAddFood: (FoodCatalogItem, Float) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedItem by remember { mutableStateOf<FoodCatalogItem?>(null) }
    var inputGrams by remember { mutableStateOf("100") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .background(Indigo800)
                .border(1.dp, Indigo700, RoundedCornerShape(24.dp))
                .padding(20.dp)
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

                // Buscador
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Buscar en Mercadona, Carrefour, Día...", color = Gray500) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Violet300) },
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
                    modifier = Modifier.fillMaxWidth()
                )

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
                            Text(text = item.name, style = MaterialTheme.typography.titleSmall, color = White, fontWeight = FontWeight.Bold)
                            Text("Marca: ${item.brand}", style = MaterialTheme.typography.labelSmall, color = Violet300)

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
                    // Lista de resultados
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
                                        text = "No se encontraron alimentos.\nPrueba a buscar pechuga, avena, yogur o productos de Mercadona/Día.",
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
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Indigo900.copy(alpha = 0.7f))
                                    .clickable {
                                        selectedItem = food
                                        inputGrams = food.servingSizeGrams.toInt().toString()
                                    }
                                    .padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = food.name, style = MaterialTheme.typography.bodyMedium, color = White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.height(2.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Indigo700)
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(text = food.brand, style = MaterialTheme.typography.labelSmall, color = Violet300, fontSize = 9.sp)
                                            }
                                            Text("${food.caloriesPer100g.toInt()} kcal/100g", style = MaterialTheme.typography.labelSmall, color = Amber400, fontSize = 11.sp)
                                        }
                                    }
                                    Icon(Icons.Default.AddCircleOutline, contentDescription = "Seleccionar", tint = Violet400, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
