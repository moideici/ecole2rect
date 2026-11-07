package fr.ecole2recte.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
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
import fr.ecole2recte.app.domain.FrenchFormat
import java.time.LocalDateTime

// ---------------------------------------------------------------------------------------------
// Semaine complète — plus on agrandit, plus on voit de détails :
//   petit      : un jour par ligne (nombre de cours, première et dernière heure)
//   moyen      : cinq colonnes de pastilles colorées avec l'initiale de la matière
//   grand      : grille jours × heures, cours colorés avec leur nom
//   très grand : la grille avec nom complet, salle, horaires et professeur
// Tous les calculs sont dans WidgetLayoutLogic (testé) ; ici on ne fait que dessiner.
// ---------------------------------------------------------------------------------------------

/** Les tailles de référence : une par niveau de détail, plus les variantes de hauteur. */
val TimetableSizes = setOf(
    DpSize(110.dp, 110.dp),
    DpSize(180.dp, 110.dp),
    DpSize(250.dp, 110.dp),
    DpSize(180.dp, 200.dp),
    DpSize(250.dp, 200.dp),
    DpSize(250.dp, 320.dp),
    DpSize(320.dp, 320.dp),
    DpSize(320.dp, 440.dp),
)

class WeekWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(TimetableSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadSnapshot(context)
        val colors = WidgetColors.load(context)
        provideContent {
            WidgetTheming(colors) {
                val size = LocalSize.current
                val layout = WidgetLayoutLogic.weekLayout(size.width.value, size.height.value, snapshot?.lessons.orEmpty(), LocalDateTime.now())
                WeekFrame(layout, hasData = snapshot != null)
            }
        }
    }
}

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}

@Composable
private fun WeekFrame(layout: WeekLayout, hasData: Boolean) {
    val c = LocalWidgetColors.current
    val noLessons = layout.columns.all { it.blocks.isEmpty() }
    Column(
        GlanceModifier.fillMaxSize().background(c.surface).cornerRadius(24.dp).padding(WidgetLayoutLogic.PAD.dp)
            .clickable(opens("agenda", "semaine")),
    ) {
        if (layout.showTitle) {
            Text(layout.title.uppercase(), style = style(c.accent, 10, bold = true), maxLines = 1)
            Spacer(GlanceModifier.height(4.dp))
        }
        when {
            !hasData -> Empty("Ouvre ecole2recte une fois pour remplir ce widget.")
            noLessons -> Empty("Aucun cours cette semaine.")
            layout.detail == WeekDetail.Summary -> WeekSummary(layout)
            layout.detail == WeekDetail.Columns -> WeekColumns(layout)
            else -> WeekGrid(layout)
        }
    }
}

/** « 8h », « 8h30 » : l'heure sans zéro, pour les très petites largeurs. */
private fun shortHour(time: String): String {
    val hour = time.take(2).toIntOrNull() ?: return time
    val minute = time.takeLast(2).toIntOrNull() ?: 0
    return "${hour}h" + if (minute == 0) "" else FrenchFormat.twoDigits(minute)
}

@Composable
private fun WeekSummary(layout: WeekLayout) {
    val c = LocalWidgetColors.current
    val narrow = LocalSize.current.width.value < 150f
    layout.columns.forEach { day ->
        val base = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp)
        Row(
            if (day.today) base.background(c.accentSoft).cornerRadius(10.dp).padding(horizontal = 4.dp) else base.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${day.label} ${day.number}",
                style = style(if (day.today) c.accent else if (day.past) c.muted else c.ink, 13, bold = true),
                modifier = GlanceModifier.width(if (narrow) 40.dp else 50.dp),
                maxLines = 1,
            )
            val range = when {
                day.lessons == 0 -> "–"
                narrow -> "${shortHour(day.first)}–${shortHour(day.last)}"
                else -> "${day.first} → ${day.last}"
            }
            Text(range, style = style(c.muted, 11), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
            if (day.canceled > 0 && !narrow) {
                Chip("${day.canceled} annulé", c.alertSoft, c.alert, size = 10)
                Spacer(GlanceModifier.width(3.dp))
            }
            if (day.lessons > 0) Chip("${day.lessons}", c.accentSoft, c.accent, size = 11)
        }
    }
}

/** L'en-tête d'une colonne : le jour, plein couleur pour aujourd'hui. */
@Composable
private fun DayHeader(day: WeekColumn, compact: Boolean) {
    val c = LocalWidgetColors.current
    val modifier = GlanceModifier.fillMaxWidth().height(WidgetLayoutLogic.HEADER_H.dp)
    Box(
        if (day.today) modifier.background(c.hero).cornerRadius(9.dp) else modifier,
        contentAlignment = Alignment.Center,
    ) {
        val text = if (compact) day.label else "${day.label} ${day.number}"
        Text(
            text,
            style = style(if (day.today) c.onHero else if (day.past) c.muted else c.ink, 10, bold = true),
            maxLines = 1,
        )
    }
}

