package fr.ecole2recte.app.core.repo

import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.format.Text
import fr.ecole2recte.app.core.model.Change
import fr.ecole2recte.app.core.model.ChangeKind
import fr.ecole2recte.app.core.model.Lesson
import fr.ecole2recte.app.core.model.SchoolSnapshot
import java.time.LocalDate

/**
 * Ce qui a changé, d'une synchronisation à l'autre.
 *
 * L'application connaît déjà l'état d'hier — le cache — et celui d'aujourd'hui. La différence
 * est exactement ce qu'on veut savoir en ouvrant le téléphone : un cours annulé, une salle
 * déplacée, une note tombée. Le reste, on l'a déjà lu.
 *
 * Trois précautions, sans lesquelles la fonctionnalité serait pire qu'absente.
 *
 * **Rien au premier passage.** Une section vide avant et pleine après n'a pas changé : elle
 * vient d'arriver. Chaque famille de données est donc comparée seulement si elle existait
 * déjà, sinon la première synchronisation annoncerait trente nouveautés d'un coup.
 *
 * **Rien sur un jour qu'on ne connaissait pas.** L'emploi du temps se charge semaine par
 * semaine : ouvrir la semaine prochaine ferait apparaître trente « cours ajouté » si l'on ne
 * s'en tenait pas aux journées déjà connues des deux côtés.
 *
 * **Rien sur le passé.** Un cours annulé la semaine dernière n'appelle aucune action.
 */
object Changes {

    /** Au-delà d'une semaine, ce n'est plus une nouvelle mais de l'archive. */
    const val HISTORY_DAYS = 7L

    /** Assez pour une semaine chargée, assez peu pour que le cache reste petit. */
    const val MAX_ENTRIES = 60

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    fun between(
        before: SchoolSnapshot,
        after: SchoolSnapshot,
        today: LocalDate,
        atMillis: Long,
    ): List<Change> {
        if (before.isEmpty) return emptyList()
        val stamp = Stamp(today, atMillis)
        return buildList {
            addAll(lessons(before, after, stamp))
            addAll(grades(before, after, stamp))
            addAll(homework(before, after, stamp))
            addAll(messages(before, after, stamp))
            addAll(schoolLife(before, after, stamp))
        }.sortedBy { it.kind.ordinal }
    }

    /**
     * Ajoute les nouveautés à l'historique, sans doublon, et oublie ce qui a plus d'une
     * semaine. L'ordre est le plus récent d'abord : c'est celui de la lecture.
     */
    fun merge(existing: List<Change>, fresh: List<Change>, atMillis: Long): List<Change> {
        val known = existing.mapTo(mutableSetOf()) { it.id }
        val cutoff = atMillis - HISTORY_DAYS * DAY_MILLIS
        return (fresh.filterNot { it.id in known } + existing)
            .filter { it.atMillis >= cutoff }
            .sortedWith(compareByDescending<Change> { it.atMillis }.thenBy { it.kind.ordinal })
            .take(MAX_ENTRIES)
    }

    private data class Stamp(val today: LocalDate, val atMillis: Long)

    private fun Stamp.change(
        id: String,
        kind: ChangeKind,
        text: String,
        detail: String = "",
        subject: String = "",
    ) = Change(
        id = id,
        kind = kind,
        text = text,
        detail = detail,
        subject = subject,
        noticedOn = today,
        atMillis = atMillis,
    )

    // ── Emploi du temps ──────────────────────────────────────────────────────

