package com.lifedashboard.core.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.feature.ExperimentalFeatureAvailabilityApi
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord

/** Estado real de la conexión con Health Connect, tal y como debe mostrarse al usuario. */
enum class HealthConnectStatus { UNAVAILABLE, NOT_GRANTED, PARTIAL, CONNECTED }

/**
 * Punto único para los permisos de Health Connect: lo que se pide en Ajustes y lo que
 * comprueba el worker deben coincidir para que un permiso parcial no rompa la sincronización.
 */
object HealthConnectAccess {
    private const val TAG = "HealthConnectAccess"

    val READ_STEPS = HealthPermission.getReadPermission(StepsRecord::class)
    val READ_DISTANCE = HealthPermission.getReadPermission(DistanceRecord::class)
    val READ_ACTIVE_CALORIES = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
    val READ_SLEEP = HealthPermission.getReadPermission(SleepSessionRecord::class)
    val READ_EXERCISE = HealthPermission.getReadPermission(ExerciseSessionRecord::class)

    val READ_PERMISSIONS: Set<String> = setOf(
        READ_STEPS,
        READ_DISTANCE,
        READ_ACTIVE_CALORIES,
        READ_SLEEP,
        READ_EXERCISE
    )

    fun isAvailable(context: Context): Boolean = try {
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo consultar el estado de Health Connect", e)
        false
    }

    /**
     * Permisos a solicitar. Incluye la lectura en segundo plano cuando el dispositivo la admite:
     * sin ella, Health Connect rechaza las lecturas de los workers periódicos y los widgets
     * solo se actualizarían con la app abierta.
     */
    @OptIn(ExperimentalFeatureAvailabilityApi::class)
    fun permissionsToRequest(context: Context): Set<String> {
        if (!isAvailable(context)) return READ_PERMISSIONS
        return try {
            val background = HealthConnectClient.getOrCreate(context).features.getFeatureStatus(
                HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND
            ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
            if (background) {
                READ_PERMISSIONS + HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
            } else {
                READ_PERMISSIONS
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo consultar la lectura en segundo plano", e)
            READ_PERMISSIONS
        }
    }

    suspend fun status(context: Context): HealthConnectStatus {
        if (!isAvailable(context)) return HealthConnectStatus.UNAVAILABLE
        return try {
            val granted = HealthConnectClient.getOrCreate(context)
                .permissionController.getGrantedPermissions()
            val required = permissionsToRequest(context)
            when {
                required.all { it in granted } -> HealthConnectStatus.CONNECTED
                READ_PERMISSIONS.any { it in granted } -> HealthConnectStatus.PARTIAL
                else -> HealthConnectStatus.NOT_GRANTED
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron consultar los permisos concedidos", e)
            HealthConnectStatus.NOT_GRANTED
        }
    }
}
