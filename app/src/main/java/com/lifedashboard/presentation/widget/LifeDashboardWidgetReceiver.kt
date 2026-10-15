package com.lifedashboard.presentation.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Receiver para el widget completo / amplio (4x2 o responsive).
 */
class LifeDashboardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LifeDashboardWidget()
}

/**
 * Receiver para el widget compacto / mini (2x2 y pantalla de bloqueo).
 */
class LifeDashboardCompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LifeDashboardCompactWidget()
}
