package fr.ecole2recte.app.core.repo

import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.format.Text
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.settings.AppSettings
import java.time.LocalDate

/**
 * Recherche globale.
 *
 * Une application scolaire répond mal à « où est ce fichu devoir de maths ? » quand chaque
 * écran a sa propre petite loupe. Ici, une seule question interroge tout : cours, devoirs,
 * notes, messages et vie scolaire, avec un ordre de pertinence simple — ce qui est proche
 * dans le temps d'abord, parce qu'on cherche presque toujours quelque chose de récent.
 */
object Search {

    enum class HitKind(val label: String) {
        LESSON("Cours"),
        HOMEWORK("Devoir"),
        GRADE("Note"),
        MESSAGE("Message"),
        SCHOOL_LIFE("Vie scolaire"),
    }

    data class Hit(
        val kind: HitKind,
        val id: String,
        val title: String,
        val subtitle: String,
        val subject: String,
        val date: LocalDate?,
    )

    /** Au-delà, ce n'est plus une réponse mais une deuxième liste à parcourir. */
    private const val MAX_PER_KIND = 5

    fun query(
        snapshot: SchoolSnapshot,
        settings: AppSettings,
        query: String,
        today: LocalDate,
    ): List<Hit> {
        val needle = Text.normalizeKey(query)
        if (needle.length < 2) return emptyList()

        val lessons = snapshot.lessons
            .filter { Insights.matches(query, it.subject, it.teacher, it.room) }
            .sortedBy { distance(it.date, today) }
            .take(MAX_PER_KIND)
            .map {
                Hit(
                    kind = HitKind.LESSON,
                    id = it.id,
                    title = it.subject,
                    subtitle = "${Dates.dayAndMonth(it.date)} · ${Dates.range(it.start, it.end)}",
                    subject = it.subject,
                    date = it.date,
                )
            }

        val homework = snapshot.homework
            .filter { Insights.matches(query, it.subject, it.content) }
            .sortedBy { distance(it.dueDate, today) }
            .take(MAX_PER_KIND)
            .map {
                Hit(
                    kind = HitKind.HOMEWORK,
                    id = it.id,
                    title = it.subject,
                    subtitle = listOfNotNull(
                        Dates.dueLabel(it.dueDate, today),
                        Text.preview(it.content, 60).takeIf { preview -> preview.isNotBlank() },
                    ).joinToString(" · "),
                    subject = it.subject,
                    date = it.dueDate,
                )
            }

        val grades = snapshot.grades
            .filter { Insights.matches(query, it.subject, it.label) }
            .take(MAX_PER_KIND)
            .map {
                Hit(
                    kind = HitKind.GRADE,
                    id = it.id,
                    title = "${it.subject} · ${it.label}",
                    subtitle = it.value?.let { value -> "$value / ${it.scale}" } ?: "sans note",
                    subject = it.subject,
                    date = it.date,
                )
            }

        val messages = snapshot.messages
            .filter { Insights.matches(query, it.subject, it.sender, it.preview, it.body) }
            .take(MAX_PER_KIND)
            .map {
                Hit(
                    kind = HitKind.MESSAGE,
                    id = it.id,
                    title = it.subject,
                    subtitle = it.sender,
                    subject = it.sender,
                    date = it.date?.toLocalDate(),
                )
            }

        val schoolLife = snapshot.schoolLife
            .filter { Insights.matches(query, it.kind.label, it.reason, it.comment) }
            .take(MAX_PER_KIND)
            .map {
                Hit(
                    kind = HitKind.SCHOOL_LIFE,
                    id = it.id,
                    title = it.kind.label,
                    subtitle = listOfNotNull(
                        Dates.dayAndMonth(it.date),
                        it.reason.takeIf { reason -> reason.isNotBlank() },
                    ).joinToString(" · "),
                    subject = it.kind.label,
                    date = it.date,
                )
            }

        return lessons + homework + grades + messages + schoolLife
    }

    private fun distance(date: LocalDate, today: LocalDate): Long =
        kotlin.math.abs(date.toEpochDay() - today.toEpochDay())
}