    private fun lessons(before: SchoolSnapshot, after: SchoolSnapshot, stamp: Stamp): List<Change> {
        if (before.lessons.isEmpty()) return emptyList()
        val old = before.lessons.associateBy { it.id }
        val new = after.lessons.associateBy { it.id }
        // Une journée n'est comparable que si les deux instantanés la couvrent : sinon une
        // semaine fraîchement chargée passerait entière pour une avalanche d'ajouts.
        val shared = before.lessons.mapTo(mutableSetOf()) { it.date }
            .intersect(after.lessons.mapTo(mutableSetOf()) { it.date })

        return buildList {
            new.values
                .filter { it.date in shared && !it.date.isBefore(stamp.today) }
                .forEach { lesson ->
                    val previous = old[lesson.id]
                    when {
                        previous == null -> add(
                            stamp.change(
                                id = "cours+:${lesson.id}",
                                kind = ChangeKind.LESSON_ADDED,
                                text = "${lesson.subject} ajouté",
                                detail = when_(lesson, stamp.today),
                                subject = lesson.subject,
                            ),
                        )

                        !previous.canceled && lesson.canceled -> add(
                            stamp.change(
                                id = "annule:${lesson.id}",
                                kind = ChangeKind.LESSON_CANCELED,
                                text = "${lesson.subject} ${Dates.time(lesson.start)} annulé",
                                detail = when_(lesson, stamp.today),
                                subject = lesson.subject,
                            ),
                        )

                        previous.room != lesson.room && lesson.room.isNotBlank() -> add(
                            stamp.change(
                                // La salle est dans l'identifiant : un second déplacement
                                // est un second changement, pas une répétition du premier.
                                id = "salle:${lesson.id}:${lesson.room}",
                                kind = ChangeKind.LESSON_MOVED,
                                text = "${lesson.subject} déplacé en ${lesson.room}",
                                detail = when_(lesson, stamp.today),
                                subject = lesson.subject,
                            ),
                        )
                    }
                }

            old.values
                .filter { it.date in shared && !it.date.isBefore(stamp.today) && it.id !in new }
                .forEach { lesson ->
                    add(
                        stamp.change(
                            id = "cours-:${lesson.id}",
                            kind = ChangeKind.LESSON_REMOVED,
                            text = "${lesson.subject} ${Dates.time(lesson.start)} retiré",
                            detail = when_(lesson, stamp.today),
                            subject = lesson.subject,
                        ),
                    )
                }
        }
    }

    /** « aujourd'hui · 10:10 », « jeudi 11 · 13:05 » — quand, en trois mots. */
    private fun when_(lesson: Lesson, today: LocalDate): String =
        "${Dates.relativeDay(lesson.date, today)} · ${Dates.time(lesson.start)}"

    // ── Notes, devoirs, messages, vie scolaire ───────────────────────────────

    private fun grades(before: SchoolSnapshot, after: SchoolSnapshot, stamp: Stamp): List<Change> {
        if (before.grades.isEmpty()) return emptyList()
        val known = before.grades.mapTo(mutableSetOf()) { it.id }
        return after.grades
            .filter { it.id !in known && it.value != null }
            .map { grade ->
                stamp.change(
                    id = "note:${grade.id}",
                    kind = ChangeKind.GRADE,
                    text = "Note en ${grade.subject} · ${Text.number(grade.value ?: 0.0)}",
                    detail = listOfNotNull(
                        grade.label.takeIf { it.isNotBlank() },
                        "coef. ${Text.number(grade.coefficient)}".takeIf { grade.coefficient != 1.0 },
                    ).joinToString(" · "),
                    subject = grade.subject,
                )
            }
    }

    private fun homework(before: SchoolSnapshot, after: SchoolSnapshot, stamp: Stamp): List<Change> {
        if (before.homework.isEmpty()) return emptyList()
        val known = before.homework.mapTo(mutableSetOf()) { it.id }
        return after.homework
            .filter { it.id !in known && !it.dueDate.isBefore(stamp.today) }
            .map { item ->
                stamp.change(
                    id = "devoir:${item.id}",
                    kind = ChangeKind.HOMEWORK,
                    text = if (item.exam) {
                        "Évaluation annoncée en ${item.subject}"
                    } else {
                        "Nouveau devoir en ${item.subject}"
                    },
                    detail = Dates.dueLabel(item.dueDate, stamp.today),
                    subject = item.subject,
                )
            }
    }

    private fun messages(before: SchoolSnapshot, after: SchoolSnapshot, stamp: Stamp): List<Change> {
        if (before.inbox.isEmpty()) return emptyList()
        val known = before.inbox.mapTo(mutableSetOf()) { it.id }
        return after.inbox
            .filter { it.id !in known }
            .map { message ->
                stamp.change(
                    id = "message:${message.id}",
                    kind = ChangeKind.MESSAGE,
                    text = "Message de ${message.sender}",
                    detail = Text.preview(message.subject, 60),
                )
            }
    }

    private fun schoolLife(before: SchoolSnapshot, after: SchoolSnapshot, stamp: Stamp): List<Change> {
        if (before.schoolLife.isEmpty()) return emptyList()
        val known = before.schoolLife.mapTo(mutableSetOf()) { it.id }
        return after.schoolLife
            .filter { it.id !in known }
            .map { event ->
                stamp.change(
                    id = "vie:${event.id}",
                    kind = ChangeKind.SCHOOL_LIFE,
                    text = "${event.kind.label} · ${Dates.relativeDay(event.date, stamp.today)}",
                    detail = event.reason.ifBlank { event.label },
                )
            }
    }
}
