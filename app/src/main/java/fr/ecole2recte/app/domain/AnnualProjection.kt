package fr.ecole2recte.app.domain

/** Projections conditionnelles, jamais des prédictions. Les périodes manquantes restent absentes. */
object AnnualProjection {
    data class Annual(val value: Double, val weighted: Boolean, val periodCount: Int)
    data class Case(val period: Double?, val annual: Annual?)
    data class Cases(val best: Case, val worst: Case, val probable: Case)

    fun annual(periods: List<GradePeriod>): Annual? {
        val used = periods.filter { !it.annual && it.value != null && it.value.isFinite() && it.value in 0.0..20.0 && it.coefficient != 0.0 }
        if (used.isEmpty()) return null
        val weighted = used.all { it.coefficient != null && it.coefficient.isFinite() && it.coefficient > 0 }
        // Même moteur que les matières : les périodes prennent ici leur place dans la pondération.
        val value = AverageEngine.weightedSubjects(used.map {
            AverageEngine.SubjectWeight(it.code, it.value, if (weighted) it.coefficient else 1.0)
        }) ?: return null
        return Annual(value, weighted, used.size)
    }

    fun periods(overview: GradeOverview): List<GradePeriod> = overview.periods.map {
        if (it.code == overview.periodCode) it.copy(value = GradeSimulation.parse(overview.average)) else it
    }

    /** N devoirs de coefficient 1 par matière dans la période affichée ; autres périodes inchangées. */
    fun project(overview: GradeOverview, remaining: Int): Cases? {
        if (remaining !in 0..1000 || GradeSimulation.parse(overview.average) == null || overview.bySubject.isEmpty()) return null
        fun case(note: Double?): Case {
            val edits = if (remaining == 0) emptyList() else overview.bySubject.filter {
                !it.isSubSubject && (it.coefficient ?: 1.0) > 0 && GradeSimulation.parse(it.average) != null
            }.map { ScenarioEdit(it.id, value = note ?: GradeSimulation.parse(it.average), coefficient = remaining.toDouble()) }
            val result = GradeScenario.project(overview, edits) as? ScenarioResult.Value
            val value = result?.general
            val periods = periods(overview).map { if (it.code == overview.periodCode) it.copy(value = value) else it }
            // Sans projection de la période courante, ne pas afficher une annuelle partielle trompeuse.
            return Case(value, if (value == null) null else annual(periods))
        }
        return Cases(best = case(20.0), worst = case(0.0), probable = case(null))
    }
}
