package fr.ecole2recte.app.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import fr.ecole2recte.app.settings.AverageDecimals
import fr.ecole2recte.app.settings.PrivacySelection

val GoalWidgetSizes = setOf(DpSize(110.dp, 110.dp), DpSize(180.dp, 180.dp), DpSize(260.dp, 240.dp), DpSize(320.dp, 440.dp), DpSize(600.dp, 400.dp))

class GoalsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(GoalWidgetSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = runCatching { androidx.glance.appwidget.GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrDefault(0)
        val config = WidgetConfigStore.at(context).read(widgetId)
        val snapshot = instanceSnapshot(context, config)
        val profile = config.profile.ifBlank { snapshot?.profileKey.orEmpty() }
        val subjects = snapshot?.meanPeriods?.firstOrNull { it.current }?.subjects.orEmpty()
        val privacy = PrivacySelection.mode(context)
        val rows = GoalWidgetData.rows(subjects, GoalStore.at(context).values(profile), AverageDecimals.read(context), privacy)
        val colors = WidgetColors.load(context)
        val scale = widgetFontScale(context)
        val textScale = widgetTextScale(context)
        provideContent {
            WidgetTheming(colors) {
                val c = LocalWidgetColors.current
                val size = LocalSize.current
                val capacity = GoalWidgetData.capacity(size.width.value, size.height.value, size.width > size.height, scale)
                Column(GlanceModifier.fillMaxSize().background(c.surface).cornerRadius(20.dp).padding(8.dp).clickable(widgetOpens("grades", profile = profile))
                    .semantics { contentDescription = "Objectifs · " + rows.joinToString(" · ") { "${it.name} : ${it.text}" } + " · Ouvrir Notes" }) {
                    Row(GlanceModifier.fillMaxWidth()) {
                        Text("Objectifs", style = style(c.accent, (12 * textScale).toInt(), true), modifier = GlanceModifier.defaultWeight())
                        if (widgetId > 0) Text("⚙", style = style(c.muted, 14), modifier = GlanceModifier.clickable(configureWidget(widgetId)).padding(horizontal = 4.dp))
                    }
                    if (rows.isEmpty()) Text("Ouvre Notes", style = style(c.muted, (10 * textScale).toInt()), maxLines = 2)
                    rows.take(capacity).forEach { row ->
                        Column(GlanceModifier.fillMaxWidth().defaultWeight().clickable(widgetOpens("grades", row.id, profile)).semantics { contentDescription = "${row.name} · ${row.text}" }) {
                            // Deux nombres sont toujours écrits ; une jauge seule ne porte aucune information.
                            Text(row.name, style = style(c.ink, (11 * textScale).toInt(), true), maxLines = 1)
                            Text(if (capacity <= 4) row.compactText else row.text, style = style(c.muted, (10 * textScale).toInt()), maxLines = 1)
                            row.percent?.let { LinearProgressIndicator(progress = it / 100f, modifier = GlanceModifier.fillMaxWidth(), color = c.accent, backgroundColor = c.soft) }
                        }
                    }
                    if (rows.size > capacity) Text("+ ${rows.size - capacity} · ouvrir Notes", style = style(c.muted, (10 * textScale).toInt()), maxLines = 1)
                }
            }
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        runCatching { androidx.glance.appwidget.GlanceAppWidgetManager(context).getAppWidgetId(glanceId) }.getOrNull()?.let { WidgetConfigStore.at(context).remove(it) }
        super.onDelete(context, glanceId)
    }
}

class GoalsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = GoalsWidget() }
