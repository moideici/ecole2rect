package fr.ecole2recte.app.sync

import fr.ecole2recte.app.widget.WidgetLogic
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class WidgetLogicTest {
    private fun slot(date: String, start: String, end: String, subject: String = "Maths", canceled: Boolean = false) =
        WidgetSlot(date, start, end, subject, "B204", "M. Petit", canceled, 0)

    private val now = LocalDateTime.of(2026, 11, 5, 9, 50)

    @Test
    fun `le prochain cours saute les cours finis et annules`() {
        val slots = listOf(
            slot("2026-11-05", "08:00", "09:00", "Passé"),
            slot("2026-11-05", "10:00", "11:00", "Annulé", canceled = true),
            slot("2026-11-05", "13:30", "14:30", "Anglais"),
            slot("2026-11-06", "08:00", "09:00", "Demain"),
        )
        assertEquals(listOf("Anglais", "Demain"), WidgetLogic.upcoming(slots, now).map { it.subject })
    }

    @Test
    fun `le statut est calcule a l'affichage`() {
        assertEquals("dans 10 min", WidgetLogic.status(slot("2026-11-05", "10:00", "11:00"), now))
        assertEquals("dans 3 h 40", WidgetLogic.status(slot("2026-11-05", "13:30", "14:30"), now))
        assertEquals("En cours · jusqu'à 10:30", WidgetLogic.status(slot("2026-11-05", "09:30", "10:30"), now))
        assertEquals("demain 08:00", WidgetLogic.status(slot("2026-11-06", "08:00", "09:00"), now))
    }

    @Test
    fun `le meme cours donne un autre statut quelques heures plus tard`() {
        val lesson = slot("2026-11-05", "13:30", "14:30")
        assertEquals("dans 3 h 40", WidgetLogic.status(lesson, now))
        assertEquals("dans 15 min", WidgetLogic.status(lesson, LocalDateTime.of(2026, 11, 5, 13, 15)))
    }

    @Test
    fun `les lignes qui tiennent dans la hauteur`() {
        assertEquals(1, WidgetLogic.rowsThatFit(60f, 36f, 34f, 9))
        assertEquals(4, WidgetLogic.rowsThatFit(200f, 36f, 38f, 9))
        assertEquals(9, WidgetLogic.rowsThatFit(900f, 36f, 34f, 9))
    }

    @Test
    fun `la semaine resume chaque jour`() {
        val days = WidgetLogic.week(
            listOf(
                slot("2026-11-05", "08:00", "09:00"),
                slot("2026-11-05", "15:00", "16:30"),
                slot("2026-11-05", "10:00", "11:00", canceled = true),
                slot("2026-11-06", "09:00", "10:00"),
                slot("2026-11-02", "08:00", "09:00"),
            ),
            LocalDate.of(2026, 11, 5),
        )
        assertEquals(listOf("Jeu 5", "Ven 6"), days.map { it.label })
        assertEquals(2, days.first().lessons)
        assertEquals(1, days.first().canceled)
        assertEquals("08:00", days.first().first)
        assertEquals("16:30", days.first().last)
    }
}
