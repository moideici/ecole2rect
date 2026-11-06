package fr.ecole2recte.app.sync

import fr.ecole2recte.app.domain.GradeEntry
import fr.ecole2recte.app.domain.GradeOverview
import fr.ecole2recte.app.domain.Homework
import fr.ecole2recte.app.domain.MessagePreview
import fr.ecole2recte.app.domain.LessonChange
import fr.ecole2recte.app.domain.LessonChangeKind
import fr.ecole2recte.app.domain.ScheduleChanges
import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.domain.Subject
import fr.ecole2recte.app.domain.ScheduleWeek
import java.time.LocalDate
import java.util.Locale

/** Les familles de notifications ; [tab] est l'onglet que le toucher ouvre. */
enum class EventKind(
    val channelId: String,
    val channelName: String,
    val tab: String,
    /** Titre affiché sur l'écran verrouillé, sans aucun détail. */
    val publicTitle: String,
    /** Cours et devoirs sont discrets ; notes et messages se remarquent. */
    val quiet: Boolean,
) {
    Canceled("canceled2", "Cours annulés", "agenda", "Cours annulé", quiet = true),
    Changed("changed2", "Cours modifiés", "agenda", "Cours modifié", quiet = true),
    Homework("homework2", "Devoirs pour demain", "agenda_homework", "Devoir pour demain", quiet = true),
    Grade("grades", "Nouvelles notes", "notes", "Nouvelle note", quiet = false),
    Message("messages", "Messages", "messages", "Nouveau message", quiet = false),
    ;

    companion object {
        /** Anciens canaux (importance normale pour tout) : supprimés au profit des nouveaux. */
        val legacyChannelIds = listOf("canceled", "changed", "homework")
    }
}

/** Une nouvelle note n'est annoncée que si elle date de moins de deux semaines : l'historique d'avant n'est pas « nouveau ». */
const val NEW_GRADE_DAYS = 14L

/** Au-delà, les nouvelles notes sont regroupées en une seule notification. */
const val MAX_GRADE_EVENTS = 4

data class NotificationEvent(
    val kind: EventKind,
    val id: String,
    val title: String,
    val text: String,
    /** L'enfant concerné pour un compte famille (sous-titre de la notification) ; vide pour un compte élève. */
    val owner: String = "",
)

