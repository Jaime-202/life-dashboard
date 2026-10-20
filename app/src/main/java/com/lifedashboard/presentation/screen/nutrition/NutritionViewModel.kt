package com.lifedashboard.presentation.screen.nutrition

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifedashboard.data.local.dao.HealthDao
import com.lifedashboard.data.local.dao.WeightDao
import com.lifedashboard.data.local.entity.MealEntryEntity
import com.lifedashboard.data.remote.food.DetectedFoodItem
import com.lifedashboard.data.remote.food.FoodAiResult
import com.lifedashboard.data.remote.food.FoodCatalogItem
import com.lifedashboard.data.remote.food.FoodSourceCategory
import com.lifedashboard.data.remote.food.RealFoodImageResolver
import com.lifedashboard.data.repository.NutritionRepository
import com.lifedashboard.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class MealSectionType(val key: String, val title: String, val iconEmoji: String) {
    DESAYUNO("DESAYUNO", "Desayuno", "☀️"),
    COMIDA("COMIDA", "Comida", "🍲"),
    MERIENDA("MERIENDA", "Merienda", "☕"),
    CENA("CENA", "Cena", "🌙"),
    SNACK("SNACK", "Snacks / Otros", "🍎")
}

data class CalculatedIntake(
    val calories: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val bmr: Float = 0f,
    val tdee: Float = 0f
)

data class NutritionUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val meals: List<MealEntryEntity> = emptyList(),
    val targetCalories: Float = 2200f,
    val targetProtein: Float = 140f,
    val targetCarbs: Float = 250f,
    val targetFat: Float = 70f,
    val activeBurnedCalories: Float = 0f,

    // Perfil y cálculo de ingesta
    val userWeightKg: Float = 75f,
    val userHeightCm: Int = 175,
    val userGoal: String = "PERDIDA_PESO", // "PERDIDA_PESO", "MANTENIMIENTO", "VOLUMEN"
    val showCalculatorDialog: Boolean = false,

    // Búsqueda de alimentos y supermercados
    val searchQuery: String = "",
    val selectedCategory: FoodSourceCategory = FoodSourceCategory.TODOS,
    val searchResults: List<FoodCatalogItem> = emptyList(),
    val isSearching: Boolean = false,

    // Análisis de foto con IA
    val stagedBitmap: Bitmap? = null,
    val stagedDescription: String = "",
    val activeMealType: String = "COMIDA",
    val isAiAnalyzing: Boolean = false,
    val aiResultItems: List<DetectedFoodItem>? = null,
    val aiErrorMessage: String? = null,
    val aiExplanation: String? = null,

    // Registro de comida por texto con IA (sin foto)
    val showTextAiDialog: Boolean = false,
    val textAiInput: String = "",
    val isTextAiAnalyzing: Boolean = false,
    val textAiResultItems: List<DetectedFoodItem>? = null,
    val textAiErrorMessage: String? = null,
    val textAiExplanation: String? = null,
    val textAiMealType: String = "COMIDA",

    // Coach Nutricional IA
    val nutritionAdvice: String? = null,
    val isAdviceLoading: Boolean = false,

    // Escáner de Códigos de Barras y QR
    val scannedFoodItem: FoodCatalogItem? = null,
    val scannedBarcode: String? = null,
    val isScanningBarcode: Boolean = false,
    val scannedUnknownBarcode: String? = null,

    // Estado de Clave Gemini y Prueba de Conexión
    val hasGeminiApiKey: Boolean = false,
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-3.8-flash",
    val isTestingApiKey: Boolean = false,
    val apiKeyTestMessage: String? = null,
    val apiKeyTestSuccess: Boolean? = null
) {
    val totalCalories: Float get() = meals.sumOf { it.calories.toDouble() }.toFloat()
    val totalProtein: Float get() = meals.sumOf { it.protein.toDouble() }.toFloat()
    val totalCarbs: Float get() = meals.sumOf { it.carbs.toDouble() }.toFloat()
    val totalFat: Float get() = meals.sumOf { it.fat.toDouble() }.toFloat()

    val caloriesProgress: Float get() = if (targetCalories > 0) (totalCalories / targetCalories).coerceIn(0f, 1f) else 0f
    val proteinProgress: Float get() = if (targetProtein > 0) (totalProtein / targetProtein).coerceIn(0f, 1f) else 0f
    val carbsProgress: Float get() = if (targetCarbs > 0) (totalCarbs / targetCarbs).coerceIn(0f, 1f) else 0f
    val fatProgress: Float get() = if (targetFat > 0) (totalFat / targetFat).coerceIn(0f, 1f) else 0f

    val remainingCalories: Float get() = targetCalories - totalCalories

    fun mealsForType(type: String): List<MealEntryEntity> = meals.filter { it.mealType == type }
}

