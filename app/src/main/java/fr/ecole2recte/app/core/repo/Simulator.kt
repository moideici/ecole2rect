package fr.ecole2recte.app.core.repo

import fr.ecole2recte.app.core.model.Grade
import fr.ecole2recte.app.core.model.SchoolSnapshot
import java.time.LocalDate

/**
 * Simulateur de moyenne.
 *
 * « Si je prends 14 au prochain devoir de maths, ma moyenne générale devient quoi ? » est la
 * question que tout élève se pose devant ses notes, et la seule à laquelle aucune application
 * scolaire ne répond. Le calcul est celui, honnête, d'une moyenne pondérée : il vaut ce que
 * valent les règles de l'établissement, et l'interface le dit.
 */
object Simulator {

    data class Hypothesis(
        val subject: String,
        val value: Double,
        val scale: Double = 20.0,
        val coefficient: Double = 1.0,
    ) {
        val isValid: Boolean get() = scale > 0 && coefficient > 0 && value >= 0 && value <= scale
    }

    data class Outcome(
        /** Moyenne de la matière avant et après l'hypothèse. */
        val subjectBefore: Double?,
        val subjectAfter: Double?,
        val generalBefore: Double?,
        val generalAfter: Double?,
    ) {
        val subjectDelta: Double? get() = difference(subjectBefore, subjectAfter)
        val generalDelta: Double? get() = difference(generalBefore, generalAfter)

        private fun difference(before: Double?, after: Double?): Double? =
            if (before == null || after == null) null else after - before
    }

    /**
     * Applique une note hypothétique aux notes réelles de la période — celle qu'on consulte,
     * ou celle en cours à défaut.
     *
     * La moyenne générale simulée est toujours recalculée depuis les notes, jamais dérivée
     * de celle de l'établissement : mélanger les deux donnerait un chiffre qui n'est ni l'un
     * ni l'autre.
     */
    fun simulate(
        snapshot: SchoolSnapshot,
        hypothesis: Hypothesis,
        today: LocalDate,
        periodCode: String? = null,
    ): Outcome? {
        if (!hypothesis.isValid) return null
        // Même raison que pour l'objectif : le simulateur doit porter sur la période qu'on
        // regarde, pas sur celle du jour.
        val period = periodCode?.let { code -> snapshot.periods.firstOrNull { it.code == code } }
            ?: Insights.currentPeriod(snapshot.periods, today)
        val grades = Insights.gradesForPeriod(snapshot.grades, period?.code)
        val subjectGrades = grades.filter { it.subject == hypothesis.subject }

        val simulated = Grade(
            id = "simulation",
            subject = hypothesis.subject,
            label = "Note simulée",
            value = hypothesis.value,
            scale = hypothesis.scale,
            coefficient = hypothesis.coefficient,
            classAverage = null,
            date = today,
            periodCode = period?.code.orEmpty(),
        )

        return Outcome(
            subjectBefore = Insights.weightedAverage(subjectGrades),
            subjectAfter = Insights.weightedAverage(subjectGrades + simulated),
            generalBefore = Insights.weightedAverage(grades),
            generalAfter = Insights.weightedAverage(grades + simulated),
        )
    }

    /**
     * La note qu'il faut obtenir pour atteindre une moyenne visée dans la matière.
     *
     * Null quand l'objectif est hors d'atteinte avec un seul devoir : dire « il te faudrait
     * 27/20 » n'aiderait personne, et prétendre le contraire serait pire.
     */
    fun requiredGrade(
        snapshot: SchoolSnapshot,
        subject: String,
        target: Double,
        coefficient: Double,
        scale: Double,
        today: LocalDate,
    ): Double? {
        if (coefficient <= 0 || scale <= 0) return null
        val period = Insights.currentPeriod(snapshot.periods, today)
        val subjectGrades = Insights.gradesForPeriod(snapshot.grades, period?.code)
            .filter { it.subject == subject && it.counted && it.value != null && it.scale > 0 }
        val weights = subjectGrades.sumOf { it.coefficient }
        val total = subjectGrades.sumOf { (it.outOfTwenty ?: 0.0) * it.coefficient }
        // Moyenne visée = (total + note×coef) / (poids + coef), résolu pour la note sur 20.
        val neededOutOfTwenty = target * (weights + coefficient) - total
        val needed = neededOutOfTwenty / coefficient
        if (needed < 0 || needed > 20.0) return null
        return needed * scale / 20.0
    }
}
