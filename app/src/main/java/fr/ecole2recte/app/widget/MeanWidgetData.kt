package fr.ecole2recte.app.widget

import fr.ecole2recte.app.domain.*
import fr.ecole2recte.app.sync.WidgetSnapshot
import org.json.JSONArray
import org.json.JSONObject

/** Valeurs non arrondies, issues du moteur de moyenne lors de la publication, jamais du réseau du widget. */
data class WidgetMean(val value: Double? = null, val source: AverageSource = AverageSource.None, val official: Double? = null) {
    fun json(): JSONObject = JSONObject().put("value", value).put("source", source.name).put("official", official)
    companion object {
        fun fromJson(json: JSONObject?) = WidgetMean(json?.number("value"),
            AverageSource.entries.firstOrNull { it.name == json?.optString("source") } ?: AverageSource.None, json?.number("official"))
    }
}

data class WidgetSubjectMean(
    val id: String,
    val name: String,
    val mean: WidgetMean,
    val coefficient: Double? = null,
    val isSubSubject: Boolean = false,
) {
    fun json(): JSONObject = JSONObject().put("id", id).put("name", name).put("mean", mean.json())
        .put("coefficient", coefficient).put("sub", isSubSubject)
}

data class WidgetMeanPeriod(
    val code: String,
    val name: String,
    val current: Boolean,
    val general: WidgetMean,
    val classAverage: Double? = null,
    val subjects: List<WidgetSubjectMean> = emptyList(),
) {
    fun json(): JSONObject = JSONObject().put("code", code).put("name", name).put("current", current)
        .put("general", general.json()).put("class", classAverage).put("subjects", JSONArray(subjects.map { it.json() }))

    companion object {
        fun fromJson(json: JSONObject) = WidgetMeanPeriod(json.optString("code"), json.optString("name"), json.optBoolean("current"),
            WidgetMean.fromJson(json.optJSONObject("general")), json.number("class"), json.objectList("subjects").map {
                WidgetSubjectMean(it.optString("id"), it.optString("name"), WidgetMean.fromJson(it.optJSONObject("mean")), it.number("coefficient"), it.optBoolean("sub"))
            })

        fun fromOverview(grades: GradeOverview, current: Boolean = true) = WidgetMeanPeriod(
            grades.periodCode, grades.periodName, current,
            WidgetMean(grades.verdict?.value ?: GradeSimulation.parse(grades.average), grades.averageSource, grades.verdict?.official),
            grades.classAverageValue ?: grades.classAverage?.let(GradeSimulation::parse),
            grades.bySubject.map { line ->
                val verdict = line.verdict
                WidgetSubjectMean(line.id, line.subject.name,
                    WidgetMean(verdict?.value ?: GradeSimulation.parse(line.average), when {
                        verdict?.origin == AverageOrigin.Recomputed -> AverageSource.Recomputed
                        line.averageOfficial -> AverageSource.Official
                        verdict?.value != null || GradeSimulation.parse(line.average) != null -> AverageSource.FromGrades
                        else -> AverageSource.None
                    }, verdict?.official), line.coefficient, line.isSubSubject)
            },
        )
    }
}

internal fun JSONObject.number(key: String): Double? = if (isNull(key)) null else optDouble(key).takeIf { it.isFinite() }
internal fun JSONObject.objectList(key: String): List<JSONObject> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}

enum class MeanSource(val label: String) { General("Moyenne générale"), Subject("Une matière"), Class("Moyenne de la classe"), GoalGap("Écart à un objectif") }
enum class MeanPeriod(val label: String) { Current("Courante"), Previous("Précédente") }
enum class MeanTone { Positive, Warning, Negative, Neutral }

data class MeanWidgetConfig(
    val profile: String = "",
    val source: MeanSource = MeanSource.General,
    val subject: String = "",
    val period: MeanPeriod = MeanPeriod.Current,
    val title: String = "",
    val target: Double = 14.0,
    val greenFrom: Double = 14.0,
    val orangeFrom: Double = 10.0,
    val showTrend: Boolean = true,
) {
    fun valid(): Boolean = title.codePointCount(0, title.length) <= 8 && target.isFinite() && target in 0.0..20.0 &&
        greenFrom.isFinite() && orangeFrom.isFinite() && greenFrom >= orangeFrom

    fun tone(value: Double?): MeanTone = when {
        value == null -> MeanTone.Neutral
        value >= greenFrom -> MeanTone.Positive
        value >= orangeFrom -> MeanTone.Warning
        else -> MeanTone.Negative
    }
}

