package fr.ecole2recte.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Un contrôle à venir, tel que le compte à rebours l'affiche. */
data class UpcomingExam(
    val subject: String,
    /** Jour du contrôle, date ISO. */
    val date: String,
    val colorIndex: Int = 0,
    /** Début de l'énoncé, quand ÉcoleDirecte l'a donné. */
    val detail: String = "",
)

/**
 * Compte à rebours avant les prochains contrôles. Un contrôle est un devoir que le cahier de textes
 * marque comme évaluation (`Homework.exam`, lu depuis `interrogation`). L'indicateur « devoir » de
 * l'emploi du temps n'est pas utilisé : son sens n'est pas établi (il ne suffit pas à dire contrôle).
 */
object ExamCountdown {
    /** Au-delà, un contrôle est trop loin pour mériter une place à l'accueil. */
    const val HORIZON_DAYS = 60L

    /** Les contrôles de [today] à l'horizon, du plus proche au plus lointain, sans doublon (même jour et même matière). */
    fun upcoming(homework: List<Homework>, today: LocalDate, limit: Int = 3): List<UpcomingExam> =
        homework.asSequence()
            .filter { it.exam }
            .mapNotNull { task ->
                val day = runCatching { LocalDate.parse(task.dueDate) }.getOrNull() ?: return@mapNotNull null
                if (day.isBefore(today) || day.isAfter(today.plusDays(HORIZON_DAYS))) return@mapNotNull null
                UpcomingExam(task.subject, day.toString(), task.colorIndex, firstLine(task))
            }
            .distinctBy { it.date to it.subject.lowercase() }
            .sortedWith(compareBy({ it.date }, { it.subject }))
            .take(limit)
            .toList()

    fun daysUntil(exam: UpcomingExam, today: LocalDate): Int =
        ChronoUnit.DAYS.between(today, LocalDate.parse(exam.date)).toInt().coerceAtLeast(0)

    /** « aujourd'hui », « demain », « dans 3 jours ». */
    fun delay(days: Int): String = when {
        days <= 0 -> "aujourd'hui"
        days == 1 -> "demain"
        else -> "dans $days jours"
    }

    /** « Contrôle de Maths dans 3 jours ». */
    fun label(exam: UpcomingExam, today: LocalDate): String =
        "Contrôle de ${exam.subject.ifBlank { "classe" }} ${delay(daysUntil(exam, today))}"

    private fun firstLine(task: Homework): String =
        task.content.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty().take(120)
}
