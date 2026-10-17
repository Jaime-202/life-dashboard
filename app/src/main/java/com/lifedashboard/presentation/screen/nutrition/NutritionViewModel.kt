package com.lifedashboard.presentation.screen.nutrition

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class NutritionUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val meals: List<MealEntryEntity> = emptyList(),
    val targetCalories: Float = 2200f,
    val targetProtein: Float = 140f,
    val targetCarbs: Float = 250f,
    val targetFat: Float = 70f,

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

    fun mealsForType(type: String): List<MealEntryEntity> = meals.filter { it.mealType == type }
}

@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val repository: NutritionRepository,
    private val userPreferences: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NutritionUiState())
    val uiState: StateFlow<NutritionUiState> = _uiState.asStateFlow()

    private var mealsJob: Job? = null
    private var searchJob: Job? = null

    init {
        // Observar objetivos del usuario
        userPreferences.userGoals
            .onEach { goals ->
                _uiState.update {
                    it.copy(
                        targetCalories = goals.caloriesTarget,
                        targetProtein  = goals.proteinTargetGrams,
                        targetCarbs    = goals.carbsTargetGrams,
                        targetFat      = goals.fatTargetGrams
                    )
                }
            }
            .launchIn(viewModelScope)

        // Cargar comidas de hoy
        observeMealsForDate(LocalDate.now())

        // Cargar catálogo inicial de básicos
        viewModelScope.launch {
            executeSearch("", FoodSourceCategory.TODOS)
        }
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        observeMealsForDate(date)
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

    // ─── Búsqueda de Alimentos y Supermercados ────────────────────────────────

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // debounce
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
            val dateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val entity = MealEntryEntity(
                dateMillis = dateMillis,
                mealType   = mealType,
                name       = item.name,
                brand      = item.brand,
                grams      = grams,
                calories   = item.caloriesPer100g * factor,
                protein    = item.proteinPer100g * factor,
                carbs      = item.carbsPer100g * factor,
                fat        = item.fatPer100g * factor
            )
            repository.insertMeal(entity)
        }
    }

    fun deleteMeal(mealId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMeal(mealId)
        }
    }

    // ─── Foto con IA y Descripción ────────────────────────────────────────────

    fun setStagedPhoto(bitmap: Bitmap?, mealType: String) {
        _uiState.update {
            it.copy(
                stagedBitmap = bitmap,
                activeMealType = mealType,
                stagedDescription = "",
                aiResultItems = null,
                aiErrorMessage = null,
                aiExplanation = null
            )
        }
    }

    fun onStagedDescriptionChange(desc: String) {
        _uiState.update { it.copy(stagedDescription = desc) }
    }

    fun dismissPhotoDialog() {
        _uiState.update {
            it.copy(
                stagedBitmap = null,
                stagedDescription = "",
                isAiAnalyzing = false,
                aiResultItems = null,
                aiErrorMessage = null,
                aiExplanation = null
            )
        }
    }

    fun analyzeStagedPhoto() {
        val bitmap = _uiState.value.stagedBitmap ?: return
        val description = _uiState.value.stagedDescription

        viewModelScope.launch {
            _uiState.update { it.copy(isAiAnalyzing = true, aiErrorMessage = null) }

            when (val result = repository.analyzeFoodPhoto(bitmap, description)) {
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

    fun updateAiResultItem(index: Int, updatedItem: DetectedFoodItem) {
        val current = _uiState.value.aiResultItems?.toMutableList() ?: return
        if (index in current.indices) {
            current[index] = updatedItem
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
            val dateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val entities = items.map { item ->
                MealEntryEntity(
                    dateMillis = dateMillis,
                    mealType   = mealType,
                    name       = item.name,
                    brand      = "IA Reconocimiento",
                    grams      = item.grams,
                    calories   = item.calories,
                    protein    = item.protein,
                    carbs      = item.carbs,
                    fat        = item.fat,
                    notes      = _uiState.value.stagedDescription
                )
            }
            repository.insertMeals(entities)
            dismissPhotoDialog()
        }
    }
}
