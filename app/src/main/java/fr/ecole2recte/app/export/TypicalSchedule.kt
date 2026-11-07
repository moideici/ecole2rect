package fr.ecole2recte.app.export

import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.ui.schedule.buildDayTimeline
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/*
 * CE QUI EST ÉTABLI, CE QUI EST SUPPOSÉ (semaines 1 / 2, dites aussi A / B)
 *
 * Établi : dans les réponses d'emploidutemps.awp que nous connaissons (la capture documentée
 * `timetable_documented.json`, le client web du même auteur), aucun champ n'indique une semaine A / B ou
 * un cours « une semaine sur deux ». Les champs vus sont : id, text, matiere, codeMatiere, typeCours
 * (COURS, PERMANENCE, CONGE), start_date, end_date, color, prof, salle, classe, classeCode, groupe,
 * groupeCode, isFlexible, isModifie, isAnnule, contenuDeSeance, devoirAFaire. `groupe` désigne un
 * sous-groupe de la classe, pas une semaine.
 *
 * Supposé : un emploi du temps qui alterne se reconnaît seulement à ses effets, des cours présents une
 * semaine sur deux. Ce n'est PAS vérifié sur une réponse réelle d'un établissement qui alterne.
 * C'est pourquoi l'alternance est un réglage de l'utilisateur (avec quelle semaine est la semaine 1) et non une
 * détection automatique ; une simple suggestion peut être affichée quand deux semaines se ressemblent peu.
 */

/**
 * Le réglage « mon emploi du temps alterne : semaine 1 / semaine 2 ». [week1Monday] est le lundi d'une
 * semaine 1 (n'importe laquelle : seules la parité et l'écart en semaines comptent).
 */
data class Alternation(val enabled: Boolean = false, val week1Monday: LocalDate? = null) {
    val active: Boolean get() = enabled && week1Monday != null

    /** 1 ou 2 : la semaine, dans l'alternance, qui contient [date]. Sans alternance active : toujours 1. */
    fun cycleOf(date: LocalDate): Int {
        val reference = week1Monday?.let(::mondayOf) ?: return 1
        val weeks = ChronoUnit.WEEKS.between(reference, mondayOf(date))
        return Math.floorMod(weeks, 2L).toInt() + 1
    }

    /** `off` ou `on;2026-11-02`, pour SharedPreferences. */
    fun serialize(): String = if (active) "on;${mondayOf(week1Monday!!)}" else "off"

    companion object {
        fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        fun parse(text: String?): Alternation {
            val parts = text.orEmpty().split(';')
            if (parts.firstOrNull() != "on") return Alternation()
            val monday = runCatching { LocalDate.parse(parts.getOrNull(1).orEmpty()) }.getOrNull() ?: return Alternation()
            return Alternation(true, mondayOf(monday))
        }

        /** Un réglage où la semaine qui contient [date] est la semaine [cycle] (1 ou 2). */
        fun with(date: LocalDate, cycle: Int): Alternation =
            Alternation(true, mondayOf(date).let { if (cycle == 2) it.minusWeeks(1) else it })
    }
}

/** Dans quelles semaines d'une alternance un créneau a lieu. */
enum class WeekSelector { Every, First, Second }

/**
 * Un créneau de la semaine type. Les doubles heures sont déjà fondues (même règle que la frise de
 * l'agenda). [dates] donne, pour chaque semaine connue où il a lieu (1, 2), la date réelle : elle sert
 * d'ancre à la récurrence. [canceledOn] liste les dates connues où ce créneau est annulé.
 */
data class TypicalSlot(
    /** 1 = lundi … 7 = dimanche. */
    val day: Int,
    val start: String,
    val end: String,
    val subject: String,
    val room: String,
    val teacher: String,
    val colorIndex: Int,
    val permanence: Boolean,
    val cycle: WeekSelector,
    val dates: Map<Int, LocalDate>,
    val canceledOn: Set<LocalDate> = emptySet(),
) {
    /** La clé qui dit que deux créneaux de deux semaines sont « le même ». */
    val signature: String get() = "$day|$start|$end|${subject.lowercase()}"

    fun occursIn(week: Int, alternating: Boolean): Boolean = !alternating || when (cycle) {
        WeekSelector.Every -> true
        WeekSelector.First -> week == 1
        WeekSelector.Second -> week == 2
    }
}

/** La ou les semaines types déduites des semaines connues en cache. */
data class TypicalSchedule(
    val alternating: Boolean,
    val slots: List<TypicalSlot>,
    /** Les semaines (1, 2) dont on a vu les cours : en alternance, une semaine inconnue est signalée à l'utilisateur. */
    val knownCycles: Set<Int>,
) {
    val isEmpty: Boolean get() = slots.isEmpty()

    /** Les créneaux d'une semaine type ; sans alternance, tous. */
    fun slotsOf(week: Int): List<TypicalSlot> = slots.filter { it.occursIn(week, alternating) }

    /** Les semaines à proposer : [1] sans alternance, [1, 2] avec. */
    val weeks: List<Int> get() = if (alternating) listOf(1, 2) else listOf(1)
}

object TypicalWeeks {
    private const val MIN_LESSONS_FOR_HINT = 3
    private const val HINT_THRESHOLD = 0.3

    private class WeekSlot(
        val date: LocalDate,
        val start: String,
        val end: String,
        val subject: String,
        val room: String,
        val teacher: String,
        val colorIndex: Int,
        val permanence: Boolean,
        val canceled: Boolean,
    ) {
        val day: Int get() = date.dayOfWeek.value
        val signature: String get() = "$day|$start|$end|${subject.lowercase()}"
    }

