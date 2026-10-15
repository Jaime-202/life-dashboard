package com.lifedashboard.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object Dashboard  : Screen("dashboard")
    data object Workout    : Screen("workout")
    data object Health     : Screen("health?tab={tab}") {
        const val ROUTE_BASE = "health"
        fun createRoute(tab: String = "PASOS") = "health?tab=$tab"
    }
    data object Settings   : Screen("settings")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon
)

val bottomNavItems = listOf(
    BottomNavItem(
        screen       = Screen.Dashboard,
        label        = "Panel",
        icon         = Icons.Default.Dashboard,
    ),
    BottomNavItem(
        screen       = Screen.Workout,
        label        = "Entreno",
        icon         = Icons.Default.FitnessCenter,
    ),
    BottomNavItem(
        screen       = Screen.Health,
        label        = "Salud",
        icon         = Icons.Default.Favorite,
    ),
    BottomNavItem(
        screen       = Screen.Settings,
        label        = "Config",
        icon         = Icons.Default.Settings,
    ),
)
