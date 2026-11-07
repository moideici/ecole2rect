package fr.ecole2recte.app.data

import fr.ecole2recte.app.data.api.ApiGrade
import fr.ecole2recte.app.data.api.ApiPeriod
import java.time.LocalDate

/**
 * Choisit la période (trimestre, semestre) à afficher.
 *
 * Les réponses réelles n'ont pas toujours de marqueur « période courante » : se fier au dernier
 * élément de la liste montrerait un trimestre qui n'a pas commencé, donc des notes et des
 * moyennes vides. On prend la période qui contient aujourd'hui **et** qui a des données ; à
 * défaut, la dernière commencée qui en a ; la période « Année » n'est jamais choisie d'office.
 */
object PeriodSelection {
    fun select(periods: List<ApiPeriod>, grades: List<ApiGrade>, today: LocalDate): ApiPeriod? {
        val regular = periods.filter { !it.annual }.ifEmpty { periods }
        val now = today.toString()
        fun hasData(period: ApiPeriod) = period.average != null || grades.any { it.periodCode == period.code }

        regular.lastOrNull { p ->
            val start = p.start
            val end = p.end
            start != null && end != null && now >= start && now <= end && hasData(p)
        }?.let { return it }
        regular.lastOrNull { it.current && hasData(it) }?.let { return it }
        regular.filter { it.start == null || it.start <= now }.lastOrNull { hasData(it) }?.let { return it }
        return regular.lastOrNull { hasData(it) } ?: regular.lastOrNull()
    }
}
