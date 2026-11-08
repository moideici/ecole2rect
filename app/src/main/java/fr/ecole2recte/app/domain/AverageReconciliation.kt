package fr.ecole2recte.app.domain

import kotlin.math.abs

enum class AverageOrigin { Official, Recomputed, Computed, None }

data class AverageVerdict(val value: Double?, val origin: AverageOrigin, val official: Double?, val behind: List<GradeEntry> = emptyList())

/** Le bulletin garde raison tant que des notes postérieures ou un préfixe concordant ne prouvent pas son retard. */
object AverageReconciliation {
    private const val TOLERANCE = 0.055

    fun reconcile(official: Double?, grades: List<GradeEntry>, computedOn: String?): AverageVerdict {
        val used = grades.filter { it.counted && it.value != null && it.coefficient > 0 }
        val calculated = GradeMath.weightedAverage(used)
        if (official == null) return AverageVerdict(calculated, if (calculated == null) AverageOrigin.None else AverageOrigin.Computed, null)
        val newer = if (computedOn == null) emptyList() else used.filter { it.date != null && it.date > computedOn }
        if (newer.isNotEmpty()) return AverageVerdict(calculated, AverageOrigin.Recomputed, official, newer)
        if (calculated == null || abs(calculated - official) <= TOLERANCE || used.any { it.date == null }) {
            return AverageVerdict(official, AverageOrigin.Official, official)
        }
        val ordered = used.sortedWith(compareByDescending<GradeEntry> { it.date }.thenByDescending { it.id })
        for (count in 1 until ordered.size) {
            val removed = ordered.take(count)
            // Avec dateCalcul, seules les notes du jour de calcul restent ambiguës.
            if (computedOn != null && removed.any { it.date!! < computedOn }) break
            val before = GradeMath.weightedAverage(ordered.drop(count)) ?: break
            if (abs(before - official) <= TOLERANCE) return AverageVerdict(calculated, AverageOrigin.Recomputed, official, removed)
        }
        return AverageVerdict(official, AverageOrigin.Official, official)
    }
}
