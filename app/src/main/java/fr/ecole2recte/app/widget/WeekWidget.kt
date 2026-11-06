package fr.ecole2recte.app.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import java.time.LocalDate
import java.time.LocalDateTime

private fun now(): LocalDateTime = LocalDateTime.now()

class WeekWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(BasicSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadSnapshot(context)
        val colors = WidgetColors.load(context)
        provideContent {
          WidgetTheming(colors) {
            val c = LocalWidgetColors.current
            val height = LocalSize.current.height.value
            Frame("Semaine", "agenda") {
                val today = LocalDate.now()
                val days = snapshot?.let { WidgetLogic.week(it.lessons, today, WidgetLogic.rowsThatFit(height, 36f, 40f, 5)) }.orEmpty()
                when {
                    snapshot == null -> Empty("Ouvre ecole2recte une fois pour remplir ce widget.")
                    days.isEmpty() -> Empty("Aucun cours les prochains jours.")
                    else -> days.forEach { day ->
                        Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(day.label, style = style(c.ink, 15, bold = true), modifier = GlanceModifier.width(62.dp), maxLines = 1)
                            Column(GlanceModifier.defaultWeight()) {
                                Text(
                                    if (day.lessons == 0) "Aucun cours" else "${day.first} → ${day.last}",
                                    style = style(c.muted, 13),
                                    maxLines = 1,
                                )
                            }
                            if (day.canceled > 0) {
                                Chip("${day.canceled} annulé", c.alertSoft, c.alert)
                                Spacer(GlanceModifier.width(4.dp))
                            }
                            if (day.lessons > 0) Chip("${day.lessons} cours", c.accentSoft, c.accent)
                        }
                    }
                }
            }
          }
        }
    }
}

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}

