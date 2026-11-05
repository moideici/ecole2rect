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
    Homework("homework", "Devoirs pour demain", "agenda_homework"),
    Grade("grades", "Nouvelles notes", "notes"),
    Message("messages", "Messages", "messages"),
}

data class NotificationEvent(val kind: EventKind, val id: String, val title: String, val text: String)

/** Ce qui a déjà été notifié, pour ne jamais notifier deux fois la même chose. */
data class NotifiedIds(
    val grades: Set<String> = emptySet(),
    val messages: Set<String> = emptySet(),
    val canceled: Set<String> = emptySet(),
    val homework: Set<String> = emptySet(),
)

/** Ce que la synchronisation a lu, réduit à ce qui peut déclencher une notification. */
data class SyncSnapshot(
    val grades: List<GradeEntry>,
    val unreadMessages: List<MessagePreview>,
    val canceledLessons: List<ScheduleEntry>,
    val homeworkTomorrow: List<Homework>,
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
        )
    }

    fun plan(previous: NotifiedIds?, snapshot: SyncSnapshot, today: LocalDate = LocalDate.now()): PlanResult {
        val gradeIds = snapshot.grades.associateBy { it.id }
        val messageIds = snapshot.unreadMessages.associateBy { messageKey(it) }
        val canceledIds = snapshot.canceledLessons.associateBy { canceledKey(it) }
        val homeworkIds = snapshot.homeworkTomorrow.associateBy { homeworkKey(it) }

        val current = NotifiedIds(gradeIds.keys, messageIds.keys, canceledIds.keys, homeworkIds.keys)
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
        }
        // On garde aussi ce qui a déjà été notifié et qui a disparu : un message relu puis redevenu
        // non lu, ou un cours annulé puis rétabli, ne doit pas notifier une seconde fois.
        val next = NotifiedIds(
            grades = merge(previous.grades, current.grades),
            messages = merge(previous.messages, current.messages),
            canceled = merge(previous.canceled, current.canceled),
            homework = merge(previous.homework, current.homework),
        )
        return PlanResult(events, next)
    }

    private fun merge(old: Set<String>, new: Set<String>): Set<String> = (old + new).toList().takeLast(HISTORY).toSet()

    private fun messageKey(message: MessagePreview) = message.id.ifBlank { "${message.sender}|${message.preview}" }
    private fun canceledKey(entry: ScheduleEntry) = "${entry.date}|${entry.start}|${entry.subject.name}"
    private fun homeworkKey(task: Homework) = "${task.dueDate}|${task.id}"

    /** 16.0 devient « 16 », 15.5 devient « 15,5 ». */
    private fun number(value: Double): String =
        String.format(Locale.FRENCH, "%.1f", value).removeSuffix(",0")
}
