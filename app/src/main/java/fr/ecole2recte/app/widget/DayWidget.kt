package fr.ecole2recte.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import java.time.LocalDateTime

// ---------------------------------------------------------------------------------------------
// Journée complète — toute la journée, du plus simple au plus détaillé :
//   petit  : le cours en cours (ou le prochain) en grand, et le suivant
//   moyen  : la liste de ce qu'il reste à faire, pauses comprises
//   grand  : la frise horaire de la journée entière, salles et professeurs
// Une barre montre l'avancement de la journée ; la journée finie, le widget passe au jour suivant.
// Les calculs sont dans WidgetLayoutLogic (testé) ; ici on ne fait que dessiner.
// ---------------------------------------------------------------------------------------------

class DayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(TimetableSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadSnapshot(context)
        val colors = WidgetColors.load(context)
        provideContent {
            WidgetTheming(colors) {
                val size = LocalSize.current
                val layout = WidgetLayoutLogic.dayLayout(size.width.value, size.height.value, snapshot?.lessons.orEmpty(), LocalDateTime.now())
                DayFrame(layout, hasData = snapshot != null, widthDp = size.width.value)
            }
        }
    }
}

class DayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DayWidget()
}

@Composable
private fun DayFrame(layout: DayLayout, hasData: Boolean, widthDp: Float) {
    val c = LocalWidgetColors.current
    val focus = layout.detail == DayDetail.Focus
    val pad = if (focus) 10.dp else 12.dp
    Column(
        GlanceModifier.fillMaxSize().background(c.surface).cornerRadius(24.dp).padding(pad).clickable(opens("agenda", "journee")),
    ) {
        when {
            !hasData -> Empty("Ouvre ecole2recte une fois pour remplir ce widget.")
            layout.state == DayState.NoLessons -> {
                DayTitle(layout, widthDp, withBar = false)
                Empty("Pas de cours aujourd'hui.")
            }
            layout.state == DayState.Finished -> {
                DayTitle(layout, widthDp, withBar = !focus)
                Spacer(GlanceModifier.height(6.dp))
                Text("Journée terminée", style = style(c.ink, if (focus) 18 else 22, bold = true), maxLines = 2)
                Text("${layout.lessonCount} cours aujourd'hui. À demain !", style = style(c.muted, 12), maxLines = 2)
            }
            focus -> DayFocus(layout)
            layout.detail == DayDetail.List -> {
                DayTitle(layout, widthDp, withBar = true)
                DayList(layout)
            }
            else -> {
                DayTitle(layout, widthDp, withBar = true)
                DayTimeline(layout)
            }
        }
    }
}