@Composable
private fun WeekColumns(layout: WeekLayout) {
    val c = LocalWidgetColors.current
    Row(GlanceModifier.fillMaxSize()) {
        layout.columns.forEach { day ->
            val base = GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp)
            Column(if (day.today) base.background(c.soft).cornerRadius(10.dp) else base) {
                DayHeader(day, compact = true)
                Spacer(GlanceModifier.height(2.dp))
                val overflow = day.blocks.size > layout.chipCapacity
                val shown = if (overflow) day.blocks.take((layout.chipCapacity - 1).coerceAtLeast(0)) else day.blocks
                shown.forEach { block -> BlockChip(block) }
                if (overflow) {
                    Text(
                        "+${day.blocks.size - shown.size}",
                        style = style(c.muted, 10, bold = true),
                        modifier = GlanceModifier.fillMaxWidth(),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Une pastille : initiale(s) de la matière sur sa couleur ; en cours = couleur pleine ; annulé = barré. */
@Composable
private fun BlockChip(block: WeekBlock) {
    val c = LocalWidgetColors.current
    val background = when {
        block.canceled -> c.soft
        block.live -> c.subject(block.colorIndex)
        else -> c.subjectSoft(block.colorIndex)
    }
    val color = when {
        block.canceled -> c.muted
        block.live -> c.onSubject(block.colorIndex)
        else -> c.ink
    }
    Box(GlanceModifier.fillMaxWidth().padding(bottom = 2.dp)) {
        Box(
            GlanceModifier.fillMaxWidth().height(16.dp).background(background).cornerRadius(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(block.initials, style = style(color, 10, bold = true, strike = block.canceled), maxLines = 1)
        }
    }
}

@Composable
private fun WeekGrid(layout: WeekLayout) {
    val c = LocalWidgetColors.current
    val perHour = layout.dpPerMinute * 60f
    Row(GlanceModifier.fillMaxSize()) {
        // Graduations des heures
        Column(GlanceModifier.width(WidgetLayoutLogic.RAIL_W.dp)) {
            Spacer(GlanceModifier.height((WidgetLayoutLogic.HEADER_H + 4f).dp))
            layout.hourLabels.forEach { hour ->
                Box(GlanceModifier.fillMaxWidth().height(perHour.dp)) {
                    Text("${hour}h", style = style(c.muted, 8), maxLines = 1)
                }
            }
        }
        layout.columns.forEach { day ->
            Column(GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp)) {
                DayHeader(day, compact = false)
                Spacer(GlanceModifier.height(4.dp))
                val area = GlanceModifier.fillMaxWidth().height(layout.gridHeightDp.dp)
                Box(if (day.today) area.background(c.soft).cornerRadius(6.dp) else area) {
                    // Traits des heures
                    Column(GlanceModifier.fillMaxWidth()) {
                        layout.hourLabels.forEach { _ ->
                            Box(GlanceModifier.fillMaxWidth().height(perHour.dp)) {
                                Box(GlanceModifier.fillMaxWidth().height(1.dp).background(c.line)) {}
                            }
                        }
                    }
                    // Cours, posés au bon endroit par des espaces proportionnels au temps
                    Column(GlanceModifier.fillMaxWidth()) {
                        var cursor = layout.fromMin
                        day.blocks.forEach { block ->
                            val gap = (block.startMin - cursor) * layout.dpPerMinute
                            if (gap >= 1f) Spacer(GlanceModifier.height(gap.dp))
                            val height = (block.endMin - maxOf(block.startMin, cursor)) * layout.dpPerMinute
                            if (height >= 4f) GridBlock(block, height, layout.columnWidthDp, layout.detail)
                            cursor = maxOf(cursor, block.endMin)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridBlock(block: WeekBlock, heightDp: Float, widthDp: Float, detail: WeekDetail) {
    val c = LocalWidgetColors.current
    val lines = WidgetLayoutLogic.blockLines(block, heightDp, widthDp, detail)
    val background = when {
        block.canceled -> c.soft
        block.live -> c.subject(block.colorIndex)
        else -> c.subjectSoft(block.colorIndex)
    }
    val color = when {
        block.canceled -> c.muted
        block.live -> c.onSubject(block.colorIndex)
        else -> c.ink
    }
    val fontSize = if (detail == WeekDetail.Detailed) 10 else 9
    Box(GlanceModifier.fillMaxWidth().height(heightDp.dp).padding(bottom = 1.dp)) {
        Row(GlanceModifier.fillMaxSize().background(background).cornerRadius(5.dp)) {
            Box(GlanceModifier.width(3.dp).fillMaxHeight().background(if (block.canceled) c.muted else c.subject(block.colorIndex))) {}
            Column(GlanceModifier.defaultWeight().padding(horizontal = 2.dp, vertical = 1.dp)) {
                lines.forEachIndexed { index, line ->
                    Text(
                        line,
                        style = style(if (index == 0) color else color, fontSize, bold = index == 0 || block.live, strike = block.canceled && index == 0),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
