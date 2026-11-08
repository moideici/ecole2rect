package fr.ecole2recte.app.domain

/** Calcul de moyenne sans Android, sans arrondi intermédiaire. */
object AverageEngine {
    data class SubjectWeight(val id: String, val value: Double?, val coefficient: Double?, val isSubSubject: Boolean = false)
    data class General(val value: Double?, val source: AverageSource)
    data class GeneralProjection(val value: Double, val estimated: Boolean)

    private fun eligible(subjects: List<SubjectWeight>) = subjects.filter {
        !it.isSubSubject && it.value != null && (it.coefficient ?: 1.0) > 0
    }

    fun weightedSubjects(subjects: List<SubjectWeight>): Double? {
        val used = eligible(subjects)
        val weight = used.sumOf { it.coefficient ?: 1.0 }
        if (weight <= 0) return null
        return used.sumOf { it.value!! * (it.coefficient ?: 1.0) } / weight
    }

    fun general(official: Double?, subjects: List<SubjectWeight>, grades: List<GradeEntry>): General {
        if (official != null) return General(official, AverageSource.Official)
        weightedSubjects(subjects)?.let { return General(it, AverageSource.FromSubjects) }
        GradeMath.weightedAverage(grades)?.let { return General(it, AverageSource.FromGrades) }
        return General(null, AverageSource.None)
    }

    fun projectGeneral(subjects: List<SubjectWeight>, id: String, projected: Double, general: Double?, official: Boolean): GeneralProjection? {
        val used = eligible(subjects)
        val before = used.firstOrNull { it.id == id } ?: return null
        val baseline = weightedSubjects(used)
        if (official || (general != null && baseline != null && kotlin.math.abs(general - baseline) > .055)) {
            val weight = used.sumOf { it.coefficient ?: 1.0 }
            return general?.let { GeneralProjection(it + (projected - before.value!!) * (before.coefficient ?: 1.0) / weight, true) }
        }
        return weightedSubjects(used.map { if (it.id == id) it.copy(value = projected) else it })?.let { GeneralProjection(it, false) }
    }
}
