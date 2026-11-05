package fr.ecole2recte.app.sync

import fr.ecole2recte.app.domain.GradeEntry
import fr.ecole2recte.app.domain.GradeOverview
import fr.ecole2recte.app.domain.Homework
import fr.ecole2recte.app.domain.MessagePreview
import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.domain.ScheduleWeek
import java.time.LocalDate
import java.util.Locale

/** Les familles de notifications ; [tab] est l'onglet que le toucher ouvre. */
enum class EventKind(val channelId: String, val channelName: String, val tab: String) {
    Canceled("canceled", "Cours annulés", "agenda"),
    Changed("changed", "Cours modifiés", "agenda"),
    Homework("homework", "Devoirs pour demain", "agenda_homework"),
    Grade("grades", "Nouvelles notes", "notes"),
    Message("messages", "Messages", "messages"),
}

data class NotificationEvent(val kind: EventKind, val id: String, val title: String, val text: String)

/** Ce que l'utilisateur veut recevoir. Chaque famille se règle à part dans Plus. */
data class NotificationRules(
    /** Cours annulé, modifié (salle, professeur, horaire) ou rétabli. */
    val schedule: Boolean = true,
    val grades: Boolean = true,
    val messages: Boolean = true,
    /** Devoir à rendre pour demain. */
    val homework: Boolean = true,
    /** Aucune notification entre 22 h et 7 h : elles attendent le matin. */
    val quietNight: Boolean = true,
) {
    fun allows(kind: EventKind): Boolean = when (kind) {
        EventKind.Canceled, EventKind.Changed -> schedule
        EventKind.Grade -> grades
        EventKind.Message -> messages
        EventKind.Homework -> homework
    }

    /** Vrai entre 22 h et 7 h quand le mode nuit est demandé. */
    fun isQuiet(hour: Int): Boolean = quietNight && (hour >= 22 || hour < 7)
}

/** Ce qui a déjà été notifié, pour ne jamais notifier deux fois la même chose. */
data class NotifiedIds(
    val grades: Set<String> = emptySet(),
    val messages: Set<String> = emptySet(),
    val canceled: Set<String> = emptySet(),
    val homework: Set<String> = emptySet(),
    /**
     * Empreinte de chaque cours connu (salle, professeur, fin, annulé, modifié), par jour, heure et
     * matière : c'est en la comparant d'un passage à l'autre qu'on voit un changement.
     */
    val lessons: Map<String, String> = emptyMap(),
)

/** Ce que la synchronisation a lu, réduit à ce qui peut déclencher une notification. */
data class SyncSnapshot(
    val grades: List<GradeEntry>,
    val unreadMessages: List<MessagePreview>,
    val canceledLessons: List<ScheduleEntry>,
    val homeworkTomorrow: List<Homework>,
    /** Tous les cours lus (deux semaines) : sert à repérer les changements. */
    val lessons: List<ScheduleEntry> = emptyList(),
)

data class PlanResult(val events: List<NotificationEvent>, val next: NotifiedIds)

/**
 * Décide quoi notifier. Fonction pure : aucun accès au réseau, à Android ni à l'horloge.
 *
 * Le premier passage (`previous == null`) n'envoie **rien** : il se contente de retenir ce qui
 * existe déjà, sinon l'activation des notifications déverserait toute la boîte et tout le
 * bulletin d'un coup.
 */
object NotificationPlanner {
    private const val HISTORY = 400

    fun snapshot(
        grades: GradeOverview,
        messages: List<MessagePreview>,
        week: ScheduleWeek,
        homework: List<Homework>,
        today: LocalDate,
    ): SyncSnapshot {
        val tomorrow = today.plusDays(1).toString()
        val soon = setOf(today.toString(), tomorrow)
        return SyncSnapshot(
            grades = grades.recent.filter { it.value != null },
            unreadMessages = messages.filter { it.unread },
            canceledLessons = week.entries.filter { it.canceled && it.date in soon },
            homeworkTomorrow = homework.filter { !it.done && it.dueDate == tomorrow },
            lessons = week.entries,
        )
    }