data class MeanWidgetReading(
    val value: String = "–",
    val title: String = "Moyenne",
    val period: String = "",
    val trend: String = "",
    val origin: String = "",
    val tone: MeanTone = MeanTone.Neutral,
    val subject: String = "",
)

/** Point unique de lecture des moyennes des widgets : ne dépend que de l'instantané publié. */
object MeanWidgetData {
    fun read(snapshot: WidgetSnapshot?, config: MeanWidgetConfig, digits: Int, privacy: PrivacyMode): MeanWidgetReading {
        val periods = snapshot?.meanPeriods.orEmpty().ifEmpty {
            // Migration de l'ancien instantané : aucune période ou moyenne de matière n'est inventée.
            snapshot?.let { old ->
                val value = GradeSimulation.parse(old.average)
                if (value == null) emptyList() else listOf(WidgetMeanPeriod("", "", true, WidgetMean(value, when {
                    old.averageLabel.startsWith("officielle") -> AverageSource.Official
                    old.averageLabel.startsWith("Recalculée") -> AverageSource.Recomputed
                    else -> AverageSource.FromGrades
                }, GradeSimulation.parse(old.officialAverage))))
            }.orEmpty()
        }
        val current = periods.indexOfFirst { it.current }
        val index = if (config.period == MeanPeriod.Current) current else current - 1
        val period = periods.getOrNull(index)
        val line = period?.subjects?.firstOrNull { it.id == config.subject }
        val title = config.title.ifBlank { when (config.source) {
            MeanSource.Subject -> line?.name ?: "Matière"
            MeanSource.Class -> "Classe"
            MeanSource.GoalGap -> "Écart objectif"
            else -> "Moyenne"
        } }
        val base = MeanWidgetReading(title = title, period = period?.name.orEmpty(), subject = if (config.source == MeanSource.Subject) config.subject else "")
        if (privacy.hidesGrades) return base.copy(value = MASKED)
        fun select(p: WidgetMeanPeriod?): WidgetMean {
            if (p == null) return WidgetMean()
            return when (config.source) {
                MeanSource.Subject -> p.subjects.firstOrNull { it.id == config.subject }?.mean ?: WidgetMean()
                MeanSource.Class -> WidgetMean(p.classAverage, if (p.classAverage != null) AverageSource.Official else AverageSource.None)
                else -> if (p.general.value != null) p.general else {
                    val result = AverageEngine.general(null, p.subjects.map { AverageEngine.SubjectWeight(it.id, it.mean.value, it.coefficient, it.isSubSubject) }, emptyList())
                    WidgetMean(result.value, result.source)
                }
            }
        }
        val mean = select(period)
        val value = mean.value?.takeIf { it.isFinite() } ?: return base
        val shown = if (config.source == MeanSource.GoalGap) value - config.target else value
        val formatted = FrenchFormat.decimal(kotlin.math.abs(shown), AveragePresentation.digits(digits))
        val previous = select(periods.getOrNull(index - 1))
        return base.copy(
            value = (if (shown < 0) "−" else if (config.source == MeanSource.GoalGap && shown > 0) "+" else "") + formatted,
            origin = AveragePresentation.label(mean.source, AverageVerdict(value, AverageOrigin.None, mean.official), AveragePresentation.digits(digits)),
            trend = if (config.showTrend) AveragePresentation.trend(value, mean.source, previous.value, previous.source, AveragePresentation.digits(digits)) else "",
            tone = config.tone(shown),
        )
    }

    fun age(updatedAt: Long, now: Long): String {
        val hours = ((now - updatedAt) / 3_600_000).coerceAtLeast(0)
        return if (updatedAt <= 0 || hours < 1) "" else "il y a $hours h"
    }
}
