package fr.ecole2recte.app.sync

import java.time.Duration
import java.time.LocalDateTime

data class CurrentCoursePlan(
    val current: WidgetSlot? = null,
    val next: WidgetSlot? = null,
    val progress: Int = 0,
    val nextBoundary: LocalDateTime? = null,
) {
    /** Progression locale entre deux bornes, sans modifier la fréquence de synchronisation réseau. */
    fun nextRefresh(now: LocalDateTime): LocalDateTime? = nextBoundary?.let {
        if (current == null) it else minOf(it, now.plusMinutes(1))
    }
}

/** Plan local, sans horloge implicite ni accès au réseau. Intervalles [début, fin[. */
object CurrentCoursePlanner {
    fun start(slot: WidgetSlot): LocalDateTime? = runCatching { LocalDateTime.parse("${slot.date}T${slot.start}") }.getOrNull()
    private fun end(slot: WidgetSlot): LocalDateTime? = runCatching { LocalDateTime.parse("${slot.date}T${slot.end}") }.getOrNull()

    fun plan(slots: List<WidgetSlot>, now: LocalDateTime, enabled: Boolean = true): CurrentCoursePlan {
        if (!enabled) return CurrentCoursePlan()
        val valid = slots.filter { !it.canceled && start(it) != null && end(it)?.isAfter(start(it)) == true }.sortedBy { start(it) }
        val current = valid.firstOrNull { now >= start(it)!! && now < end(it)!! }
        val next = valid.firstOrNull { start(it)!! > now }
        val boundary = valid.flatMap { listOf(start(it)!!, end(it)!!) }.filter { it > now }.minOrNull()
        val progress = current?.let {
            (100 * Duration.between(start(it), now).seconds / Duration.between(start(it), end(it)).seconds).toInt().coerceIn(0, 100)
        } ?: 0
        return CurrentCoursePlan(current, next, progress, boundary)
    }

    fun text(plan: CurrentCoursePlan, hidden: Boolean): String {
        if (hidden) return "•••"
        val current = plan.current ?: return ""
        return listOfNotNull(
            current.room.takeIf { it.isNotBlank() }?.let { "Salle $it" },
            "${current.start} – ${current.end}",
            plan.next?.let { "Ensuite : ${it.subject} à ${it.start}" },
        ).joinToString(" · ")
    }
}
