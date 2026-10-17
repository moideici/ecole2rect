package fr.ecole2recte.app.core.notify

import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.format.Text
import fr.ecole2recte.app.core.model.ChangeKind
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.repo.Insights
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.core.widget.WidgetContent
import fr.ecole2recte.app.core.widget.WidgetTarget
import java.time.LocalDateTime

/** Les six familles de notifications, chacune sur son canal Android. */
enum class NotificationKind(val channelId: String, val channelName: String, val channelDescription: String) {
    GRADE("notes", "Nouvelles notes", "Quand une note est publiée."),
    HOMEWORK("devoirs", "Nouveaux devoirs", "Quand un devoir est ajouté au cahier de textes."),
    MESSAGE("messages", "Nouveaux messages", "Quand un message arrive dans la messagerie."),
    EXAM("evaluations", "Rappels d'évaluation", "La veille d'un devoir surveillé annoncé."),
    TIMETABLE("emploi-du-temps", "Emploi du temps", "Quand un cours est annulé, déplacé ou ajouté."),
    TOMORROW("demain", "Rappel du soir", "Les cours de demain et ce qu'il faut emporter."),
}

data class PlannedNotification(
    /** Identifiant stable : c'est lui qui garantit qu'on ne signale pas deux fois la même chose. */
    val id: String,
    val kind: NotificationKind,
    val title: String,
    val text: String,
    val target: WidgetTarget,
)

/**
 * Décide ce qui mérite une notification.
 *
 * Deux règles tiennent tout le fichier :
 *
 *  1. **rien deux fois** — chaque candidat porte un identifiant stable, comparé à ce qui a
 *     déjà été signalé ;
 *  2. **jamais de rafale au premier lancement** — une première synchronisation découvre
 *     trente notes et cinquante messages d'un coup ; les signaler apprendrait à l'élève à
 *     balayer sans lire. Le premier passage ne fait que mémoriser.
 *
 * La fonction est pure : elle prend l'état, elle rend une liste. Le travail de fond n'a plus
 * qu'à l'afficher, ce qui rend ces règles testables sans Android.
 */
object NotificationPlan {

    /** Ce qu'il faut afficher, et ce qu'il faut retenir comme déjà vu. */
    data class Plan(
        val notifications: List<PlannedNotification>,
        val seen: List<String>,
    )

    /** Au-delà, ce n'est plus une information mais une avalanche. */
    private const val MAX_PER_KIND = 3

    fun compute(snapshot: SchoolSnapshot, settings: AppSettings, now: LocalDateTime): Plan {
        val today = now.toLocalDate()
        val candidates = buildList {
            if (settings.notifications.grades) addAll(grades(snapshot))
            if (settings.notifications.homework) addAll(homework(snapshot, settings, today))
            if (settings.notifications.messages) addAll(messages(snapshot, settings))
            if (settings.notifications.examReminder) addAll(examReminders(snapshot, settings, today))
            if (settings.notifications.timetable) addAll(timetable(snapshot))
            if (settings.notifications.tomorrow) addAll(tomorrow(snapshot, settings, now))
        }
        val seen = candidates.map { it.id }
        if (!settings.notifications.enabled) return Plan(emptyList(), seen)

        // Premier passage : on mémorise sans rien afficher.
        val baseline = settings.notified.isEmpty()
        if (baseline) return Plan(emptyList(), seen)

        val fresh = candidates.filterNot { settings.hasBeenNotified(it.id) }
        return Plan(
            notifications = fresh.groupBy { it.kind }
                .flatMap { (_, byKind) -> byKind.take(MAX_PER_KIND) },
            seen = seen,
        )
    }

    /**
     * Un cours annulé, déplacé ou ajouté.
     *
     * C'est la seule notification qui ne naît pas d'une donnée mais d'une **différence** :
     * elle relit l'historique tenu par [fr.ecole2recte.app.core.repo.Changes] au lieu de
     * recalculer sa propre comparaison. Un seul endroit décide de ce qui a changé.
     */
    private fun timetable(snapshot: SchoolSnapshot) = snapshot.changes
        .filter { it.kind in TIMETABLE_CHANGES }
        .map { change ->
            PlannedNotification(
                id = "edt:${change.id}",
                kind = NotificationKind.TIMETABLE,
                title = change.kind.label,
                text = listOf(change.text, change.detail).filter { it.isNotBlank() }.joinToString(" · "),
                target = WidgetTarget.TIMETABLE,
            )
        }

    private val TIMETABLE_CHANGES = setOf(
        ChangeKind.LESSON_CANCELED,
        ChangeKind.LESSON_MOVED,
        ChangeKind.LESSON_ADDED,
        ChangeKind.LESSON_REMOVED,
    )