    /**
     * Construit la semaine type (ou les deux) à partir des cours connus (en pratique deux semaines
     * consécutives). Les congés (`CONGE`) sont écartés ; les cours annulés restent, repérés, pour que
     * l'export ICS les exclut de la date concernée.
     */
    fun build(entries: List<ScheduleEntry>, alternation: Alternation): TypicalSchedule {
        val weeks = weeksOf(entries)
        if (weeks.isEmpty()) return TypicalSchedule(alternation.active, emptyList(), emptySet())
        return if (alternation.active) buildAlternating(weeks, alternation) else buildSingle(weeks)
    }

    private fun buildSingle(weeks: Map<LocalDate, List<WeekSlot>>): TypicalSchedule {
        // La semaine la plus fournie (à égalité, la plus ancienne) fait référence : une semaine de vacances ne la remplace pas.
        val reference = weeks.entries.sortedWith(compareBy({ -it.value.size }, { it.key })).first().value
        val slots = reference.map { it.toSlot(WeekSelector.Every, mapOf(1 to it.date)) }.sortedSlots()
        return TypicalSchedule(false, slots, setOf(1))
    }

    private fun buildAlternating(weeks: Map<LocalDate, List<WeekSlot>>, alternation: Alternation): TypicalSchedule {
        fun reference(cycle: Int): List<WeekSlot>? = weeks.entries
            .filter { alternation.cycleOf(it.key) == cycle }
            .sortedWith(compareBy({ -it.value.size }, { it.key }))
            .firstOrNull()?.value
        val first = reference(1).orEmpty()
        val second = reference(2).orEmpty()
        val bySig1 = first.associateBy { it.signature }
        val bySig2 = second.associateBy { it.signature }
        val slots = (bySig1.keys + bySig2.keys).map { signature ->
            val a = bySig1[signature]
            val b = bySig2[signature]
            when {
                a != null && b != null -> a.toSlot(WeekSelector.Every, mapOf(1 to a.date, 2 to b.date), setOfNotNull(a.date.takeIf { a.canceled }, b.date.takeIf { b.canceled }))
                a != null -> a.toSlot(WeekSelector.First, mapOf(1 to a.date))
                else -> b!!.toSlot(WeekSelector.Second, mapOf(2 to b.date))
            }
        }.sortedSlots()
        val known = buildSet { if (first.isNotEmpty()) add(1); if (second.isNotEmpty()) add(2) }
        return TypicalSchedule(true, slots, known)
    }

    private fun WeekSlot.toSlot(cycle: WeekSelector, dates: Map<Int, LocalDate>, canceledOn: Set<LocalDate> = setOfNotNull(date.takeIf { canceled })) =
        TypicalSlot(day, start, end, subject, room, teacher, colorIndex, permanence, cycle, dates, canceledOn)

    private fun List<TypicalSlot>.sortedSlots() = sortedWith(compareBy({ it.day }, { it.start }, { it.subject }))

    /** Les cours de chaque semaine connue, doubles heures fondues, sans les congés. */
    private fun weeksOf(entries: List<ScheduleEntry>): Map<LocalDate, List<WeekSlot>> {
        val usable = entries.filter { !it.kind.equals("CONGE", ignoreCase = true) }.mapNotNull { entry ->
            val date = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: return@mapNotNull null
            if (entry.start.length < 5 || entry.end.length < 5) null else date to entry
        }
        return usable.groupBy({ Alternation.mondayOf(it.first) }, { it.second }).mapValues { (_, weekEntries) ->
            weekEntries.groupBy { it.date }.flatMap { (date, dayEntries) ->
                buildDayTimeline(dayEntries).lessons.map { lesson ->
                    val first = lesson.first
                    WeekSlot(
                        date = LocalDate.parse(date),
                        start = first.start,
                        end = lesson.entries.last().end,
                        subject = first.subject.name,
                        room = first.subject.room,
                        teacher = first.subject.teacher,
                        colorIndex = first.subject.colorIndex,
                        permanence = first.kind.equals("PERMANENCE", ignoreCase = true),
                        canceled = lesson.entries.all { it.canceled },
                    )
                }
            }
        }.filterValues { it.isNotEmpty() }
    }

    /**
     * Vrai quand deux semaines consécutives connues se ressemblent peu (au moins 30 % de créneaux qui
     * n'existent que dans l'une des deux) : de quoi suggérer le réglage d'alternance. Ce n'est qu'une
     * suggestion, jamais appliquée seule.
     */
    fun looksAlternating(entries: List<ScheduleEntry>): Boolean {
        val weeks = weeksOf(entries).toSortedMap()
        val full = weeks.filterValues { slots -> slots.count { !it.canceled } >= MIN_LESSONS_FOR_HINT }
        val keys = full.keys.toList()
        for (i in 0 until keys.size - 1) {
            if (ChronoUnit.WEEKS.between(keys[i], keys[i + 1]) != 1L) continue
            val a = full.getValue(keys[i]).filter { !it.canceled }.map { it.signature }.toSet()
            val b = full.getValue(keys[i + 1]).filter { !it.canceled }.map { it.signature }.toSet()
            val union = (a + b).size
            val different = (a - b).size + (b - a).size
            if (union > 0 && different.toDouble() / union >= HINT_THRESHOLD) return true
        }
        return false
    }

    /** Dernier jour de l'année scolaire : le 30 juin ; à partir de juillet, celui de l'année scolaire qui commence. */
    fun defaultEnd(today: LocalDate): LocalDate =
        LocalDate.of(if (today.monthValue >= 7) today.year + 1 else today.year, 6, 30)
}