@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val repository: NutritionRepository,
    private val userPreferences: UserPreferencesRepository,
    private val weightDao: WeightDao,
    private val healthDao: HealthDao,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(NutritionUiState())
    val uiState: StateFlow<NutritionUiState> = _uiState.asStateFlow()

    private var mealsJob: Job? = null
    private var searchJob: Job? = null

    init {
        // Observar objetivos del usuario y perfil antropométrico
        userPreferences.userGoals
            .onEach { goals ->
                val normalizedGoal = when (goals.primaryGoal) {
                    "GRASA" -> "PERDIDA_PESO"
                    "MUSCULO" -> "VOLUMEN"
                    else -> "MANTENIMIENTO"
                }

                // Si las calorías objetivo están desactualizadas o por debajo de un mínimo realista (< 1200 kcal),
                // calcular automáticamente los valores óptimos basados en peso, altura y objetivo.
                if (goals.caloriesTarget < 1200f) {
                    val intake = calculateIntake(goals.weightKg, goals.heightCm, normalizedGoal)
                    userPreferences.updateCalorieAndMacroTargets(
                        calories = intake.calories,
                        protein = intake.protein,
                        carbs = intake.carbs,
                        fat = intake.fat
                    )
                }

                _uiState.update {
                    it.copy(
                        targetCalories = if (goals.caloriesTarget < 1200f) 1850f else goals.caloriesTarget,
                        targetProtein  = goals.proteinTargetGrams,
                        targetCarbs    = goals.carbsTargetGrams,
                        targetFat      = goals.fatTargetGrams,
                        userWeightKg   = goals.weightKg,
                        userHeightCm   = goals.heightCm,
                        userGoal       = normalizedGoal,
                        hasGeminiApiKey = goals.geminiApiKey.isNotBlank(),
                        geminiApiKey   = goals.geminiApiKey,
                        geminiModel    = goals.geminiModel
                    )
                }
            }
            .launchIn(viewModelScope)

        // Cargar peso más reciente registrado si existe
        viewModelScope.launch(Dispatchers.IO) {
            val latest = weightDao.getLatestWeight()
            if (latest != null && latest.weightKg > 0) {
                _uiState.update { it.copy(userWeightKg = latest.weightKg) }
            }
        }

        // Cargar comidas y gasto calórico de hoy
        observeMealsForDate(LocalDate.now())
        loadHealthDataForDate(LocalDate.now())

        // Cargar catálogo inicial
        viewModelScope.launch {
            executeSearch("", FoodSourceCategory.TODOS)
        }

        // Solicitar consejo inicial del coach
        viewModelScope.launch {
            delay(400)
            requestNutritionAdvice()
        }
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        observeMealsForDate(date)
        loadHealthDataForDate(date)
        viewModelScope.launch {
            delay(300)
            requestNutritionAdvice()
        }
    }

    private fun observeMealsForDate(date: LocalDate) {
        mealsJob?.cancel()
        mealsJob = repository.observeMealsForDate(date)
            .onEach { list ->
                _uiState.update { it.copy(meals = list) }
            }
            .flowOn(Dispatchers.IO)
            .launchIn(viewModelScope)
    }

    private fun loadHealthDataForDate(date: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            val zone = ZoneId.systemDefault()
            val millis = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val health = healthDao.getHealthForDay(millis)
            val burned = health?.activeCalories ?: 0f
            _uiState.update { it.copy(activeBurnedCalories = burned) }
        }
    }

    // ─── Búsqueda de Alimentos y Supermercados ────────────────────────────────

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250) // debounce
            executeSearch(query, _uiState.value.selectedCategory)
        }
    }

    fun onCategorySelect(category: FoodSourceCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            executeSearch(_uiState.value.searchQuery, category)
        }
    }

    private suspend fun executeSearch(query: String, category: FoodSourceCategory) {
        _uiState.update { it.copy(isSearching = true) }
        val results = repository.searchFoods(query, category)
        _uiState.update { it.copy(searchResults = results, isSearching = false) }
    }

    fun onBarcodeDetected(rawCode: String, mealType: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningBarcode = true, activeMealType = mealType) }
            val item = repository.findFoodByBarcode(rawCode)
            if (item != null) {
                _uiState.update {
                    it.copy(
                        isScanningBarcode = false,
                        scannedFoodItem = item,
                        scannedBarcode = rawCode,
                        scannedUnknownBarcode = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isScanningBarcode = false,
                        scannedFoodItem = null,
                        scannedBarcode = rawCode,
                        scannedUnknownBarcode = rawCode
                    )
                }
            }
        }
    }

    fun dismissScannedItem() {
        _uiState.update { it.copy(scannedFoodItem = null, scannedUnknownBarcode = null, isScanningBarcode = false) }
    }

    fun addFoodFromCatalog(
        item: FoodCatalogItem,
        grams: Float,
        mealType: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val factor = grams / 100f
            val date = _uiState.value.selectedDate
            val zone = ZoneId.systemDefault()
            val dateMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()

            val entry = MealEntryEntity(
                dateMillis = dateMillis,
                mealType = mealType,
                name = item.name,
                brand = item.brand,
                grams = grams,
                calories = item.caloriesPer100g * factor,
                protein = item.proteinPer100g * factor,
                carbs = item.carbsPer100g * factor,
                fat = item.fatPer100g * factor,
                imageUrl = RealFoodImageResolver.resolve(item.name, item.brand, item.imageUrl)
            )
            repository.insertMeal(entry)
            delay(200)
            requestNutritionAdvice()
        }
    }

    fun deleteMeal(mealId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMeal(mealId)
            delay(200)
            requestNutritionAdvice()
        }
    }

    fun updateMealGrams(meal: MealEntryEntity, newGrams: Float) {
        if (meal.grams <= 0f || newGrams <= 0f) return
        val ratio = newGrams / meal.grams
        val updated = meal.copy(
            grams = newGrams,
            calories = (meal.calories * ratio).coerceAtLeast(0f),
            protein = (meal.protein * ratio).coerceAtLeast(0f),
            carbs = (meal.carbs * ratio).coerceAtLeast(0f),
            fat = (meal.fat * ratio).coerceAtLeast(0f)
        )
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertMeal(updated)
            delay(200)
            requestNutritionAdvice()
        }
    }

    fun addQuickMeal(
        name: String,
        calories: Float,
        protein: Float,
        carbs: Float,
        fat: Float,
        mealType: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val date = _uiState.value.selectedDate
            val zone = ZoneId.systemDefault()
            val dateMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val cleanName = name.ifBlank { "Comida rápida" }

            val entry = MealEntryEntity(
                dateMillis = dateMillis,
                mealType = mealType,
                name = cleanName,
                brand = "Manual",
                grams = 100f,
                calories = calories.coerceAtLeast(0f),
                protein = protein.coerceAtLeast(0f),
                carbs = carbs.coerceAtLeast(0f),
                fat = fat.coerceAtLeast(0f),
                imageUrl = RealFoodImageResolver.resolve(cleanName, "Manual")
            )
            repository.insertMeal(entry)
            delay(200)
            requestNutritionAdvice()
        }
    }

    fun updateCustomTargets(calories: Float, protein: Float, carbs: Float, fat: Float) {
        viewModelScope.launch {
            userPreferences.updateCalorieAndMacroTargets(
                calories = calories.coerceAtLeast(500f),
                protein = protein.coerceAtLeast(0f),
                carbs = carbs.coerceAtLeast(0f),
                fat = fat.coerceAtLeast(0f)
            )
            com.lifedashboard.core.worker.WidgetUpdateWorker.updateAllWidgets(context)
            _uiState.update {
                it.copy(
                    targetCalories = calories.coerceAtLeast(500f),
                    targetProtein = protein.coerceAtLeast(0f),
                    targetCarbs = carbs.coerceAtLeast(0f),
                    targetFat = fat.coerceAtLeast(0f)
                )
            }
            delay(200)
            requestNutritionAdvice()
        }
    }

    // ─── Cálculo y Ajuste de Ingesta Calórica Basada en Peso y Altura ─────────

    fun setCalculatorDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showCalculatorDialog = visible) }
    }

    fun calculateIntake(weightKg: Float, heightCm: Int, goal: String): CalculatedIntake {
        // Fórmula de Mifflin-St Jeor
        val bmr = (10f * weightKg) + (6.25f * heightCm) - (5f * 25f) + 5f
        val tdee = bmr * 1.35f // Factor de actividad moderada habitual

        val targetCalories = when (goal) {
            "PERDIDA_PESO", "GRASA" -> (tdee - 450f).coerceAtLeast(1400f)
            "VOLUMEN", "MUSCULO" -> tdee + 300f
            else -> tdee
        }

        val protein = (weightKg * 2.0f).coerceIn(90f, 240f)
        val fat = (weightKg * 0.85f).coerceIn(40f, 110f)
        val remainingKcal = targetCalories - (protein * 4f + fat * 9f)
        val carbs = (remainingKcal / 4f).coerceAtLeast(50f)

        return CalculatedIntake(
            calories = targetCalories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            bmr = bmr,
            tdee = tdee
        )
    }

    fun recalculateFromProfile() {
        viewModelScope.launch {
            val goals = userPreferences.userGoals.first()
            val normalizedGoal = when (goals.primaryGoal) {
                "GRASA" -> "PERDIDA_PESO"
                "MUSCULO" -> "VOLUMEN"
                else -> "MANTENIMIENTO"
            }
            val intake = calculateIntake(goals.weightKg, goals.heightCm, normalizedGoal)
            applyCalculatedIntake(intake, goals.weightKg, goals.heightCm, normalizedGoal)
        }
    }

    fun applyCalculatedIntake(intake: CalculatedIntake, weightKg: Float, heightCm: Int, goal: String) {
        viewModelScope.launch {
            userPreferences.updateCalorieAndMacroTargets(
                calories = intake.calories,
                protein = intake.protein,
                carbs = intake.carbs,
                fat = intake.fat
            )
            val currentFitnessLevel = userPreferences.userGoals.first().fitnessLevel
            userPreferences.updateFitnessProfile(
                level = currentFitnessLevel,
                goal = goal,
                weight = weightKg,
                height = heightCm
            )
            com.lifedashboard.core.worker.WidgetUpdateWorker.updateAllWidgets(context)
            _uiState.update {
                it.copy(
                    targetCalories = intake.calories,
                    targetProtein = intake.protein,
                    targetCarbs = intake.carbs,
                    targetFat = intake.fat,
                    userWeightKg = weightKg,
                    userHeightCm = heightCm,
                    userGoal = goal,
                    showCalculatorDialog = false
                )
            }
            delay(200)
            requestNutritionAdvice()
        }
    }

    // ─── Análisis de Foto con Gemini AI ───────────────────────────────────────

    fun setStagedPhoto(bitmap: Bitmap, mealType: String) {
        _uiState.update {
            it.copy(
                stagedBitmap = bitmap,
                stagedDescription = "",
                activeMealType = mealType,
                aiResultItems = null,
                aiErrorMessage = null,
                aiExplanation = null
            )
        }
    }

    fun onStagedDescriptionChange(desc: String) {
        _uiState.update { it.copy(stagedDescription = desc) }
    }

    fun clearStagedPhoto() {
        _uiState.update {
            it.copy(
                stagedBitmap = null,
                stagedDescription = "",
                aiResultItems = null,
                aiErrorMessage = null,
                aiExplanation = null,
                isAiAnalyzing = false
            )
        }
    }

    fun analyzeStagedPhoto() {
        val bitmap = _uiState.value.stagedBitmap ?: return
        val desc = _uiState.value.stagedDescription

        _uiState.update { it.copy(isAiAnalyzing = true, aiErrorMessage = null) }

        viewModelScope.launch {
            when (val result = repository.analyzeFoodPhoto(bitmap, desc)) {
                is FoodAiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isAiAnalyzing = false,
                            aiResultItems = result.items,
                            aiExplanation = result.explanation
                        )
                    }
                }
                is FoodAiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isAiAnalyzing = false,
                            aiErrorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun updateAiResultItem(index: Int, item: DetectedFoodItem) {
        val current = _uiState.value.aiResultItems?.toMutableList() ?: return
        if (index in current.indices) {
            current[index] = item
            _uiState.update { it.copy(aiResultItems = current) }
        }
    }

    fun removeAiResultItem(index: Int) {
        val current = _uiState.value.aiResultItems?.toMutableList() ?: return
        if (index in current.indices) {
            current.removeAt(index)
            _uiState.update { it.copy(aiResultItems = current) }
        }
    }

    fun confirmAiItems(items: List<DetectedFoodItem>, mealType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val date = _uiState.value.selectedDate
            val zone = ZoneId.systemDefault()
            val dateMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()

            val entities = items.map { item ->
                MealEntryEntity(
                    dateMillis = dateMillis,
                    mealType = mealType,
                    name = item.name,
                    brand = "Casero",
                    grams = item.grams,
                    calories = item.calories,
                    protein = item.protein,
                    carbs = item.carbs,
                    fat = item.fat,
                    imageUrl = RealFoodImageResolver.resolve(item.name, "Casero")
                )
            }
            repository.insertMeals(entities)
            clearStagedPhoto()
            delay(200)
            requestNutritionAdvice()
        }
    }

    // ─── Registro de Comida por Texto con IA (Sin Foto) ───────────────────────

    fun openTextAiDialog(mealType: String) {
        _uiState.update {
            it.copy(
                showTextAiDialog = true,
                textAiMealType = mealType,
                textAiInput = "",
                textAiResultItems = null,
                textAiErrorMessage = null,
                textAiExplanation = null,
                isTextAiAnalyzing = false
            )
        }
    }

    fun closeTextAiDialog() {
        _uiState.update { it.copy(showTextAiDialog = false) }
    }

    fun onTextAiInputChange(text: String) {
        _uiState.update { it.copy(textAiInput = text) }
    }

    fun analyzeTextAiMeal() {
        val input = _uiState.value.textAiInput.trim()
        if (input.isBlank()) return

        _uiState.update { it.copy(isTextAiAnalyzing = true, textAiErrorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.analyzeFoodText(input)) {
                is FoodAiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isTextAiAnalyzing = false,
                            textAiResultItems = result.items,
                            textAiExplanation = result.explanation
                        )
                    }
                }
                is FoodAiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isTextAiAnalyzing = false,
                            textAiErrorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun updateTextAiResultItem(index: Int, item: DetectedFoodItem) {
        val current = _uiState.value.textAiResultItems?.toMutableList() ?: return
        if (index in current.indices) {
            current[index] = item
            _uiState.update { it.copy(textAiResultItems = current) }
        }
    }

    fun removeTextAiResultItem(index: Int) {
        val current = _uiState.value.textAiResultItems?.toMutableList() ?: return
        if (index in current.indices) {
            current.removeAt(index)
            _uiState.update { it.copy(textAiResultItems = current) }
        }
    }

    fun confirmTextAiItems(items: List<DetectedFoodItem>, mealType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val date = _uiState.value.selectedDate
            val zone = ZoneId.systemDefault()
            val dateMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()

            val entities = items.map { item ->
                MealEntryEntity(
                    dateMillis = dateMillis,
                    mealType = mealType,
                    name = item.name,
                    brand = "Casero",
                    grams = item.grams,
                    calories = item.calories,
                    protein = item.protein,
                    carbs = item.carbs,
                    fat = item.fat,
                    imageUrl = RealFoodImageResolver.resolve(item.name, "Casero")
                )
            }
            repository.insertMeals(entities)
            closeTextAiDialog()
            delay(200)
            requestNutritionAdvice()
        }
    }

    // ─── Asesor / Coach Nutricional IA ────────────────────────────────────────

    fun requestNutritionAdvice() {
        _uiState.update { it.copy(isAdviceLoading = true) }
        viewModelScope.launch {
            val state = _uiState.value
            val advice = repository.getNutritionAdvice(
                consumedCalories = state.totalCalories,
                targetCalories = state.targetCalories,
                burnedCalories = state.activeBurnedCalories,
                protein = state.totalProtein,
                targetProtein = state.targetProtein,
                carbs = state.totalCarbs,
                targetCarbs = state.targetCarbs,
                fat = state.totalFat,
                targetFat = state.targetFat,
                goal = state.userGoal
            )
            _uiState.update { it.copy(nutritionAdvice = advice, isAdviceLoading = false) }
        }
    }

    // ─── Gestión de Clave Gemini y Prueba de Conexión ─────────────────────────

    fun updateGeminiApiKey(apiKey: String) {
        viewModelScope.launch {
            userPreferences.updateGeminiApiKey(apiKey.trim())
            delay(200)
            requestNutritionAdvice()
        }
    }

    fun updateGeminiModel(model: String) {
        viewModelScope.launch {
            userPreferences.updateGeminiModel(model.trim())
        }
    }

    suspend fun testApiKey(key: String, preferredModel: String? = null): Result<String> {
        return repository.testApiKeyConnection(key, preferredModel ?: _uiState.value.geminiModel)
    }

    fun testApiKeyConnection(key: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingApiKey = true, apiKeyTestMessage = null, apiKeyTestSuccess = null) }
            val result = repository.testApiKeyConnection(key, _uiState.value.geminiModel)
            if (result.isSuccess) {
                val msg = result.getOrNull() ?: "Conexión exitosa"
                _uiState.update { it.copy(isTestingApiKey = false, apiKeyTestMessage = msg, apiKeyTestSuccess = true) }
                onComplete(true, msg)
            } else {
                val err = result.exceptionOrNull()?.message ?: "Error al conectar con Gemini"
                _uiState.update { it.copy(isTestingApiKey = false, apiKeyTestMessage = err, apiKeyTestSuccess = false) }
                onComplete(false, err)
            }
        }
    }
}
