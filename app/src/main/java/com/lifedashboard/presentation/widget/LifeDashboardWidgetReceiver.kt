package com.lifedashboard.presentation.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 1. Receiver para el widget 1x1 (Tile de pasos)
 */
class LifeDashboard1x1WidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LifeDashboard1x1Widget()
}

/**
 * 2. Receiver para el widget 1x2 / 2x1 (Cápsula de pulso)
 */
class LifeDashboard1x2WidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LifeDashboard1x2Widget()
}

/**
 * 3. Receiver para el widget 2x2 (Cuadrante biométrico)
 */
class LifeDashboard2x2WidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LifeDashboard2x2Widget()
}

/**
 * 4. Receiver para el widget 4x2 (Resumen amplio completo)
 */
class LifeDashboardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LifeDashboardWidget()
}

/** Alias para compatibilidad previa */
typealias LifeDashboardCompactWidgetReceiver = LifeDashboard2x2WidgetReceiver
