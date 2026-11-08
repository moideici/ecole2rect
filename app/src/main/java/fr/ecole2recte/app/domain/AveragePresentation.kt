package fr.ecole2recte.app.domain

import kotlin.math.abs

/** Présentation française commune aux écrans et au widget, indépendante d'Android. */
object AveragePresentation {
    fun digits(value: Int) = if (value == 1) 1 else 2
    fun number(text: String, digits: Int): String = GradeSimulation.parse(text)?.let { FrenchFormat.decimal(it, AveragePresentation.digits(digits)) } ?: text
    private fun number(text: String?, value: Double?, digits: Int): String? = value?.let { FrenchFormat.decimal(it, digits) } ?: text?.let { number(it, digits) }

    fun format(overview: GradeOverview, digits: Int): GradeOverview = overview.copy(
        average = overview.verdict?.value?.let { FrenchFormat.decimal(it, digits) } ?: number(overview.average, digits),
        classAverage = number(overview.classAverage, overview.classAverageValue, digits),
        periods = overview.periods.map { it.copy(average = number(it.average, it.value, digits)) },
        bySubject = overview.bySubject.map { line -> line.copy(
            average = line.verdict?.value?.let { FrenchFormat.decimal(it, digits) } ?: number(line.average, digits), classAverage = number(line.classAverage, line.classAverageValue, digits),
            minimum = number(line.minimum, line.minimumValue, digits), maximum = number(line.maximum, line.maximumValue, digits),
        ) },
        trend = overview.trend.replace(Regex("[0-9]+,[0-9]+")) { number(it.value, digits) },
    )

    fun trend(current: Double?, source: AverageSource, previous: Double?, previousSource: AverageSource, digits: Int): String {
        if (current == null || previous == null || source == AverageSource.None || previousSource == AverageSource.None || source.isOfficial != previousSource.isOfficial) return ""
        val delta = current - previous
        if (abs(delta) < .05) return ""
        return (if (source.isOfficial) "" else "≈ ") + (if (delta > 0) "↗ +" else "↘ −") + FrenchFormat.decimal(abs(delta), digits)
    }

    fun label(source: AverageSource, verdict: AverageVerdict?, digits: Int): String =
        if (source == AverageSource.Recomputed) "Recalculée" + verdict?.official?.let { " · bulletin ${FrenchFormat.decimal(it, digits)}" }.orEmpty()
        else if (source.isOfficial) "officielle" else if (source == AverageSource.None) "" else "calculée par l'app"

    fun behindText(grade: GradeEntry): String = "calculée avant la note ${grade.label.ifBlank { grade.subject }} du " +
        grade.date?.let { runCatching { java.time.LocalDate.parse(it).format(FrenchFormat.formatter("dd/MM")) }.getOrDefault(it) }.orEmpty()
}