/** Le jour, ce qu'il reste (si la place le permet) et la barre d'avancement de la journée. */
@Composable
private fun DayTitle(layout: DayLayout, widthDp: Float, withBar: Boolean) {
    val c = LocalWidgetColors.current
    Row(GlanceModifier.fillMaxWidth().height(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(layout.title.uppercase(), style = style(c.accent, 11, bold = true), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
        if (layout.state == DayState.NextDay) {
            Chip(layout.relative, c.accentSoft, c.accent, size = 10)
        } else if (layout.remaining.isNotBlank() && widthDp >= 200f) {
            Text(layout.remaining.substringAfter("· "), style = style(c.muted, 10, bold = true), maxLines = 1)
        }
    }
    if (withBar) {
        Spacer(GlanceModifier.height(4.dp))
        ProgressBar(layout.progress, widthDp - 24f)
        Spacer(GlanceModifier.height(6.dp))
    }
}

/** La barre d'avancement : un fond doux et une part pleine, calculée en dp faute de poids dynamique. */
@Composable
private fun ProgressBar(progress: Float, widthDp: Float) {
    val c = LocalWidgetColors.current
    Box(GlanceModifier.fillMaxWidth().height(6.dp).background(c.soft).cornerRadius(3.dp)) {
        if (progress > 0.01f) {
            Box(GlanceModifier.width((widthDp * progress).coerceAtLeast(6f).dp).height(6.dp).background(c.hero).cornerRadius(3.dp)) {}
        }
    }
}

// ----- petit : le cours en cours ou le prochain, en grand ------------------------------------

@Composable
private fun DayFocus(layout: DayLayout) {
    val c = LocalWidgetColors.current
    val height = LocalSize.current.height.value
    val width = LocalSize.current.width.value
    val current = layout.current
    val next = layout.next
    val shown = current ?: next
    if (shown == null) {
        Empty("Pas de cours à venir.")
        return
    }
    val live = current != null
    val background = if (live) c.subject(shown.colorIndex) else c.subjectSoft(shown.colorIndex)
    val ink = if (live) c.onSubject(shown.colorIndex) else c.ink
    val tag = when {
        live -> "EN COURS · jusqu'à ${shown.end}"
        layout.state == DayState.NextDay -> "${layout.relative.uppercase()} · ${shown.start}"
        else -> "PROCHAIN · ${layout.nextIn}"
    }
    Box(GlanceModifier.fillMaxWidth().background(background).cornerRadius(16.dp).padding(horizontal = 10.dp, vertical = 7.dp)) {
        Column {
            Text(tag, style = style(ink, 10, bold = true), maxLines = 1)
            Text(shown.title, style = style(ink, if (width < 150f) 18 else 22, bold = true), maxLines = if (height >= 130f) 2 else 1)
            if (height >= 120f) {
                val place = listOfNotNull(
                    "${shown.start} – ${shown.end}".takeIf { !live },
                    shown.room.takeIf { it.isNotBlank() }?.let { "salle $it" },
                    shown.teacher.takeIf { it.isNotBlank() && width >= 200f },
                )
                if (place.isNotEmpty()) Text(place.joinToString(" · "), style = style(ink, 12), maxLines = 1)
            }
        }
    }
    if (live && next != null) {
        Spacer(GlanceModifier.height(4.dp))
        Text("Ensuite · ${next.title} ${layout.nextIn}", style = style(c.muted, 11, bold = true), maxLines = 1)
    }
    if (layout.state == DayState.Active) {
        Spacer(GlanceModifier.height(5.dp))
        ProgressBar(layout.progress, width - 20f)
    }
}

// ----- moyen : la liste de ce qu'il reste ---------------------------------------------------

@Composable
private fun DayList(layout: DayLayout) {
    val c = LocalWidgetColors.current
    layout.rows.forEach { row ->
        if (row.kind == RowKind.Gap) {
            Text(row.lines.firstOrNull().orEmpty(), style = style(c.muted, 10), modifier = GlanceModifier.padding(start = 46.dp), maxLines = 1)
        } else {
            val base = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)
            val ink = if (row.live) c.onSubject(row.colorIndex) else if (row.canceled) c.muted else c.ink
            val soft = if (row.live) c.onSubject(row.colorIndex) else c.muted
            Row(
                if (row.live) base.background(c.subject(row.colorIndex)).cornerRadius(10.dp).padding(horizontal = 4.dp) else base,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.start, style = style(if (row.live) ink else c.muted, 12, bold = true), modifier = GlanceModifier.width(40.dp), maxLines = 1)
                Box(GlanceModifier.width(4.dp).height(28.dp).background(if (row.canceled) c.muted else if (row.live) ink else c.subject(row.colorIndex)).cornerRadius(2.dp)) {}
                Spacer(GlanceModifier.width(7.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(row.lines.getOrNull(0).orEmpty(), style = style(ink, 14, bold = true, strike = row.canceled), maxLines = 1)
                    Text(row.lines.getOrNull(1).orEmpty(), style = style(soft, 11), maxLines = 1)
                }
                if (row.live) Text("EN COURS", style = style(ink, 9, bold = true), maxLines = 1)
            }
        }
    }
    if (layout.hiddenLessons > 0) {
        Text("+ ${layout.hiddenLessons} autre(s) cours", style = style(c.accent, 11, bold = true), maxLines = 1)
    }
}

// ----- grand : la frise horaire --------------------------------------------------------------

@Composable
private fun DayTimeline(layout: DayLayout) {
    val c = LocalWidgetColors.current
    layout.rows.forEach { row ->
        if (row.kind == RowKind.Gap) {
            Row(GlanceModifier.fillMaxWidth().height(row.heightDp.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(GlanceModifier.width(44.dp))
                Text("· ${row.lines.firstOrNull().orEmpty()}", style = style(c.muted, 10), maxLines = 1)
            }
        } else {
            val background = when {
                row.live -> c.subject(row.colorIndex)
                row.canceled || row.past -> c.soft
                else -> c.subjectSoft(row.colorIndex)
            }
            val ink = when {
                row.live -> c.onSubject(row.colorIndex)
                row.canceled || row.past -> c.muted
                else -> c.ink
            }
            Row(GlanceModifier.fillMaxWidth().height(row.heightDp.dp).padding(bottom = 2.dp)) {
                Text(
                    row.start,
                    style = style(if (row.live) c.accent else c.muted, 11, bold = true),
                    modifier = GlanceModifier.width(44.dp),
                    maxLines = 1,
                )
                Row(GlanceModifier.defaultWeight().fillMaxHeight().background(background).cornerRadius(10.dp)) {
                    Box(GlanceModifier.width(5.dp).fillMaxHeight().background(if (row.canceled || row.past) c.muted else if (row.live) ink else c.subject(row.colorIndex))) {}
                    Column(GlanceModifier.defaultWeight().padding(horizontal = 7.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(GlanceModifier.fillMaxWidth()) {
                            Text(
                                row.lines.getOrNull(0).orEmpty(),
                                style = style(ink, 13, bold = true, strike = row.canceled),
                                modifier = GlanceModifier.defaultWeight(),
                                maxLines = 1,
                            )
                            if (row.live) Text("EN COURS", style = style(ink, 9, bold = true), maxLines = 1)
                        }
                        row.lines.drop(1).forEach { line -> Text(line, style = style(ink, 11), maxLines = 1) }
                    }
                }
            }
        }
    }
    if (layout.hiddenLessons > 0) {
        Text("+ ${layout.hiddenLessons} autre(s) cours", style = style(c.accent, 11, bold = true), maxLines = 1)
    }
}
