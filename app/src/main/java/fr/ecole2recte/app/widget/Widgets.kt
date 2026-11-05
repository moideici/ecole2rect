package fr.ecole2recte.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GlanceColor
import fr.ecole2recte.app.MainActivity
import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.domain.Subject
import fr.ecole2recte.app.sync.AndroidNotifier
import fr.ecole2recte.app.sync.WidgetSink
import fr.ecole2recte.app.sync.WidgetSlot
import fr.ecole2recte.app.sync.WidgetSnapshot
import fr.ecole2recte.app.sync.WidgetSnapshotStore
import fr.ecole2recte.app.ui.schedule.buildDayTimeline
import fr.ecole2recte.app.ui.schedule.formatMinutes
import fr.ecole2recte.app.ui.theme.DarkSubjectColors
import fr.ecole2recte.app.ui.theme.LightSubjectColors
import java.time.LocalDate
import java.time.LocalDateTime

// ---------------------------------------------------------------------------------------------
// Style. Principe : un seul regard doit suffire. Le widget qui compte le plus (prochain cours,
// messages) est plein indigo, texte blanc et très gros ; les listes ont une hiérarchie nette :
// l'heure ou la valeur en gras à gauche/à droite, le cours en cours sur fond coloré, ce qui est
// urgent en rouge. Clair le jour, sombre la nuit.
// ---------------------------------------------------------------------------------------------

private val Surface = ColorProvider(day = Color.White, night = Color(0xFF1A212B))
private val Ink = ColorProvider(day = Color(0xFF18202B), night = Color(0xFFF2F5FA))
private val Muted = ColorProvider(day = Color(0xFF566273), night = Color(0xFFB4BDCA))
private val Accent = ColorProvider(day = Color(0xFF4A50D6), night = Color(0xFFB0B4FF))
private val AccentSoft = ColorProvider(day = Color(0xFFE6E8FF), night = Color(0xFF2B316A))
private val Alert = ColorProvider(day = Color(0xFFC0392B), night = Color(0xFFFF9A93))
private val AlertSoft = ColorProvider(day = Color(0xFFFDE7E4), night = Color(0xFF4B2420))
private val Soft = ColorProvider(day = Color(0xFFF1F3FA), night = Color(0xFF242C38))
private val Hero = ColorProvider(day = Color(0xFF4A50D6), night = Color(0xFF454CCB))
private val OnHero = ColorProvider(day = Color.White, night = Color.White)
private val OnHeroSoft = ColorProvider(day = Color(0xCCFFFFFF), night = Color(0xCCFFFFFF))
private val HeroChip = ColorProvider(day = Color(0x33FFFFFF), night = Color(0x33FFFFFF))

private fun subjectColor(index: Int) = ColorProvider(
    day = LightSubjectColors[Math.floorMod(index, LightSubjectColors.size)],
    night = DarkSubjectColors[Math.floorMod(index, DarkSubjectColors.size)],
)

