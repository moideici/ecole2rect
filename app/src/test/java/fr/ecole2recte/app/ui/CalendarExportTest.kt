package fr.ecole2recte.app.ui

import fr.ecole2recte.app.domain.CalendarExport
import fr.ecole2recte.app.domain.Homework
import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.domain.Subject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CalendarExportTest {
    private val paris = ZoneId.of("Europe/Paris")

    private fun entry(
        date: String = "2026-11-05",
        start: String = "10:15",
        end: String = "11:10",
        canceled: Boolean = false,
        room: String = "B12",
        teacher: String = "M. PETIT",
        note: String = "",
        exam: Boolean = false,
    ) = ScheduleEntry(Subject("Maths", teacher, room, 0), "", canceled, date, start, end, exam, note)

    @Test
    fun `un cours donne titre, horaire local, salle en lieu et professeur en description`() {
        val fields = CalendarExport.lesson(entry(), paris)!!
        assertEquals("Maths", fields.title)
        // 10 h 15 à Paris le 5 novembre (heure d'hiver, UTC+1) = 09 h 15 UTC.
        assertEquals(Instant.parse("2026-11-05T09:15:00Z").toEpochMilli(), fields.beginMillis)
        assertEquals(Instant.parse("2026-11-05T10:10:00Z").toEpochMilli(), fields.endMillis)
        assertFalse(fields.allDay)
        assertEquals("B12", fields.location)
        assertTrue(fields.description.contains("Professeur : M. PETIT"))
    }

    @Test
    fun `l'heure d'ete est respectee`() {
        val fields = CalendarExport.lesson(entry(date = "2026-06-10", start = "08:00", end = "08:55"), paris)!!
        assertEquals(Instant.parse("2026-06-10T06:00:00Z").toEpochMilli(), fields.beginMillis)
    }

    @Test
    fun `un cours annule est signale dans le titre et la description`() {
        val fields = CalendarExport.lesson(entry(canceled = true), paris)!!
        assertEquals("Maths (annulé)", fields.title)
        assertTrue(fields.description.contains("annulé"))
    }

    @Test
    fun `sans salle ni professeur les champs restent vides ou absents`() {
        val fields = CalendarExport.lesson(entry(room = "", teacher = ""), paris)!!
        assertEquals("", fields.location)
        assertFalse(fields.description.contains("Professeur"))
    }

    @Test
    fun `un horaire inutilisable ne donne pas d'evenement`() {
        assertNull(CalendarExport.lesson(entry(start = ""), paris))
        assertNull(CalendarExport.lesson(entry(date = "pas une date"), paris))
    }

    @Test
    fun `une fin avant le debut donne une duree par defaut`() {
        val fields = CalendarExport.lesson(entry(start = "10:00", end = "09:00"), paris)!!
        assertEquals(55 * 60_000L, fields.endMillis - fields.beginMillis)
    }

    @Test
    fun `un controle est un evenement sur la journee en UTC`() {
        val task = Homework("Réviser le chapitre 4", "Maths", "jeudi", 0, "7", "2026-11-12", false, true, "Réviser le chapitre 4\n\nExercices 12 et 13")
        val fields = CalendarExport.homework(task)!!
        assertEquals("Contrôle de Maths", fields.title)
        assertTrue(fields.allDay)
        assertEquals(Instant.parse("2026-11-12T00:00:00Z").toEpochMilli(), fields.beginMillis)
        assertEquals(Instant.parse("2026-11-13T00:00:00Z").toEpochMilli(), fields.endMillis)
        assertTrue(fields.description.startsWith("Réviser le chapitre 4\nExercices 12 et 13"))
    }

    @Test
    fun `un devoir ordinaire s'appelle devoir et un long enonce est coupe`() {
        val task = Homework("x", "Anglais", "", 0, "8", "2026-11-12", false, false, "mot ".repeat(300))
        val fields = CalendarExport.homework(task)!!
        assertEquals("Devoir de Anglais", fields.title)
        assertTrue(fields.description.length < 520)
        assertTrue(fields.description.contains("…"))
    }

    @Test
    fun `un devoir sans date n'est pas exporte`() {
        assertNull(CalendarExport.homework(Homework("x", "Anglais", "", 0)))
    }
}
