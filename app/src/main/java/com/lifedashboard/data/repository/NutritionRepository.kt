package com.lifedashboard.data.repository

import android.graphics.Bitmap
import com.lifedashboard.data.local.dao.MealDao
import com.lifedashboard.data.local.entity.MealEntryEntity
import com.lifedashboard.data.remote.food.DetectedFoodItem
import com.lifedashboard.data.remote.food.FoodAiResult
import com.lifedashboard.data.remote.food.FoodAiService
import com.lifedashboard.data.remote.food.FoodCatalogItem
import com.lifedashboard.data.remote.food.FoodCatalogService
import com.lifedashboard.data.remote.food.FoodSourceCategory
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NutritionRepository @Inject constructor(
    private val mealDao: MealDao,
    private val catalogService: FoodCatalogService,
    private val aiService: FoodAiService,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {

    fun observeMealsForDate(date: LocalDate): Flow<List<MealEntryEntity>> {
        val zone = ZoneId.systemDefault()
        val startMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return mealDao.observeMealsForDay(startMillis, endMillis)
    }

    suspend fun insertMeal(meal: MealEntryEntity): Long {
        val id = mealDao.insertMeal(meal)
        com.lifedashboard.core.worker.WidgetUpdateWorker.updateAllWidgets(context)
        return id
    }

    suspend fun insertMeals(meals: List<MealEntryEntity>) {
        mealDao.insertMeals(meals)
        com.lifedashboard.core.worker.WidgetUpdateWorker.updateAllWidgets(context)
    }

    suspend fun deleteMeal(mealId: Long) {
        mealDao.deleteMealById(mealId)
        com.lifedashboard.core.worker.WidgetUpdateWorker.updateAllWidgets(context)
    }

    suspend fun searchFoods(query: String, category: FoodSourceCategory): List<FoodCatalogItem> {
        return catalogService.searchFoods(query, category)
    }

    suspend fun analyzeFoodPhoto(bitmap: Bitmap, userDescription: String): FoodAiResult {
        return aiService.analyzeFoodImage(bitmap, userDescription)
    }

    suspend fun analyzeFoodText(userDescription: String): FoodAiResult {
        return aiService.analyzeFoodText(userDescription)
    }

    suspend fun getNutritionAdvice(
        consumedCalories: Float,
        targetCalories: Float,
        burnedCalories: Float,
        protein: Float,
        targetProtein: Float,
        carbs: Float,
        targetCarbs: Float,
        fat: Float,
        targetFat: Float,
        goal: String
    ): String {
        return aiService.generateNutritionAdvice(
            consumedCalories = consumedCalories,
            targetCalories = targetCalories,
            burnedCalories = burnedCalories,
            protein = protein,
            targetProtein = targetProtein,
            carbs = carbs,
            targetCarbs = targetCarbs,
            fat = fat,
            targetFat = targetFat,
            goal = goal
        )
    }
}