/** Ce que l'utilisateur veut recevoir. Chaque famille se règle à part dans Plus. */
data class NotificationRules(
    /** Cours annulé, modifié (salle, professeur, horaire) ou rétabli. */
    val schedule: Boolean = true,
    val grades: Boolean = true,
    val messages: Boolean = true,
    /** Devoir à rendre pour demain, non fait : un rappel du soir. */
    val homework: Boolean = true,
    /** Aucune notification entre 22 h et 7 h : elles attendent le matin. */
    val quietNight: Boolean = true,
    /** Heure à partir de laquelle le rappel des devoirs pour demain peut partir (16 à 20 h). */
    val eveningHour: Int = DEFAULT_EVENING_HOUR,
) {
    fun allows(kind: EventKind): Boolean = when (kind) {
        EventKind.Canceled, EventKind.Changed -> schedule
        EventKind.Grade -> grades
        EventKind.Message -> messages
        EventKind.Homework -> homework
    }

    /** Vrai entre 22 h et 7 h quand le mode nuit est demandé. */
    fun isQuiet(hour: Int): Boolean = quietNight && (hour >= 22 || hour < 7)

    /** Vrai quand le rappel du soir peut partir à [hour] ; `null` : l'heure n'est pas connue, pas de restriction. */
    fun reminderOpen(hour: Int?): Boolean = hour == null || hour >= eveningHour

    companion object {
        const val DEFAULT_EVENING_HOUR = 18

        /** Les heures proposées dans Plus. */
        val EVENING_HOURS = listOf(16, 17, 18, 19, 20)

        /** Ramène une valeur lue (réglage ancien ou abîmé) à l'une des heures proposées. */
        fun clampEveningHour(hour: Int): Int = hour.coerceIn(EVENING_HOURS.first(), EVENING_HOURS.last())
    }
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
 *
 * Le rappel des devoirs pour demain est un rappel **du soir** : avant l'heure choisie
 * ([NotificationRules.eveningHour]) il n'envoie rien **et ne retient rien**, de sorte que le premier
 * passage après cette heure le déclenche une fois, sans doublon ensuite. Aucune heure exacte n'est
 * garantie : il part au premier passage de la synchronisation qui suit l'heure choisie.
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
            grades = grades.all.ifEmpty { grades.recent }.filter { it.value != null },
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
        /** Heure locale (0 à 23) du passage ; `null` : pas de restriction horaire pour le rappel du soir. */
        hour: Int? = null,
    ): PlanResult {
        val gradeIds = snapshot.grades.associateBy { it.id }
        val messageIds = snapshot.unreadMessages.associateBy { messageKey(it) }
        val canceledIds = snapshot.canceledLessons.associateBy { canceledKey(it) }
        val homeworkIds = snapshot.homeworkTomorrow.associateBy { homeworkKey(it) }

        val fingerprints = snapshot.lessons.associate { lessonKey(it) to fingerprint(it) }
        // Avant l'heure du rappel, les devoirs pour demain ne sont ni annoncés ni retenus : ils seront vus ce soir.
        val reminderOpen = rules.reminderOpen(hour)
        val homeworkSeen = if (reminderOpen) homeworkIds.keys else emptySet()
        val current = NotifiedIds(gradeIds.keys, messageIds.keys, canceledIds.keys, homeworkSeen, fingerprints)
        if (previous == null) return PlanResult(emptyList(), current)

        // Ce qui a bougé depuis le dernier passage, pour aujourd'hui et demain seulement. On compare avec
        // les cours retenus la fois d'avant (début, matière, salle, professeur, fin) : pas de premier passage,
        // et rien tant qu'on n'a pas de quoi comparer.
        val changes = if (previous.lessons.isEmpty()) {
            emptyList()
        } else {
            ScheduleChanges.diff(rememberedLessons(previous.lessons), snapshot.lessons, setOf(today.toString(), today.plusDays(1).toString()))
        }
        // Un cours annulé puis reprogrammé le même jour est annoncé comme déplacé, pas aussi comme annulé.
        val movedAway = changes.filter { it.kind == LessonChangeKind.Moved }.map { canceledKey(it.before) }.toSet()

        val events = buildList {
            canceledIds.filterKeys { it !in previous.canceled && it !in movedAway }.forEach { (key, entry) ->
                val when_ = if (entry.date == today.toString()) "aujourd'hui" else "demain"
                add(NotificationEvent(EventKind.Canceled, key, "Cours annulé : ${entry.subject.name}", "$when_ à ${entry.start}"))
            }
            homeworkIds.filterKeys { reminderOpen && it !in previous.homework }.forEach { (key, task) ->
                add(
                    NotificationEvent(
                        EventKind.Homework,
                        key,
                        "Pour demain : ${task.subject}",
                        task.title.ifBlank { "Travail à faire" },
                    ),
                )
            }
            // Toutes les notes sont suivies, mais seules les récentes sont annoncées : à la première mise à jour
            // qui élargit le suivi, l'historique de l'année ne déclenche pas une rafale de notifications.
            val since = today.minusDays(NEW_GRADE_DAYS).toString()
            val fresh = gradeIds.filterKeys { it !in previous.grades }.filterValues { grade -> grade.date == null || grade.date.take(10) >= since }
            if (fresh.size > MAX_GRADE_EVENTS) {
                add(NotificationEvent(EventKind.Grade, "grades|${fresh.keys.sorted().joinToString(",")}", "${fresh.size} nouvelles notes", fresh.values.map { it.subject }.distinct().joinToString(", ")))
            } else {
                fresh.forEach { (key, grade) ->
                    add(
                        NotificationEvent(
                            EventKind.Grade,
                            key,
                            "Nouvelle note en ${grade.subject}",
                            "${number(grade.value!!)} / ${number(grade.scale)}" + grade.label.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                        ),
                    )
                }
            }
            messageIds.filterKeys { it !in previous.messages }.forEach { (key, message) ->
                add(NotificationEvent(EventKind.Message, key, message.sender.ifBlank { "Nouveau message" }, message.preview))
            }
            changes.forEach { add(describeChange(it, today)) }
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

    /** Relit les cours retenus (clé `jour|début|matière`, empreinte `annulé|modifié|salle|professeur|fin`). */
    private fun rememberedLessons(map: Map<String, String>): List<ScheduleEntry> = map.mapNotNull { (key, print) ->
        val k = key.split('|', limit = 3)
        val p = print.split('|')
        if (k.size < 3 || p.size < 5) return@mapNotNull null
        ScheduleEntry(
            subject = Subject(k[2], p[3], p[2], 0),
            whenLabel = "",
            canceled = p[0] == "true",
            date = k[0],
            start = k[1],
            end = p[4],
            modified = p[1] == "true",
        )
    }

    /** Dit en une phrase ce qui a changé : « 10:00 → 14:00 », « salle B12 → C03 », « professeur A → B ». */
    private fun describeChange(change: LessonChange, today: LocalDate): NotificationEvent {
        val entry = change.after
        val day = if (entry.date == today.toString()) "aujourd'hui" else "demain"
        val name = entry.subject.name
        val detail = change.parts.joinToString(", ")
        return when (change.kind) {
            LessonChangeKind.Moved -> NotificationEvent(
                EventKind.Changed,
                "mv|${entry.date}|${change.before.start}|${entry.start}|$name",
                "Cours déplacé : $name",
                "$day · $detail",
            )
            LessonChangeKind.Replaced -> NotificationEvent(
                EventKind.Changed,
                "rp|${entry.date}|${entry.start}|${change.before.subject.name}|$name",
                "Cours remplacé : ${change.before.subject.name} → $name",
                "$day à ${entry.start}" + change.parts.drop(1).joinToString(", ").takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty(),
            )
            LessonChangeKind.Restored, LessonChangeKind.Updated -> {
                val title = if (change.kind == LessonChangeKind.Restored) "Cours rétabli : $name" else "Cours modifié : $name"
                NotificationEvent(EventKind.Changed, "chg|${lessonKey(entry)}|${fingerprint(entry)}", title, "$day à ${entry.start} · $detail")
            }
        }
    }

    private fun messageKey(message: MessagePreview) = message.id.ifBlank { "${message.sender}|${message.preview}" }
    private fun canceledKey(entry: ScheduleEntry) = "${entry.date}|${entry.start}|${entry.subject.name}"
    private fun homeworkKey(task: Homework) = "${task.dueDate}|${task.id}"

    /** 16.0 devient « 16 », 15.5 devient « 15,5 ». */
    private fun number(value: Double): String =
        String.format(Locale.FRENCH, "%.1f", value).removeSuffix(",0")
}
