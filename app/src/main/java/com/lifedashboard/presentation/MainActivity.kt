package com.lifedashboard.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lifedashboard.core.worker.WidgetUpdateWorker
import com.lifedashboard.core.worker.WorkManagerScheduler
import com.lifedashboard.presentation.navigation.*
import com.lifedashboard.presentation.theme.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var workManagerScheduler: WorkManagerScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Iniciar workers en background
        workManagerScheduler.scheduleAll()
        workManagerScheduler.runHealthSyncNow()

        setContent {
            LifeDashboardTheme {
                LifeDashboardApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) {
            WidgetUpdateWorker.updateAllWidgets(applicationContext)
        }
    }
}

@Composable
private fun LifeDashboardApp() {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route
    val context = LocalContext.current

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* El usuario responde al permiso */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        containerColor = Indigo900,
        bottomBar = {
            LifeDashboardBottomNav(
                currentRoute = currentRoute,
                onNavigate   = { screen ->
                    if (screen == Screen.Dashboard) {
                        navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                    } else {
                        val targetRoute = if (screen is Screen.Health) Screen.Health.createRoute("PASOS") else screen.route
                        navController.navigate(targetRoute) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(Modifier.padding(paddingValues)) {
            NavGraph(navController = navController)
        }
    }
}

@Composable
private fun LifeDashboardBottomNav(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Indigo900)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        NavigationBar(
            containerColor = Indigo800,
            tonalElevation = 0.dp,
            modifier       = Modifier
                .clip(RoundedCornerShape(24.dp))
        ) {
            bottomNavItems.forEach { item ->
                val isSelected = currentRoute?.substringBefore("?") == item.screen.route.substringBefore("?")
                NavigationBarItem(
                    selected = isSelected,
                    onClick  = { onNavigate(item.screen) },
                    icon     = {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.icon,
                            contentDescription = item.label
                        )
                    },
                    label    = {
                        Text(
                            text  = item.label,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors   = NavigationBarItemDefaults.colors(
                        selectedIconColor       = Violet300,
                        selectedTextColor       = Violet300,
                        unselectedIconColor     = Gray500,
                        unselectedTextColor     = Gray500,
                        indicatorColor          = Indigo700
                    )
                )
            }
        }
    }
}
