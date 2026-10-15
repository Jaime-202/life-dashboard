package com.lifedashboard.presentation.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
        composable(Screen.Dashboard.route) { DashboardScreen() }
        composable(Screen.Workout.route)   { WorkoutScreen() }
        composable(Screen.Health.route)    { HealthScreen() }
        composable(Screen.Settings.route)  { SettingsScreen() }
    }
}
