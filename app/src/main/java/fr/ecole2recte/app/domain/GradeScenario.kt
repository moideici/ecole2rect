package fr.ecole2recte.app.domain

/** gradeId absent : ajout ; présent : remplacement ou retrait d'une note réelle. */
data class ScenarioEdit(
    val subjectId: String,
    val gradeId: String? = null,
    val removed: Boolean = false,
    val value: Double? = null,
    val scale: Double = 20.0,
    val coefficient: Double = 1.0,
)

data class SavedGradeScenario(val id: String, val name: String, val edits: List<ScenarioEdit>)

sealed interface ScenarioResult {
    data class Value(val subjects: Map<String, Double?>, val general: Double?) : ScenarioResult
    data class Invalid(val reason: String) : ScenarioResult
}

/** Extension du moteur : conserve la base affichée, applique les différences sans arrondi. */
object GradeScenario {
    fun edit(subjectId: String, gradeId: String?, value: String, scale: String, coefficient: String): ScenarioEdit? {
        val note = GradeSimulation.parse(value) ?: return null
        val outOf = GradeSimulation.parse(scale)?.takeIf { it > 0 } ?: return null
        val weight = GradeSimulation.parse(coefficient)?.takeIf { it > 0 } ?: return null
        if (note !in 0.0..outOf) return null
        return ScenarioEdit(subjectId, gradeId, value = note, scale = outOf, coefficient = weight)
    }

    fun project(overview: GradeOverview, edits: List<ScenarioEdit>): ScenarioResult {
        fun invalid(reason: String) = ScenarioResult.Invalid(reason)
        val realEdits = edits.filter { it.gradeId != null }
        if (realEdits.map { it.subjectId to it.gradeId }.distinct().size != realEdits.size)
            return invalid("Une note réelle ne peut être modifiée qu'une fois dans un scénario.")
        if (edits.any { edit -> overview.bySubject.none { it.id == edit.subjectId } })
            return invalid("Une matière de ce scénario n'est plus disponible pour cette période.")
        val subjects = overview.bySubject.associate { it.id to GradeSimulation.parse(it.average) }.toMutableMap()
        for ((id, changes) in edits.groupBy { it.subjectId }) {
            val line = overview.bySubject.first { it.id == id }
            val counted = line.grades.filter { it.counted && it.value != null && it.coefficient > 0 }
            var weight = counted.sumOf { it.coefficient }
            var points = (subjects[id] ?: 0.0) * weight
            for (change in changes) {
                if (change.gradeId != null) {
                    val old = counted.firstOrNull { it.id == change.gradeId }
                        ?: return invalid("Une note de ce scénario n'est plus disponible ou ne compte pas dans cette période.")
                    points -= GradeMath.on20(old.value!!, old.scale) * old.coefficient
                    weight -= old.coefficient
                } else if (change.removed) return invalid("Seule une note réelle peut être retirée.")
                if (!change.removed) {
                    val value = change.value
                    if (value == null || !value.isFinite() || !change.scale.isFinite() || change.scale <= 0 || value !in 0.0..change.scale || !change.coefficient.isFinite() || change.coefficient <= 0)
                        return invalid("Saisis une note dans son barème et un coefficient supérieur à 0.")
                    points += GradeMath.on20(value, change.scale) * change.coefficient
                    weight += change.coefficient
                }
            }
            val projected = if (weight > 1e-9) points / weight else null
            if (projected != null && (!projected.isFinite() || projected < -1e-9 || projected > 20 + 1e-9))
                return invalid("L'écart avec le bulletin empêche d'estimer ce remplacement ou ce retrait.")
            subjects[id] = projected?.coerceIn(0.0, 20.0)
        }
        val weights = overview.bySubject.map { AverageEngine.SubjectWeight(it.id, GradeSimulation.parse(it.average), it.coefficient, it.isSubSubject) }
        val initial = GradeSimulation.parse(overview.average)
        var general = initial
        for (id in edits.map { it.subjectId }.distinct()) {
            val line = overview.bySubject.first { it.id == id }
            if (line.isSubSubject || (line.coefficient ?: 1.0) <= 0) continue
            val projected = subjects[id]
            val effect = projected?.let { AverageEngine.projectGeneral(weights, id, it, initial, overview.averageSource.isOfficial) }
            general = if (general != null && initial != null && effect != null) general + effect.value - initial else null
        }
        return ScenarioResult.Value(subjects, general?.takeIf { it.isFinite() && it in 0.0..20.0 })
    }
}

/** Décision pure commune aux nouveaux formulaires ; pas de dimensions imposées au contenu. */
enum class AnalysisLayout {
    Stacked, Columns, Wide;

    companion object {
        fun choose(widthDp: Int, heightDp: Int, landscape: Boolean, fontScale: Float = 1f): AnalysisLayout = when {
            widthDp < 600 || fontScale > 1.3f || (landscape && heightDp < 480) -> Stacked
            widthDp <= 840 -> Columns
            else -> Wide
        }
    }
}