    /**
     * Le rappel du soir : demain en une phrase, pour préparer son sac.
     *
     * Trois précautions. Il n'arrive que **le soir** — un rappel de préparation de sac à
     * quatorze heures n'est pas un rappel, c'est du bruit. Il porte la **date du jour visé**
     * dans son identifiant, donc il ne peut pas tomber deux fois pour la même veille, même si
     * la synchronisation de fond passe quatre fois entre dix-huit et vingt-deux heures. Et il
     * vise le **prochain jour d'école**, pas le lendemain : un vendredi soir, ce qui compte
     * n'est pas le samedi vide mais le lundi.
     */
    private fun tomorrow(
        snapshot: SchoolSnapshot,
        settings: AppSettings,
        now: LocalDateTime,
    ): List<PlannedNotification> {
        if (now.hour !in EVENING_FROM..EVENING_TO) return emptyList()
        val today = now.toLocalDate()
        val date = Insights.nextLessonDay(snapshot.lessons, today) ?: return emptyList()
        val summary = Insights.daySummary(snapshot.lessons, date)
        val start = summary.start ?: return emptyList()
        val end = summary.end ?: return emptyList()
        val lessons = Insights.lessonsOn(snapshot.lessons, date)
        val homework = Insights.homeworkDueOn(snapshot.homework, date)
            .filterNot { settings.isHomeworkDone(it.id, it.done) }
        val subjects = Insights.satchel(lessons)
        return listOf(
            PlannedNotification(
                id = "demain:$date",
                kind = NotificationKind.TOMORROW,
                title = "Demain · ${Dates.dayName(date)}",
                text = listOfNotNull(
                    "${Dates.time(start)} – ${Dates.time(end)}",
                    "${summary.lessons} cours",
                    homework.size.takeIf { it > 0 }?.let { "$it à rendre" },
                    subjects.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { "à prendre : $it" },
                ).joinToString(" · "),
                target = WidgetTarget.TIMETABLE,
            ),
        )
    }

    /** Le créneau où l'on prépare son sac : ni pendant les cours, ni au milieu de la nuit. */
    private const val EVENING_FROM = 18
    private const val EVENING_TO = 22

    private fun grades(snapshot: SchoolSnapshot) = snapshot.grades
        .filter { it.value != null }
        .map { grade ->
            PlannedNotification(
                id = "note:${grade.id}",
                kind = NotificationKind.GRADE,
                title = "Nouvelle note en ${grade.subject}",
                text = buildString {
                    append(WidgetContent.number(grade.value ?: 0.0))
                    append("/")
                    append(WidgetContent.number(grade.scale))
                    if (grade.label.isNotBlank()) {
                        append(" · ")
                        append(grade.label)
                    }
                    grade.classAverage?.let {
                        append(" · classe ")
                        append(WidgetContent.number(it))
                    }
                },
                target = WidgetTarget.GRADES,
            )
        }

    private fun homework(snapshot: SchoolSnapshot, settings: AppSettings, today: java.time.LocalDate) =
        Insights.pendingHomework(snapshot.homework, settings, today)
            .filterNot { it.dueDate.isBefore(today) }
            .map { item ->
                PlannedNotification(
                    id = "devoir:${item.id}",
                    kind = NotificationKind.HOMEWORK,
                    title = if (item.exam) {
                        "Évaluation annoncée en ${item.subject}"
                    } else {
                        "Nouveau devoir en ${item.subject}"
                    },
                    text = listOfNotNull(
                        Dates.dueLabel(item.dueDate, today).replaceFirstChar { it.uppercase() },
                        Text.preview(item.content, 80).takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    target = WidgetTarget.HOMEWORK,
                )
            }

    private fun messages(snapshot: SchoolSnapshot, settings: AppSettings) =
        Insights.unreadMessages(snapshot.inbox, settings).map { message ->
            PlannedNotification(
                id = "message:${message.id}",
                kind = NotificationKind.MESSAGE,
                title = "Message de ${message.sender}",
                text = message.subject,
                target = WidgetTarget.MESSAGES,
            )
        }

    /**
     * Rappel la veille d'une évaluation. L'identifiant porte la date : le même devoir
     * surveillé reporté d'une semaine mérite un nouveau rappel, mais pas deux le même soir.
     */
    private fun examReminders(snapshot: SchoolSnapshot, settings: AppSettings, today: java.time.LocalDate) =
        Insights.upcomingExams(snapshot.homework, settings, today)
            .filter { it.dueDate == today.plusDays(1) }
            .map { exam ->
                PlannedNotification(
                    id = "rappel:${exam.id}:${exam.dueDate}",
                    kind = NotificationKind.EXAM,
                    title = "Évaluation demain en ${exam.subject}",
                    text = Text.preview(exam.content, 80).ifBlank { "Pense à réviser ce soir." },
                    target = WidgetTarget.HOMEWORK,
                )
            }
}
