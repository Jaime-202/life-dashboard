package com.lifedashboard.presentation.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.lifedashboard.presentation.screen.dashboard.DashboardScreen
import com.lifedashboard.presentation.screen.health.HealthScreen
import com.lifedashboard.presentation.screen.settings.SettingsScreen
import com.lifedashboard.presentation.screen.workout.WorkoutScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController    = navController,
        startDestination = Screen.Dashboard.route,
        enterTransition  = {
            fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 6 }
        },
        exitTransition   = {
            fadeOut(tween(150))
        }
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToHealth = { tab ->
                    navController.navigate(Screen.Health.createRoute(tab)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToWorkout = {
                    navController.navigate(Screen.Workout.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToNutrition = {
                    navController.navigate(Screen.Nutrition.route) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Screen.Workout.route) {
            WorkoutScreen(
                onBack = {
                    navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                }
            )
        }
        composable(Screen.Nutrition.route) {
            com.lifedashboard.presentation.screen.nutrition.NutritionScreen(
                onBack = {
                    navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                }
            )
        }
        composable(
            route = Screen.Health.route,
            arguments = listOf(
                navArgument("tab") {
                    type = NavType.StringType
                    defaultValue = "PASOS"
                }
            )
        ) { backStackEntry ->
            val tab = backStackEntry.arguments?.getString("tab") ?: "PASOS"
            HealthScreen(
                initialTabName = tab,
                onBack = {
                    navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