    fun plan(
        previous: NotifiedIds?,
        snapshot: SyncSnapshot,
        today: LocalDate = LocalDate.now(),
        rules: NotificationRules = NotificationRules(),
    ): PlanResult {
        val gradeIds = snapshot.grades.associateBy { it.id }
        val messageIds = snapshot.unreadMessages.associateBy { messageKey(it) }
        val canceledIds = snapshot.canceledLessons.associateBy { canceledKey(it) }
        val homeworkIds = snapshot.homeworkTomorrow.associateBy { homeworkKey(it) }

        val fingerprints = snapshot.lessons.associate { lessonKey(it) to fingerprint(it) }
        val current = NotifiedIds(gradeIds.keys, messageIds.keys, canceledIds.keys, homeworkIds.keys, fingerprints)
        if (previous == null) return PlanResult(emptyList(), current)

        val events = buildList {
            canceledIds.filterKeys { it !in previous.canceled }.forEach { (key, entry) ->
                val when_ = if (entry.date == today.toString()) "aujourd'hui" else "demain"
                add(NotificationEvent(EventKind.Canceled, key, "Cours annulé : ${entry.subject.name}", "$when_ à ${entry.start}"))
            }
            homeworkIds.filterKeys { it !in previous.homework }.forEach { (key, task) ->
                add(
                    NotificationEvent(
                        EventKind.Homework,
                        key,
                        "Pour demain : ${task.subject}",
                        task.title.ifBlank { "Travail à faire" },
                    ),
                )
            }
            gradeIds.filterKeys { it !in previous.grades }.forEach { (key, grade) ->
                add(
                    NotificationEvent(
                        EventKind.Grade,
                        key,
                        "Nouvelle note en ${grade.subject}",
                        "${number(grade.value!!)} / ${number(grade.scale)}" + grade.label.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                    ),
                )
            }
            messageIds.filterKeys { it !in previous.messages }.forEach { (key, message) ->
                add(NotificationEvent(EventKind.Message, key, message.sender.ifBlank { "Nouveau message" }, message.preview))
            }
            // Un cours qui change : seulement pour aujourd'hui et demain, et pas au premier passage
            // après une mise à jour (rien à comparer encore).
            if (previous.lessons.isNotEmpty()) {
                val soon = setOf(today.toString(), today.plusDays(1).toString())
                snapshot.lessons.filter { it.date in soon }.forEach { entry ->
                    val before = previous.lessons[lessonKey(entry)] ?: return@forEach
                    val after = fingerprint(entry)
                    if (before == after) return@forEach
                    describeChange(before, after, entry, today)?.let { add(it) }
                }
            }
        }.filter { rules.allows(it.kind) }
        // On garde aussi ce qui a déjà été notifié et qui a disparu : un message relu puis redevenu
        // non lu, ou un cours annulé puis rétabli, ne doit pas notifier une seconde fois.
        val next = NotifiedIds(
            grades = merge(previous.grades, current.grades),
            messages = merge(previous.messages, current.messages),
            canceled = merge(previous.canceled, current.canceled),
            homework = merge(previous.homework, current.homework),
            lessons = current.lessons,
        )
        return PlanResult(events, next)
    }

    private fun merge(old: Set<String>, new: Set<String>): Set<String> = (old + new).toList().takeLast(HISTORY).toSet()

    private fun lessonKey(entry: ScheduleEntry) = "${entry.date}|${entry.start}|${entry.subject.name}"

    /** annulé | modifié | salle | professeur | fin */
    private fun fingerprint(entry: ScheduleEntry) =
        "${entry.canceled}|${entry.modified}|${entry.subject.room}|${entry.subject.teacher}|${entry.end}"

    /** Dit en une phrase ce qui a changé ; `null` pour un cours qui vient d'être annulé (déjà couvert ailleurs). */
    private fun describeChange(before: String, after: String, entry: ScheduleEntry, today: LocalDate): NotificationEvent? {
        val old = before.split('|')
        val new = after.split('|')
        if (old.size < 5 || new.size < 5) return null
        val day = if (entry.date == today.toString()) "aujourd'hui" else "demain"
        val where = "$day à ${entry.start}"
        val changes = mutableListOf<String>()
        when {
            old[0] == "true" && new[0] == "false" -> changes += "cours rétabli"
            new[0] == "true" -> return null
        }
        if (old[2] != new[2] && new[2].isNotBlank()) changes += if (old[2].isBlank()) "salle ${new[2]}" else "salle ${old[2]} → ${new[2]}"
        if (old[3] != new[3] && new[3].isNotBlank()) changes += if (old[3].isBlank()) "avec ${new[3]}" else "professeur ${old[3]} → ${new[3]}"
        if (old[4] != new[4]) changes += "fin à ${new[4]}"
        if (changes.isEmpty() && new[1] == "true" && old[1] != "true") changes += "modifié"
        if (changes.isEmpty()) return null
        val title = if (changes.first() == "cours rétabli") "Cours rétabli : ${entry.subject.name}" else "Cours modifié : ${entry.subject.name}"
        return NotificationEvent(EventKind.Changed, "chg|${lessonKey(entry)}|$after", title, "$where · " + changes.joinToString(", "))
    }

    private fun messageKey(message: MessagePreview) = message.id.ifBlank { "${message.sender}|${message.preview}" }
    private fun canceledKey(entry: ScheduleEntry) = "${entry.date}|${entry.start}|${entry.subject.name}"
    private fun homeworkKey(task: Homework) = "${task.dueDate}|${task.id}"

    /** 16.0 devient « 16 », 15.5 devient « 15,5 ». */
    private fun number(value: Double): String =
        String.format(Locale.FRENCH, "%.1f", value).removeSuffix(",0")
}
