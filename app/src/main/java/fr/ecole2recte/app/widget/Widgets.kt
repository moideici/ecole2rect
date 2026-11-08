package fr.ecole2recte.app.widget

import android.content.Context
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
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import fr.ecole2recte.app.domain.MASKED
import fr.ecole2recte.app.sync.WidgetSink
import fr.ecole2recte.app.sync.WidgetSnapshot
import fr.ecole2recte.app.sync.WidgetSnapshotStore
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// Style. Principe : un seul regard doit suffire. Le widget qui compte le plus (prochain cours,
// messages) est plein couleur du thème, texte contrasté et très gros ; les listes ont une hiérarchie
// nette : l'heure ou la valeur en gras à gauche/à droite, le cours en cours sur fond coloré, ce qui
// est urgent en rouge. Les couleurs viennent du thème choisi dans l'application (WidgetUi.kt).
// Les widgets « Semaine complète » et « Journée complète » sont dans WeekWidget.kt et DayWidget.kt.
// ---------------------------------------------------------------------------------------------

private fun now(): LocalDateTime = LocalDateTime.now()

// ---------------------------------------------------------------------------------------------
// 1. Prochain cours — plein couleur du thème, gros
// ---------------------------------------------------------------------------------------------

class NextClassWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(BasicSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadSnapshot(context)
        val colors = WidgetColors.load(context)
        provideContent {
            WidgetTheming(colors) {
                val c = LocalWidgetColors.current
                val height = LocalSize.current.height.value
                val width = LocalSize.current.width.value
                Frame("Prochain cours", "agenda", hero = true) {
                    val upcoming = snapshot?.let { WidgetLogic.upcoming(it.lessons, now()) }.orEmpty()
                    val next = upcoming.firstOrNull()
                    when {
                        snapshot == null -> Empty("Ouvre l'app.", hero = true)
                        next == null -> Empty("Plus de cours à venir.", hero = true)
                        else -> {
                            Chip(WidgetLogic.status(next, now()), c.heroChip, c.onHero)
                            Spacer(GlanceModifier.height(6.dp))
                            Text(next.subject, style = style(c.onHero, if (width < 150) 22 else 28, bold = true), maxLines = 2)
                            if (height >= 120) {
                                Spacer(GlanceModifier.height(4.dp))
                                Text("${next.start} – ${next.end}", style = style(c.onHero, 18, bold = true), maxLines = 1)
                                val place = listOfNotNull(next.room.takeIf { it.isNotBlank() }?.let { "salle $it" }, next.teacher.takeIf { it.isNotBlank() && width >= 170 })
                                if (place.isNotEmpty()) Text(place.joinToString(" · "), style = style(c.onHeroSoft, 13), maxLines = 1)
                            }
                            val following = upcoming.drop(1)
                            if (height >= 190 && following.isNotEmpty()) {
                                Spacer(GlanceModifier.height(10.dp))
                                Text("ENSUITE", style = style(c.onHeroSoft, 11, bold = true))
                                following.take(WidgetLogic.rowsThatFit(height, 200f, 30f, 4)).forEach {
                                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(it.start, style = style(c.onHero, 14, bold = true), modifier = GlanceModifier.width(46.dp))
                                        Text(it.subject, style = style(c.onHero, 14), maxLines = 1)
                                    }
                                }
                            } else if (height in 120f..189f && following.isNotEmpty() && width >= 170) {
                                Spacer(GlanceModifier.height(6.dp))
                                Text("Ensuite · ${following.first().subject} à ${following.first().start}", style = style(c.onHeroSoft, 12), maxLines = 1)
                            }
                            if (height >= 150) {
                                // Zone d'ouverture explicite : l'agenda du jour (l'ensemble du widget ouvre déjà l'agenda).
                                Spacer(GlanceModifier.height(8.dp))
                                Box(
                                    GlanceModifier.background(c.heroChip).cornerRadius(14.dp).padding(horizontal = 12.dp, vertical = 5.dp)
                                        .clickable(opens("agenda", "prochain")),
                                ) {
                                    Text("Agenda  ›", style = style(c.onHero, 12, bold = true), maxLines = 1)
                                }
                            }
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
// 4. Devoirs à faire — l'urgent en rouge
// ---------------------------------------------------------------------------------------------

class HomeworkWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(BasicSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadSnapshot(context)
        val colors = WidgetColors.load(context)
        provideContent {
            WidgetTheming(colors) {
                val c = LocalWidgetColors.current
                val height = LocalSize.current.height.value
                Frame("Devoirs à faire", "agenda_homework") {
                    val tasks = snapshot?.tasks.orEmpty()
                    val soon = LocalDate.now().plusDays(1).toString()
                    when {
                        snapshot == null -> Empty("Ouvre l'app.")
                        tasks.isEmpty() -> Empty("Rien à faire.")
                        else -> {
                            val rows = WidgetLogic.rowsThatFit(height, 36f, 38f, 7)
                            tasks.take(rows).forEach { task ->
                                val urgent = task.dueDate.isNotBlank() && task.dueDate <= soon
                                val whenText = task.due.substringBefore(' ').takeIf { it.isNotBlank() } ?: ""
                                Line(
                                    color = c.subject(task.colorIndex),
                                    first = task.subject,
                                    second = task.title.takeIf { it.isNotBlank() },
                                    trailing = if (whenText.isBlank()) null else ({
                                        when {
                                            task.exam -> Chip("évaluation", c.alertSoft, c.alert)
                                            urgent -> Chip(whenText, c.alertSoft, c.alert)
                                            else -> Chip(whenText, c.soft, c.muted)
                                        }
                                    }),
                                )
                            }
                            if (tasks.size > rows) Text("+ ${tasks.size - rows} autre(s)", style = style(c.accent, 12, bold = true))
                        }
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
// 5. Notes — la moyenne en grand, chaque note dans sa pastille (« ••• » en mode « montrer »)
// ---------------------------------------------------------------------------------------------

class GradesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(BasicSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadSnapshot(context)
        val colors = WidgetColors.load(context)
        provideContent {
            WidgetTheming(colors) {
                val c = LocalWidgetColors.current
                val height = LocalSize.current.height.value
                Frame("Notes", "notes") {
                    when {
                        snapshot == null -> Empty("Ouvre l'app.")
                        snapshot.grades.isEmpty() && snapshot.average.isBlank() -> Empty("Pas encore de notes.")
                        else -> {
                            if (snapshot.average.isNotBlank()) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(snapshot.average, style = style(c.accent, if (height < 140) 34 else 42, bold = true))
                                    if (snapshot.average != MASKED) Text("  /20", style = style(c.muted, 15, bold = true), maxLines = 1)
                                }
                                Text("moyenne générale", style = style(c.muted, 12), maxLines = 1)
                                if (snapshot.averageLabel.isNotBlank()) Text(snapshot.averageLabel, style = style(c.muted, 11), maxLines = 2)
                                if (snapshot.averageBehind.isNotBlank() && height >= 150) Text(snapshot.averageBehind, style = style(c.muted, 11), maxLines = 2)
                            }
                            if (height >= 150) {
                                Spacer(GlanceModifier.height(8.dp))
                                snapshot.grades.take(WidgetLogic.rowsThatFit(height, 110f, 38f, 5)).forEach { grade ->
                                    Line(
                                        color = c.subject(grade.colorIndex),
                                        first = grade.subject,
                                        second = grade.label.takeIf { it.isNotBlank() }?.let { "$it · coef. ${grade.coefficient}" } ?: "coef. ${grade.coefficient}",
                                        trailing = { Chip(grade.value, c.accentSoft, c.accent) },
                                    )
                                }
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
// 6. Messages non lus — plein couleur du thème, le chiffre saute aux yeux
// ---------------------------------------------------------------------------------------------

class MessagesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(BasicSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadSnapshot(context)
        val colors = WidgetColors.load(context)
        val messagesHidden = fr.ecole2recte.app.settings.PrivacySelection.mode(context).hidesMessages
        provideContent {
            WidgetTheming(colors) {
                val c = LocalWidgetColors.current
                val height = LocalSize.current.height.value
                Frame("Messages", "messages", hero = true) {
                    when {
                        snapshot == null -> Empty("Ouvre l'app.", hero = true)
                        messagesHidden -> Text(MASKED, style = style(c.onHero, 40, bold = true))
                        snapshot.unread == 0 -> Empty("Aucun message non lu.", hero = true)
                        else -> {
                            Text(snapshot.unread.toString(), style = style(c.onHero, if (height < 140) 40 else 54, bold = true))
                            Text(if (snapshot.unread == 1) "message non lu" else "messages non lus", style = style(c.onHeroSoft, 14, bold = true), maxLines = 1)
                            if (height >= 170) {
                                Spacer(GlanceModifier.height(8.dp))
                                snapshot.mails.take(WidgetLogic.rowsThatFit(height, 130f, 34f, 3)).forEach { mail ->
                                    Column(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                        Text(mail.sender.ifBlank { "Nouveau message" }, style = style(c.onHero, 14, bold = true), maxLines = 1)
                                        Text(mail.subject, style = style(c.onHeroSoft, 12), maxLines = 1)
                                    }
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

/** Redessine tous les widgets déjà posés sur l'écran d'accueil (sans toucher à l'instantané ni au réseau). */
suspend fun updateAllWidgets(context: Context) {
    runCatching {
        NextClassWidget().updateAll(context)
        DayWidget().updateAll(context)
        WeekWidget().updateAll(context)
        HomeworkWidget().updateAll(context)
        GradesWidget().updateAll(context)
        MessagesWidget().updateAll(context)
        MeanWidget().updateAll(context)
        SmallNextClassWidget().updateAll(context)
        RemainingHomeworkWidget().updateAll(context)
        LastGradeWidget().updateAll(context)
        UnreadMessagesWidget().updateAll(context)
        ExamCountdownWidget().updateAll(context)
        GoalsWidget().updateAll(context)
        SchoolLifeWidget().updateAll(context)
    }
}

/**
 * Demande un redessin immédiat des widgets quand un réglage qui les concerne change (thème de couleurs,
 * mode clair/sombre/noir, mode « montrer à quelqu'un »). Pas de tâche planifiée : un simple lancement.
 */
object WidgetRefresh {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun requestAll(context: Context) {
        val app = context.applicationContext
        scope.launch { updateAllWidgets(app) }
    }
}

/** Enregistre l'instantané puis redessine tous les widgets déjà posés sur l'écran d'accueil. */
class WidgetUpdater(private val context: Context) : WidgetSink {
    override val wantsSchoolLife: Boolean get() = fr.ecole2recte.app.widget.wantsSchoolLife(context)
    override suspend fun publishSchoolLife(owner: String, events: List<fr.ecole2recte.app.domain.SchoolLifeEvent>) {
        SchoolLifeSnapshotStore(context).publish(owner, events)
    }
    override suspend fun publish(snapshot: WidgetSnapshot) {
        if (snapshot.owner.isNotBlank() && fr.ecole2recte.app.sync.LocalFeaturePreferences(context).activeOwner != snapshot.owner) return
        val seen = (context.applicationContext as fr.ecole2recte.app.Ecole2recteApplication).container.seenGrades
        fun mark(value: WidgetSnapshot): WidgetSnapshot {
            val times = seen.record(value.profileKey, value.gradeIds.ifEmpty { value.grades.map { it.id } })
            return value.copy(grades = value.grades.map { it.copy(firstSeenAt = times[it.id] ?: 0L) })
        }
        WidgetSnapshotStore(context).write(mark(snapshot).copy(profiles = snapshot.profiles.map(::mark)))
        runCatching { fr.ecole2recte.app.sync.CurrentCourseScheduler.refresh(context) }
        fr.ecole2recte.app.sync.AppShortcuts.refresh(context)
        updateAllWidgets(context)
    }
}
