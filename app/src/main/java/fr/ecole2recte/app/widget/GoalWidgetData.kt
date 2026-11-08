package fr.ecole2recte.app.widget

import fr.ecole2recte.app.domain.FrenchFormat
import fr.ecole2recte.app.domain.MASKED
import fr.ecole2recte.app.domain.PrivacyMode
import kotlin.math.roundToInt

data class GoalWidgetRow(val id: String, val name: String, val text: String, val percent: Int?, val gap: Double?, val compactText: String = text)

object GoalWidgetData {
    fun progress(average: Double?, target: Double?): Int? {
        if (average == null || target == null || !average.isFinite() || !target.isFinite() || target <= 0) return null
        return (average / target * 100).coerceIn(0.0, 100.0).roundToInt()
    }

    fun rows(subjects: List<WidgetSubjectMean>, goals: Map<String, Double>, digits: Int, privacy: PrivacyMode): List<GoalWidgetRow> {
        val rows = subjects.map { subject ->
            if (privacy.hidesGrades) GoalWidgetRow(subject.id, subject.name, MASKED, null, null)
            else {
                val value = subject.mean.value
                val target = goals[subject.id]
                GoalWidgetRow(subject.id, subject.name,
                    (value?.let { FrenchFormat.decimal(it, digits) } ?: "–") + " / " + (target?.let { "objectif ${FrenchFormat.decimal(it, digits)}" } ?: "objectif à définir"),
                    progress(value, target), if (value != null && target != null) value - target else null,
                    (value?.let { FrenchFormat.decimal(it, digits) } ?: "–") + " → " + (target?.let { FrenchFormat.decimal(it, digits) } ?: "–"))
            }
        }
        return if (privacy.hidesGrades) rows else rows.sortedWith(compareBy<GoalWidgetRow> { it.gap ?: Double.POSITIVE_INFINITY }.thenBy { it.name })
    }

    fun capacity(widthDp: Float, heightDp: Float, landscape: Boolean, fontScale: Float): Int {
        val scale = fontScale.coerceAtLeast(1f)
        val width = widthDp / scale
        val height = heightDp / scale
        if (width < 92) return 0 // Police trop grande : lien explicite vers Notes plutôt que deux nombres coupés.
        val larger = if (landscape) width >= 320 && height >= 220 else width >= 260 && height >= 240
        return ((height - 48) / 32).toInt().coerceIn(0, if (larger) 12 else 4)
    }
}
