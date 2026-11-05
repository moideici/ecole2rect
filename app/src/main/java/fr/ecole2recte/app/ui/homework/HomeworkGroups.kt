package fr.ecole2recte.app.ui.homework

import fr.ecole2recte.app.domain.Homework
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class HomeworkGroup(val title: String, val items: List<Homework>)

/**
 * Regroupe les devoirs par échéance : aujourd'hui, demain, cette semaine, plus tard.
 * Ceux qui sont faits vont à la fin, sous « Faits », pour pouvoir être décochés.
 * [subject] limite la liste à une matière.
 */
fun groupHomework(items: List<Homework>, today: LocalDate, subject: String? = null): List<HomeworkGroup> {
    val visible = items.filter { subject == null || it.subject == subject }.sortedBy { it.dueDate }
    val endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val pending = visible.filter { !it.done }
    val groups = listOf(
        "Aujourd'hui" to pending.filter { it.dueDate == today.toString() },
        "Demain" to pending.filter { it.dueDate == today.plusDays(1).toString() },
        "Cette semaine" to pending.filter {
            it.dueDate > today.plusDays(1).toString() && it.dueDate <= endOfWeek.toString()
        },
        "Plus tard" to pending.filter { it.dueDate > endOfWeek.toString() && it.dueDate > today.plusDays(1).toString() },
        "Faits" to visible.filter { it.done },
    )
    return groups.filter { it.second.isNotEmpty() }.map { HomeworkGroup(it.first, it.second) }
}
