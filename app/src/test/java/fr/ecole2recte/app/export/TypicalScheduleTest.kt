package fr.ecole2recte.app.export

import fr.ecole2recte.app.export.ExportFixtures.alternation
import fr.ecole2recte.app.export.ExportFixtures.lesson
import fr.ecole2recte.app.export.ExportFixtures.twoWeeks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TypicalScheduleTest {
    private fun d(text: String) = LocalDate.parse(text)

    @Test
    fun `la semaine 1 et la semaine 2 se reperent par rapport a la semaine de reference`() {
        assertEquals(1, alternation.cycleOf(d("2026-11-02")))
        assertEquals(1, alternation.cycleOf(d("2026-11-08")))
        assertEquals(2, alternation.cycleOf(d("2026-11-09")))
        assertEquals(1, alternation.cycleOf(d("2026-11-16")))
        assertEquals(2, alternation.cycleOf(d("2026-10-26")))
        // À travers le changement d'année.
        assertEquals(1, alternation.cycleOf(d("2026-12-28")))
        assertEquals(2, alternation.cycleOf(d("2027-01-04")))
        // Sans alternance, toujours la semaine 1.
        assertEquals(1, Alternation().cycleOf(d("2026-11-09")))
    }

    @Test
    fun `le reglage s'enregistre, se relit et se repare`() {
        val saved = Alternation.with(d("2026-11-11"), cycle = 2)
        assertEquals(d("2026-11-02"), saved.week1Monday)
        assertEquals(2, saved.cycleOf(d("2026-11-11")))
        assertEquals(saved, Alternation.parse(saved.serialize()))
        assertFalse(Alternation.parse(null).active)
        assertFalse(Alternation.parse("on;pas une date").active)
        assertFalse(Alternation.parse("n'importe quoi").active)
        assertEquals("off", Alternation().serialize())
        // Une date en milieu de semaine est ramenée au lundi.
        assertEquals(d("2026-11-02"), Alternation.parse("on;2026-11-04").week1Monday)
    }

    @Test
    fun `chaque creneau est classe toutes les semaines, semaine 1 seulement ou semaine 2 seulement`() {
        val schedule = TypicalWeeks.build(twoWeeks, alternation)
        assertTrue(schedule.alternating)
        assertEquals(setOf(1, 2), schedule.knownCycles)
        val bySubject = schedule.slots.associateBy { it.subject }
        assertEquals(WeekSelector.Every, bySubject.getValue("Maths").cycle)
        assertEquals(WeekSelector.Every, bySubject.getValue("Anglais").cycle)
        assertEquals(WeekSelector.Every, bySubject.getValue("SVT").cycle)
        assertEquals(WeekSelector.First, bySubject.getValue("Physique").cycle)
        assertEquals(WeekSelector.Second, bySubject.getValue("Chimie").cycle)
    }

    @Test
    fun `la double heure est fondue et l'ancre est la date reelle`() {
        val maths = TypicalWeeks.build(twoWeeks, alternation).slots.first { it.subject == "Maths" }
        assertEquals("08:00", maths.start)
        assertEquals("09:50", maths.end)
        assertEquals(1, maths.day)
        assertEquals(mapOf(1 to d("2026-11-02"), 2 to d("2026-11-09")), maths.dates)
        val chimie = TypicalWeeks.build(twoWeeks, alternation).slots.first { it.subject == "Chimie" }
        assertEquals(mapOf(2 to d("2026-11-11")), chimie.dates)
    }

    @Test
    fun `une semaine type ne garde que ses creneaux`() {
        val schedule = TypicalWeeks.build(twoWeeks, alternation)
        assertEquals(listOf("Maths", "Anglais", "Physique", "SVT"), schedule.slotsOf(1).map { it.subject })
        assertEquals(listOf("Maths", "Anglais", "Chimie", "SVT"), schedule.slotsOf(2).map { it.subject })
        assertEquals(listOf(1, 2), schedule.weeks)
    }

    @Test
    fun `un cours annule est garde avec sa date pour etre exclu`() {
        val svt = TypicalWeeks.build(twoWeeks, alternation).slots.first { it.subject == "SVT" }
        assertEquals(setOf(d("2026-11-05")), svt.canceledOn)
    }

    @Test
    fun `sans alternance une seule semaine type, les exceptions de l'autre semaine sont ignorees`() {
        val schedule = TypicalWeeks.build(twoWeeks, Alternation())
        assertFalse(schedule.alternating)
        assertEquals(listOf(1), schedule.weeks)
        assertEquals(listOf("Maths", "Anglais", "Physique", "SVT"), schedule.slots.map { it.subject })
        assertTrue(schedule.slots.all { it.cycle == WeekSelector.Every })
        assertEquals(schedule.slots, schedule.slotsOf(1))
    }

    @Test
    fun `quand une des deux semaines est inconnue, c'est dit et rien n'est invente`() {
        val onlyFirst = twoWeeks.filter { it.date < "2026-11-09" }
        val schedule = TypicalWeeks.build(onlyFirst, alternation)
        assertEquals(setOf(1), schedule.knownCycles)
        assertTrue(schedule.slots.all { it.cycle == WeekSelector.First })
        assertTrue(schedule.slotsOf(2).isEmpty())
    }

    @Test
    fun `les conges ne sont pas des cours et une semaine vide ne remplace pas la reference`() {
        val holiday = lesson("2026-11-09", "00:00", "23:59", "Vacances", kind = "CONGE")
        val schedule = TypicalWeeks.build(ExportFixtures.week(d("2026-11-02"), false) + holiday, Alternation())
        assertEquals(4, schedule.slots.size)
        assertTrue(schedule.slots.none { it.subject == "Vacances" })
        assertTrue(TypicalWeeks.build(listOf(holiday), Alternation()).isEmpty)
        assertTrue(TypicalWeeks.build(emptyList(), alternation).isEmpty)
    }

    @Test
    fun `les entrees sans date ou sans heure sont ignorees`() {
        val broken = listOf(lesson("pas une date", "08:00", "09:00", "X"), lesson("2026-11-02", "", "", "Y"))
        assertTrue(TypicalWeeks.build(broken, Alternation()).isEmpty)
    }

    @Test
    fun `la suggestion d'alternance apparait quand deux semaines se ressemblent peu seulement`() {
        assertTrue(TypicalWeeks.looksAlternating(twoWeeks))
        assertFalse(TypicalWeeks.looksAlternating(ExportFixtures.sameTwice))
        assertFalse(TypicalWeeks.looksAlternating(ExportFixtures.week(d("2026-11-02"), false)))
        assertFalse(TypicalWeeks.looksAlternating(emptyList()))
    }

    @Test
    fun `la fin par defaut est la fin juin de l'annee scolaire`() {
        assertEquals(d("2027-06-30"), TypicalWeeks.defaultEnd(d("2026-11-06")))
        assertEquals(d("2027-06-30"), TypicalWeeks.defaultEnd(d("2027-03-01")))
        assertEquals(d("2028-06-30"), TypicalWeeks.defaultEnd(d("2027-07-15")))
    }
}
