package fr.ecole2recte.app.ui

import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.domain.Subject
import fr.ecole2recte.app.ui.schedule.ScheduleZoom
import fr.ecole2recte.app.ui.schedule.blockContent
import fr.ecole2recte.app.ui.schedule.buildWeekTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekLayoutTest {
    private fun lesson(date: String, name: String, start: String, end: String, canceled: Boolean = false, modified: Boolean = false) =
        ScheduleEntry(Subject(name, "", "B1", 0), "", canceled = canceled, date = date, start = start, end = end, modified = modified)

    @Test
    fun `les colonnes de la semaine partagent la meme echelle horaire`() {
        val byDate = mapOf(
            "2026-11-02" to listOf(lesson("2026-11-02", "Maths", "08:00", "08:55")),
            "2026-11-03" to listOf(lesson("2026-11-03", "Anglais", "14:00", "15:55")),
        )
        val week = buildWeekTimeline(listOf("2026-11-02", "2026-11-03", "2026-11-04"), byDate)
        assertEquals(8 * 60, week.fromMin)
        assertEquals(16 * 60, week.toMin)
        assertEquals(3, week.days.size)
        assertEquals(480, week.duration)
        assertTrue(week.days[2].timeline.lessons.isEmpty())
    }

    @Test
    fun `les doubles heures et les cours simultanes sont deja regroupes dans chaque colonne`() {
        val day = "2026-11-02"
        val week = buildWeekTimeline(
            listOf(day),
            mapOf(day to listOf(lesson(day, "Maths", "08:00", "08:55"), lesson(day, "Maths", "08:55", "09:50"), lesson(day, "Anglais", "10:00", "11:00"), lesson(day, "Espagnol", "10:00", "11:00"))),
        )
        val lessons = week.days.single().timeline.lessons
        assertEquals(3, lessons.size)
        assertTrue(lessons.first().isDouble)
        assertEquals(2, lessons.last().lanes)
    }

    @Test
    fun `une semaine sans cours garde une plage par defaut`() {
        val week = buildWeekTimeline(listOf("2026-11-02", "2026-11-03"), emptyMap())
        assertEquals(8 * 60, week.fromMin)
        assertEquals(17 * 60, week.toMin)
    }

    @Test
    fun `les cours annules et modifies restent dans la frise`() {
        val day = "2026-11-02"
        val week = buildWeekTimeline(
            listOf(day),
            mapOf(day to listOf(lesson(day, "Maths", "08:00", "08:55", canceled = true), lesson(day, "SVT", "09:00", "09:55", modified = true))),
        )
        val lessons = week.days.single().timeline.lessons
        assertTrue(lessons[0].entries.all { it.canceled })
        assertTrue(lessons[1].entries.all { it.modified })
    }

    @Test
    fun `le zoom reste dans ses bornes et ignore les valeurs absurdes`() {
        assertEquals(ScheduleZoom.MAX, ScheduleZoom.pinch(2.5f, 10f), 0f)
        assertEquals(ScheduleZoom.MIN, ScheduleZoom.pinch(0.6f, 0.01f), 0f)
        assertEquals(1f, ScheduleZoom.clamp(Float.NaN), 0f)
        assertEquals(1f, ScheduleZoom.pinch(1f, 0f), 0f)
        assertEquals(1.5f, ScheduleZoom.pinch(1f, 1.5f), 0f)
    }

    @Test
    fun `les boutons plus et moins font des crans reguliers puis s'arretent`() {
        assertEquals(1.25f, ScheduleZoom.zoomIn(1f), 0f)
        assertEquals(0.8f, ScheduleZoom.zoomOut(1f), 0f)
        var zoom = 1f
        repeat(20) { zoom = ScheduleZoom.zoomIn(zoom) }
        assertEquals(ScheduleZoom.MAX, zoom, 0f)
        assertFalse(ScheduleZoom.canZoomIn(zoom))
        assertTrue(ScheduleZoom.canZoomOut(zoom))
        repeat(40) { zoom = ScheduleZoom.zoomOut(zoom) }
        assertEquals(ScheduleZoom.MIN, zoom, 0f)
        assertFalse(ScheduleZoom.canZoomOut(zoom))
    }

    @Test
    fun `le pourcentage affiche l'echelle`() {
        assertEquals(100, ScheduleZoom.percent(1f))
        assertEquals(125, ScheduleZoom.percent(1.25f))
        assertEquals(300, ScheduleZoom.percent(99f))
    }

    @Test
    fun `un bloc court n'ecrit que la matiere et un grand bloc ajoute salle horaire et mention`() {
        val small = blockContent(heightDp = 20f, widthDp = 60f)
        assertEquals(1, small.titleLines)
        assertFalse(small.showRoom)
        assertFalse(small.showTime)
        assertFalse(small.showTag)
        val big = blockContent(heightDp = 110f, widthDp = 60f)
        assertTrue(big.showRoom && big.showTime && big.showTag)
        val narrow = blockContent(heightDp = 110f, widthDp = 40f)
        assertFalse(narrow.showTime)
        assertTrue(narrow.showRoom)
    }
}
