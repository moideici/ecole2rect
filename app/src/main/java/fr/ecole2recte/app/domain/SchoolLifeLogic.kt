package fr.ecole2recte.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

object SchoolLifeLogic {
    /** Uniquement une durée explicite ; jamais une différence entre deux horaires. */
    fun countedMinutes(raw: String): Int? {
        val text = raw.trim().lowercase()
        Regex("^(\\d{1,3})\\s*(?:h|heures?)\\s*(\\d{1,2})?\\s*(?:min)?$").matchEntire(text)?.let {
            val hours = it.groupValues[1].toIntOrNull() ?: return null
            val minutes = it.groupValues[2].takeIf(String::isNotEmpty)?.toIntOrNull() ?: 0
            return (hours * 60 + minutes).takeIf { minutes < 60 }
        }
        Regex("^(\\d{1,5})\\s*(?:min|minutes?)$").matchEntire(text)?.let { return it.groupValues[1].toIntOrNull() }
        Regex("^(\\d{1,3})[:h](\\d{2})$").matchEntire(text)?.let {
            return (it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt()).takeIf { _ -> it.groupValues[2].toInt() < 60 }
        }
        Regex("^(\\d{1,3}[,.]\\d{1,2})\\s*(?:h|heures?)$").matchEntire(text)?.let {
            return (it.groupValues[1].replace(',', '.').toDouble() * 60).toInt()
        }
        return null
    }

    private fun unjustified(events: List<SchoolLifeEvent>) = events.filter { it.kind == SchoolLifeKind.Absence && !it.justified }
    fun unjustifiedMinutes(events: List<SchoolLifeEvent>) = unjustified(events).sumOf { it.countedMinutes ?: 0 }
    fun unknownDurations(events: List<SchoolLifeEvent>) = unjustified(events).count { it.countedMinutes == null }
    fun month(event: SchoolLifeEvent) = runCatching { YearMonth.from(LocalDate.parse(event.date)).toString() }.getOrDefault("Date inconnue")
    fun months(events: List<SchoolLifeEvent>): Map<String, List<SchoolLifeEvent>> = events.groupBy(::month)
        .toSortedMap(compareBy<String> { it == "Date inconnue" }.thenByDescending { it })
    fun filter(events: List<SchoolLifeEvent>, kind: SchoolLifeKind?, month: String?) = events.filter {
        (kind == null || it.kind == kind) && (month == null || SchoolLifeLogic.month(it) == month)
    }

    fun reminders(events: List<SchoolLifeEvent>, today: LocalDate, days: Int?, notified: Set<String>): List<SchoolLifeEvent> {
        if (days !in listOf(2, 5, 7)) return emptyList()
        return unjustified(events).filter {
            val date = runCatching { LocalDate.parse(it.date) }.getOrNull()
            it.id.isNotBlank() && it.id !in notified && date != null && ChronoUnit.DAYS.between(date, today) >= days!!
        }
    }

    /** Les grands caractères gardent les compteurs empilés. Pas de dimension imposée au contenu. */
    fun columns(width: Float, height: Float, landscape: Boolean, fontScale: Float): Int = when {
        fontScale >= 1.5f || width < 600f || (landscape && height < 480f) -> 1
        width <= 840f -> 2
        else -> 3
    }
    fun widgetExpanded(width: Float, height: Float, fontScale: Float = 1f) = width >= 160f && height >= 100f * fontScale
    fun widgetShowsCaption(width: Float, height: Float, fontScale: Float) = width >= 50f * fontScale && height >= 55f * fontScale
    fun widgetNumberSize(width: Float, height: Float, fontScale: Float, characters: Int): Int = minOf(
        22f, (height - 12f) / fontScale.coerceAtLeast(0.7f),
        (width - 12f) / (characters.coerceAtLeast(1) * 0.65f * fontScale.coerceAtLeast(0.7f)),
    ).toInt().coerceAtLeast(1)
    fun widgetValue(count: Int?, hidden: Boolean) = if (hidden) MASKED else count?.toString() ?: "–"
}