private fun style(color: GlanceColor, size: Int, bold: Boolean = false) =
    TextStyle(color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

/** Les tailles auxquelles chaque widget change de disposition ; Android choisit la plus proche. */
private val Sizes = setOf(
    DpSize(110.dp, 110.dp),
    DpSize(180.dp, 110.dp),
    DpSize(180.dp, 200.dp),
    DpSize(260.dp, 200.dp),
    DpSize(260.dp, 320.dp),
)

@Composable
private fun opens(tab: String): Action =
    actionStartActivity(
        Intent(LocalContext.current, MainActivity::class.java)
            .putExtra(AndroidNotifier.TAB_EXTRA, tab)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )

/** Cadre commun : fond arrondi (indigo plein pour [hero]), titre, un toucher ouvre l'onglet voulu. */
@Composable
private fun Frame(label: String, tab: String, hero: Boolean = false, content: @Composable () -> Unit) {
    Column(
        GlanceModifier.fillMaxSize().background(if (hero) Hero else Surface).cornerRadius(24.dp).padding(14.dp).clickable(opens(tab)),
    ) {
        Text(label.uppercase(), style = style(if (hero) OnHeroSoft else Accent, 11, bold = true), maxLines = 1)
        Spacer(GlanceModifier.height(6.dp))
        content()
    }
}

@Composable
private fun Empty(message: String, hero: Boolean = false) {
    Text(message, style = style(if (hero) OnHero else Ink, 14, bold = true))
}

private fun now(): LocalDateTime = LocalDateTime.now()

/** Une pastille de texte : fond doux, texte en gras. */
@Composable
private fun Chip(text: String, background: GlanceColor, color: GlanceColor) {
    Box(GlanceModifier.background(background).cornerRadius(12.dp).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Text(text, style = style(color, 12, bold = true), maxLines = 1)
    }
}

/** Une ligne de liste : barre de la couleur de la matière, texte sur deux niveaux, valeur à droite. */
@Composable
private fun Line(
    color: GlanceColor,
    first: String,
    second: String?,
    trailing: @Composable (() -> Unit)? = null,
    highlighted: Boolean = false,
    dim: Boolean = false,
) {
    val base = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)
    Row(
        (if (highlighted) base.background(AccentSoft).cornerRadius(12.dp).padding(horizontal = 6.dp, vertical = 4.dp) else base),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.width(if (highlighted) 6.dp else 4.dp).height(32.dp).background(color).cornerRadius(3.dp)) {}
        Spacer(GlanceModifier.width(9.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(first, style = style(if (dim) Muted else Ink, 15, bold = true), maxLines = 1)
            if (!second.isNullOrBlank()) Text(second, style = style(Muted, 12), maxLines = 1)
        }
        if (trailing != null) {
            Spacer(GlanceModifier.width(6.dp))
            trailing()
        }
    }
}

private fun detail(slot: WidgetSlot) =
    listOfNotNull("${slot.start} – ${slot.end}", slot.room.takeIf { it.isNotBlank() }?.let { "salle $it" }).joinToString(" · ")

// ---------------------------------------------------------------------------------------------
// 1. Prochain cours — plein indigo, gros
// ---------------------------------------------------------------------------------------------

class NextClassWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(Sizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotStore(context).read()
        provideContent {
            val height = LocalSize.current.height.value
            val width = LocalSize.current.width.value
            Frame("Prochain cours", "agenda", hero = true) {
                val upcoming = snapshot?.let { WidgetLogic.upcoming(it.lessons, now()) }.orEmpty()
                val next = upcoming.firstOrNull()
                when {
                    snapshot == null -> Empty("Ouvre ecole2recte une fois pour remplir ce widget.", hero = true)
                    next == null -> Empty("Plus de cours à venir.", hero = true)
                    else -> {
                        Chip(WidgetLogic.status(next, now()), HeroChip, OnHero)
                        Spacer(GlanceModifier.height(6.dp))
                        Text(next.subject, style = style(OnHero, if (width < 150) 22 else 28, bold = true), maxLines = 2)
                        if (height >= 120) {
                            Spacer(GlanceModifier.height(4.dp))
                            Text("${next.start} – ${next.end}", style = style(OnHero, 18, bold = true), maxLines = 1)
                            val place = listOfNotNull(next.room.takeIf { it.isNotBlank() }?.let { "salle $it" }, next.teacher.takeIf { it.isNotBlank() && width >= 170 })
                            if (place.isNotEmpty()) Text(place.joinToString(" · "), style = style(OnHeroSoft, 13), maxLines = 1)
                        }
                        val following = upcoming.drop(1)
                        if (height >= 190 && following.isNotEmpty()) {
                            Spacer(GlanceModifier.height(10.dp))
                            Text("ENSUITE", style = style(OnHeroSoft, 11, bold = true))
                            following.take(WidgetLogic.rowsThatFit(height, 170f, 30f, 4)).forEach {
                                Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(it.start, style = style(OnHero, 14, bold = true), modifier = GlanceModifier.width(46.dp))
                                    Text(it.subject, style = style(OnHero, 14), maxLines = 1)
                                }
                            }
                        } else if (height in 120f..189f && following.isNotEmpty() && width >= 170) {
                            Spacer(GlanceModifier.height(6.dp))
                            Text("Ensuite · ${following.first().subject} à ${following.first().start}", style = style(OnHeroSoft, 12), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

class NextClassWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextClassWidget()
}

// ---------------------------------------------------------------------------------------------
// 2. Ma journée — la frise du jour, pauses comprises, cours en cours en surbrillance
// ---------------------------------------------------------------------------------------------

class DayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(Sizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotStore(context).read()
        provideContent {
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
                                color = subjectColor(first.subject.colorIndex),
                                first = first.start + "  " + first.subject.name + if (lesson.isDouble) " ×${lesson.entries.size}" else "",
                                second = "jusqu'à ${lesson.entries.last().end}" + first.subject.room.takeIf { it.isNotBlank() }?.let { " · salle $it" }.orEmpty(),
                                trailing = when {
                                    live -> ({ Chip("en cours", Hero, OnHero) })
                                    first.canceled -> ({ Chip("annulé", AlertSoft, Alert) })
                                    else -> null
                                },
                                highlighted = live,
                                dim = first.canceled,
                            )
                            val gap = timeline.gaps.firstOrNull { it.startMin == lesson.endMin }
                            if (gap != null && i < visible.lastIndex && height >= 170) {
                                Text("      ${gap.label} · ${formatMinutes(gap.duration)}", style = style(Muted, 11), maxLines = 1)
                            }
                        }
                        val hidden = timeline.lessons.size - firstIndex - visible.size
                        if (hidden > 0) Text("+ $hidden autre(s) cours", style = style(Accent, 12, bold = true))
                    }
                }
            }
        }
    }
}

class DayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DayWidget()
}

// ---------------------------------------------------------------------------------------------
// 3. Semaine
// ---------------------------------------------------------------------------------------------

class WeekWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(Sizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotStore(context).read()
        provideContent {
            val height = LocalSize.current.height.value
            Frame("Semaine", "agenda") {
                val today = LocalDate.now()
                val days = snapshot?.let { WidgetLogic.week(it.lessons, today, WidgetLogic.rowsThatFit(height, 36f, 40f, 5)) }.orEmpty()
                when {
                    snapshot == null -> Empty("Ouvre ecole2recte une fois pour remplir ce widget.")
                    days.isEmpty() -> Empty("Aucun cours les prochains jours.")
                    else -> days.forEach { day ->
                        Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(day.label, style = style(Ink, 15, bold = true), modifier = GlanceModifier.width(62.dp), maxLines = 1)
                            Column(GlanceModifier.defaultWeight()) {
                                Text(
                                    if (day.lessons == 0) "Aucun cours" else "${day.first} → ${day.last}",
                                    style = style(Muted, 13),
                                    maxLines = 1,
                                )
                            }
                            if (day.canceled > 0) {
                                Chip("${day.canceled} annulé", AlertSoft, Alert)
                                Spacer(GlanceModifier.width(4.dp))
                            }
                            if (day.lessons > 0) Chip("${day.lessons} cours", AccentSoft, Accent)
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

// ---------------------------------------------------------------------------------------------
// 4. Devoirs à faire — l'urgent en rouge
// ---------------------------------------------------------------------------------------------

class HomeworkWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(Sizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotStore(context).read()
        provideContent {
            val height = LocalSize.current.height.value
            Frame("Devoirs à faire", "agenda_homework") {
                val tasks = snapshot?.tasks.orEmpty()
                val soon = LocalDate.now().plusDays(1).toString()
                when {
                    snapshot == null -> Empty("Ouvre ecole2recte une fois pour remplir ce widget.")
                    tasks.isEmpty() -> Empty("Rien à faire. Profites-en !")
                    else -> {
                        val rows = WidgetLogic.rowsThatFit(height, 36f, 38f, 7)
                        tasks.take(rows).forEach { task ->
                            val urgent = task.dueDate.isNotBlank() && task.dueDate <= soon
                            val whenText = task.due.substringBefore(' ').takeIf { it.isNotBlank() } ?: ""
                            Line(
                                color = subjectColor(task.colorIndex),
                                first = task.subject,
                                second = task.title.takeIf { it.isNotBlank() },
                                trailing = if (whenText.isBlank()) null else ({
                                    when {
                                        task.exam -> Chip("évaluation", AlertSoft, Alert)
                                        urgent -> Chip(whenText, AlertSoft, Alert)
                                        else -> Chip(whenText, Soft, Muted)
                                    }
                                }),
                            )
                        }
                        if (tasks.size > rows) Text("+ ${tasks.size - rows} autre(s)", style = style(Accent, 12, bold = true))
                    }
                }
            }
        }
    }
}

class HomeworkWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HomeworkWidget()
}

// ---------------------------------------------------------------------------------------------
// 5. Notes — la moyenne en grand, chaque note dans sa pastille
// ---------------------------------------------------------------------------------------------

class GradesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(Sizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotStore(context).read()
        provideContent {
            val height = LocalSize.current.height.value
            Frame("Notes", "notes") {
                when {
                    snapshot == null -> Empty("Ouvre ecole2recte une fois pour remplir ce widget.")
                    snapshot.grades.isEmpty() && snapshot.average.isBlank() -> Empty("Pas encore de notes.")
                    else -> {
                        if (snapshot.average.isNotBlank()) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(snapshot.average, style = style(Accent, if (height < 140) 34 else 42, bold = true))
                                Text("  /20", style = style(Muted, 15, bold = true), maxLines = 1)
                            }
                            Text("moyenne générale", style = style(Muted, 12), maxLines = 1)
                        }
                        if (height >= 150) {
                            Spacer(GlanceModifier.height(8.dp))
                            snapshot.grades.take(WidgetLogic.rowsThatFit(height, 110f, 38f, 5)).forEach { grade ->
                                Line(
                                    color = subjectColor(grade.colorIndex),
                                    first = grade.subject,
                                    second = grade.label.takeIf { it.isNotBlank() }?.let { "$it · coef. ${grade.coefficient}" } ?: "coef. ${grade.coefficient}",
                                    trailing = { Chip(grade.value, AccentSoft, Accent) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class GradesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GradesWidget()
}

// ---------------------------------------------------------------------------------------------
// 6. Messages non lus — plein indigo, le chiffre saute aux yeux
// ---------------------------------------------------------------------------------------------

class MessagesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(Sizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotStore(context).read()
        provideContent {
            val height = LocalSize.current.height.value
            Frame("Messages", "messages", hero = true) {
                when {
                    snapshot == null -> Empty("Ouvre ecole2recte une fois pour remplir ce widget.", hero = true)
                    snapshot.unread == 0 -> Empty("Aucun message non lu.", hero = true)
                    else -> {
                        Text(snapshot.unread.toString(), style = style(OnHero, if (height < 140) 40 else 54, bold = true))
                        Text(if (snapshot.unread == 1) "message non lu" else "messages non lus", style = style(OnHeroSoft, 14, bold = true), maxLines = 1)
                        if (height >= 170) {
                            Spacer(GlanceModifier.height(8.dp))
                            snapshot.mails.take(WidgetLogic.rowsThatFit(height, 130f, 34f, 3)).forEach { mail ->
                                Column(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    Text(mail.sender.ifBlank { "Nouveau message" }, style = style(OnHero, 14, bold = true), maxLines = 1)
                                    Text(mail.subject, style = style(OnHeroSoft, 12), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class MessagesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MessagesWidget()
}

/** Enregistre l'instantané puis redessine tous les widgets déjà posés sur l'écran d'accueil. */
class WidgetUpdater(private val context: Context) : WidgetSink {
    override suspend fun publish(snapshot: WidgetSnapshot) {
        WidgetSnapshotStore(context).write(snapshot)
        runCatching {
            NextClassWidget().updateAll(context)
            DayWidget().updateAll(context)
            WeekWidget().updateAll(context)
            HomeworkWidget().updateAll(context)
            GradesWidget().updateAll(context)
            MessagesWidget().updateAll(context)
        }
    }
}
