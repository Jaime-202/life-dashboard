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
    val aiExplanation: String? = null
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
    private val healthDao: HealthDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(NutritionUiState())
    val uiState: StateFlow<NutritionUiState> = _uiState.asStateFlow()

    private var mealsJob: Job? = null
    private var searchJob: Job? = null

    init {
        // Observar objetivos del usuario y perfil antropométrico
        userPreferences.userGoals
            .onEach { goals ->
                _uiState.update {
                    it.copy(
                        targetCalories = goals.caloriesTarget,
                        targetProtein  = goals.proteinTargetGrams,
                        targetCarbs    = goals.carbsTargetGrams,
                        targetFat      = goals.fatTargetGrams,
                        userWeightKg   = goals.weightKg,
                        userHeightCm   = goals.heightCm
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
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        observeMealsForDate(date)
        loadHealthDataForDate(date)
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
                fat = item.fatPer100g * factor
            )
            repository.insertMeal(entry)
        }
    }

    fun deleteMeal(mealId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMeal(mealId)
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
            "PERDIDA_PESO", "GRASA" -> (tdee - 500f).coerceAtLeast(1400f)
            "VOLUMEN", "MUSCULO" -> tdee + 300f
            else -> tdee
        }

        // Distribución óptima de macronutrientes:
        // Proteínas: 2.0 g/kg (protege masa muscular en déficit)
        // Grasas: 0.85 g/kg (salud hormonal)
        // Carbohidratos: el resto calórico
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
                    fat = item.fat
                )
            }
            repository.insertMeals(entities)
            clearStagedPhoto()
        }
    }
}
