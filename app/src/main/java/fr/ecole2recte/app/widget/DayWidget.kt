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
import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.domain.Subject
import fr.ecole2recte.app.ui.schedule.buildDayTimeline
import fr.ecole2recte.app.ui.schedule.formatMinutes
import java.time.LocalDate
import java.time.LocalDateTime

private fun now(): LocalDateTime = LocalDateTime.now()

class DayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(BasicSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadSnapshot(context)
        val colors = WidgetColors.load(context)
        provideContent {
          WidgetTheming(colors) {
            val c = LocalWidgetColors.current
            val height = LocalSize.current.height.value
            val today = LocalDate.now()
            val dayName = today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.FRENCH)
            Frame("$dayName ${today.dayOfMonth}", "agenda") {
                val slots = snapshot?.let { WidgetLogic.today(it.lessons, today) }.orEmpty()
                when {
                    snapshot == null -> Empty("Ouvre ecole2recte une fois pour remplir ce widget.")
                    slots.isEmpty() -> Empty("Pas de cours aujourd'hui.")
                    else -> {
                        val entries = slots.map {
                            ScheduleEntry(Subject(it.subject, it.teacher, it.room, it.colorIndex), "", it.canceled, it.date, it.start, it.end)
                        }
                        val timeline = buildDayTimeline(entries)
                        val current = now()
                        val nowMin = current.hour * 60 + current.minute
                        // On commence au premier cours pas fini : le widget montre l'avenir plutôt que le passé.
                        val firstIndex = timeline.lessons.indexOfFirst { it.endMin > nowMin }.coerceAtLeast(0)
                        val rows = WidgetLogic.rowsThatFit(height, 36f, 38f, 9)
                        val visible = timeline.lessons.drop(firstIndex).take(rows)
                        visible.forEachIndexed { i, lesson ->
                            val first = lesson.first
                            val live = nowMin in lesson.startMin until lesson.endMin
                            Line(
                                color = c.subject(first.subject.colorIndex),
                                first = first.start + "  " + first.subject.name + if (lesson.isDouble) " ×${lesson.entries.size}" else "",
                                second = "jusqu'à ${lesson.entries.last().end}" + first.subject.room.takeIf { it.isNotBlank() }?.let { " · salle $it" }.orEmpty(),
                                trailing = when {
                                    live -> ({ Chip("en cours", c.hero, c.onHero) })
                                    first.canceled -> ({ Chip("annulé", c.alertSoft, c.alert) })
                                    else -> null
                                },
                                highlighted = live,
                                dim = first.canceled,
                            )
                            val gap = timeline.gaps.firstOrNull { it.startMin == lesson.endMin }
                            if (gap != null && i < visible.lastIndex && height >= 170) {
                                Text("      ${gap.label} · ${formatMinutes(gap.duration)}", style = style(c.muted, 11), maxLines = 1)
                            }
                        }
                        val hidden = timeline.lessons.size - firstIndex - visible.size
                        if (hidden > 0) Text("+ $hidden autre(s) cours", style = style(c.accent, 12, bold = true))
                    }
                }
            }
          }
        }
    }
}

class DayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DayWidget()
}

