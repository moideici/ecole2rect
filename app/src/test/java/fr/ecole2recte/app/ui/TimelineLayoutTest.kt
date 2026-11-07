package fr.ecole2recte.app.ui

import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.domain.Subject
import fr.ecole2recte.app.ui.schedule.buildDayTimeline
import fr.ecole2recte.app.ui.schedule.formatMinutes
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineLayoutTest {
    private fun lesson(name: String, start: String, end: String, canceled: Boolean = false) =
        ScheduleEntry(Subject(name, "", "B1", 0), "", canceled = canceled, date = "2026-11-05", start = start, end = end)

    @Test
    fun `deux cours de la meme matiere qui se suivent forment une double heure`() {
        val day = buildDayTimeline(listOf(lesson("Maths", "08:00", "08:55"), lesson("Maths", "08:55", "09:50")))
        assertEquals(1, day.lessons.size)
        assertEquals("Double heure", day.lessons.single().multiLabel)
        assertEquals(110, day.lessons.single().duration)
    }

    @Test
    fun `deux matieres differentes ne se regroupent pas`() {
        val day = buildDayTimeline(listOf(lesson("Maths", "08:00", "08:55"), lesson("Anglais", "08:55", "09:50")))
        assertEquals(2, day.lessons.size)
        assertEquals(emptyList<String>(), day.gaps.map { it.label })
    }

    @Test
    fun `un cours annule n'est pas fondu dans une double heure`() {
        val day = buildDayTimeline(listOf(lesson("Maths", "08:00", "08:55"), lesson("Maths", "08:55", "09:50", canceled = true)))
        assertEquals(2, day.lessons.size)
    }

    @Test
    fun `les temps libres sont nommes selon leur duree et le midi`() {
        val day = buildDayTimeline(
            listOf(
                lesson("A", "08:00", "08:55"),
                lesson("B", "09:10", "10:05"), // 15 min : récréation
                lesson("C", "10:35", "11:30"), // 30 min : pause
                lesson("D", "13:00", "13:55"), // 1 h 30 : pause déjeuner
                lesson("E", "15:55", "16:50"), // 2 h : heure libre
            ),
        )
        assertEquals(listOf("Récréation", "Pause", "Pause déjeuner", "Heure libre"), day.gaps.map { it.label })
    }

    @Test
    fun `les cours simultanes se partagent la largeur`() {
        val day = buildDayTimeline(listOf(lesson("Anglais G1", "10:00", "11:00"), lesson("Espagnol G2", "10:00", "11:00"), lesson("Maths", "11:00", "12:00")))
        val parallel = day.lessons.filter { it.startMin == 600 }
        assertEquals(listOf(0, 1), parallel.map { it.lane }.sorted())
        assertEquals(listOf(2, 2), parallel.map { it.lanes })
        assertEquals(1, day.lessons.first { it.startMin == 660 }.lanes)
    }

    @Test
    fun `la frise commence et finit sur une heure ronde`() {
        val day = buildDayTimeline(listOf(lesson("A", "08:15", "09:10"), lesson("B", "16:05", "17:20")))
        assertEquals(8 * 60, day.fromMin)
        assertEquals(18 * 60, day.toMin)
    }

    @Test
    fun `les durees s'ecrivent en francais`() {
        assertEquals("55 min", formatMinutes(55))
        assertEquals("2 h", formatMinutes(120))
        assertEquals("1 h 05", formatMinutes(65))
    }

    @Test
    fun `une journee sans cours donne une frise vide`() {
        val day = buildDayTimeline(emptyList())
        assertEquals(0, day.lessons.size)
    }
}
