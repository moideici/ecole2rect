package fr.ecole2recte.app.ui.homework

import fr.ecole2recte.app.domain.Homework

/** Ce que montre la liste des devoirs : à faire, faits, contrôles ou tout. Retenu par profil. */
enum class HomeworkFilter(val label: String, val emptyTitle: String, val emptyText: String) {
    All("Tous", "Rien à faire", ""),
    Todo("À faire", "Tout est fait", ""),
    Done("Faits", "Rien de fait", ""),
    Exams("Contrôles", "Aucun contrôle", ""),
    ;

    fun accepts(task: Homework): Boolean = when (this) {
        All -> true
        Todo -> !task.done
        Done -> task.done
        Exams -> task.exam
    }

    companion object {
        /** Retrouve un filtre retenu ; une valeur inconnue ou absente donne [All]. */
        fun parse(name: String?): HomeworkFilter = entries.firstOrNull { it.name == name } ?: All
    }
}

fun filterHomework(items: List<Homework>, filter: HomeworkFilter): List<Homework> = items.filter(filter::accepts)

/** Le nombre de devoirs que chaque puce afficherait. */
fun countByFilter(items: List<Homework>): Map<HomeworkFilter, Int> =
    HomeworkFilter.entries.associateWith { filter -> items.count(filter::accepts) }
