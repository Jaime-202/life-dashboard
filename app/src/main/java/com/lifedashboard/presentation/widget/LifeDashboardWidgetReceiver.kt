package com.lifedashboard.presentation.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Receiver del widget. Se dispara:
 * 1. Manualmente desde HealthConnectSyncWorker al terminar.
 * 2. Por el sistema en el intervalo de AppWidgetProviderInfo (cada 30 min).
 *
 * Delega a LifeDashboardWidget.update() que lee Room directamente.
 */
class LifeDashboardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LifeDashboardWidget()
}
