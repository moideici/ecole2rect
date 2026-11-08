package fr.ecole2recte.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.*
import fr.ecole2recte.app.settings.AverageDecimals
import fr.ecole2recte.app.settings.PrivacySelection

class MeanWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(OneByOneSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = (id as? AppWidgetId)?.appWidgetId ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val config = WidgetConfigStore.at(context).read(widgetId)
        val snapshot = instanceSnapshot(context, config)
        val reading = MeanWidgetData.read(snapshot, config, AverageDecimals.read(context), PrivacySelection.mode(context))
        val colors = WidgetColors.load(context)
        provideContent {
            WidgetTheming(colors) {
                val title = reading.title + if (reading.origin == "calculée par l'app" || reading.origin.startsWith("Recalculée")) " · ≈" else ""
                OneByOne("∑", reading.value, title, widgetOpens("grades", reading.subject, config.profile), widgetVariant(), reading.tone,
                    detail = listOf(reading.period, reading.trend, reading.origin).filter { it.isNotBlank() }.joinToString(" · "),
                    badge = snapshot?.let { MeanWidgetData.age(it.updatedAt, System.currentTimeMillis()) }.orEmpty(), configId = widgetId)
            }
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        (glanceId as? AppWidgetId)?.let { WidgetConfigStore.at(context).remove(it.appWidgetId) }
        super.onDelete(context, glanceId)
    }
}

class MeanWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MeanWidget()
}
