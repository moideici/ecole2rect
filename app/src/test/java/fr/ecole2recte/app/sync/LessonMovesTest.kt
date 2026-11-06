package fr.ecole2recte.app.sync

import fr.ecole2recte.app.domain.LessonChangeKind
import fr.ecole2recte.app.domain.ScheduleChanges
import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.domain.Subject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Cours déplacé, remplacé, salle ou professeur changés : ce que la comparaison repère et ce que la notification dit. */
class LessonMovesTest {
    // Jeudi 5 novembre 2026.
    private val today = LocalDate.of(2026, 11, 5)
    private val day = "2026-11-05"

    private fun lesson(
        name: String = "Maths",
        start: String = "10:00",
        end: String = "10:55",
        room: String = "B12",
        teacher: String = "M. Petit",
        canceled: Boolean = false,
        date: String = day,
    ) = ScheduleEntry(Subject(name, teacher, room, 0), "", canceled, date, start, end)

    private fun snap(vararg lessons: ScheduleEntry) = SyncSnapshot(emptyList(), emptyList(), lessons.filter { it.canceled }, emptyList(), lessons.toList())

    private fun plan(before: List<ScheduleEntry>, after: List<ScheduleEntry>): List<NotificationEvent> {
        val first = NotificationPlanner.plan(null, snap(*before.toTypedArray()), today)
        return NotificationPlanner.plan(first.next, snap(*after.toTypedArray()), today).events
    }

    @Test
    fun `un cours deplace dit l'ancienne et la nouvelle heure`() {
        val events = plan(listOf(lesson(start = "10:00", end = "10:55")), listOf(lesson(start = "14:00", end = "14:55")))
        val event = events.single()
        assertEquals(EventKind.Changed, event.kind)
        assertEquals("Cours déplacé : Maths", event.title)
        assertEquals("aujourd'hui · 10:00 → 14:00", event.text)
    }

    @Test
    fun `un deplacement avec changement de salle dit les deux`() {
        val event = plan(listOf(lesson()), listOf(lesson(start = "14:00", end = "14:55", room = "C03"))).single()
        assertEquals("aujourd'hui · 10:00 → 14:00, salle B12 → C03", event.text)
    }

    @Test
    fun `un cours remplace par une autre matiere est signale`() {
        val event = plan(listOf(lesson("Maths")), listOf(lesson("Anglais", teacher = "Mme Roux"))).single()
        assertEquals("Cours remplacé : Maths → Anglais", event.title)
        assertEquals("aujourd'hui à 10:00 · professeur M. Petit → Mme Roux", event.text)
    }

    @Test
    fun `un cours annule puis reprogramme le meme jour n'est annonce que comme deplace`() {
        val before = listOf(lesson(start = "10:00"))
        val after = listOf(lesson(start = "10:00", canceled = true), lesson(start = "14:00", end = "14:55"))
        val events = plan(before, after)
        assertEquals(listOf("Cours déplacé : Maths"), events.map { it.title })
        assertEquals(emptyList<EventKind>(), events.filter { it.kind == EventKind.Canceled }.map { it.kind })
    }

    @Test
    fun `un cours simplement annule reste une annulation`() {
        val events = plan(listOf(lesson()), listOf(lesson(canceled = true)))
        assertEquals(listOf(EventKind.Canceled), events.map { it.kind })
    }

    @Test
    fun `un deplacement n'est notifie qu'une fois`() {
        val first = NotificationPlanner.plan(null, snap(lesson()), today)
        val moved = NotificationPlanner.plan(first.next, snap(lesson(start = "14:00", end = "14:55")), today)
        val again = NotificationPlanner.plan(moved.next, snap(lesson(start = "14:00", end = "14:55")), today)
        assertEquals(1, moved.events.size)
        assertEquals(emptyList<NotificationEvent>(), again.events)
    }

    @Test
    fun `deux cours de la meme matiere qui gardent leur heure ne sont pas pris pour un deplacement`() {
        val both = listOf(lesson(start = "08:00", end = "08:55"), lesson(start = "10:00"))
        assertEquals(emptyList<NotificationEvent>(), plan(both, both))
    }

    @Test
    fun `la comparaison ignore les jours sans cours d'avant`() {
        val changes = ScheduleChanges.diff(emptyList(), listOf(lesson()), setOf(day))
        assertEquals(emptyList<Any>(), changes)
    }

    @Test
    fun `chaque cours deplace est rapproche du plus proche de sa matiere`() {
        val before = listOf(lesson(start = "08:00", end = "08:55"), lesson(start = "13:00", end = "13:55"))
        val after = listOf(lesson(start = "09:00", end = "09:55"), lesson(start = "14:00", end = "14:55"))
        val moves = ScheduleChanges.diff(before, after, setOf(day))
        assertEquals(listOf("08:00 → 09:00", "13:00 → 14:00"), moves.map { it.parts.first() })
        assertTrue(moves.all { it.kind == LessonChangeKind.Moved })
    }

    @Test
    fun `l'annotation pose l'ancienne valeur sur le cours change seulement`() {
        val before = listOf(lesson(start = "10:00"), lesson("Anglais", start = "11:00", end = "11:55"))
        val after = listOf(lesson(start = "14:00", end = "14:55", room = "C03"), lesson("Anglais", start = "11:00", end = "11:55"))
        val annotated = ScheduleChanges.annotate(before, after, setOf(day))
        val moved = annotated[0]
        assertEquals("10:00", moved.before?.start)
        assertEquals("Déplacé", ScheduleChanges.tag(moved))
        assertEquals(
            listOf("Horaire: 10:00 – 10:55 -> 14:00 – 14:55", "Salle: B12 -> C03"),
            ScheduleChanges.lines(moved).map { "${it.label}: ${it.old} -> ${it.new}" },
        )
        assertNull(annotated[1].before)
        assertNull(ScheduleChanges.tag(annotated[1]))
    }

    @Test
    fun `un cours remplace est etiquete remplace`() {
        val annotated = ScheduleChanges.annotate(listOf(lesson("Maths")), listOf(lesson("Anglais")), setOf(day))
        assertEquals("Remplacé", ScheduleChanges.tag(annotated.single()))
        assertEquals("Matière", ScheduleChanges.lines(annotated.single()).first().label)
    }

    @Test
    fun `un cours retabli n'est pas annote comme change`() {
        val annotated = ScheduleChanges.annotate(listOf(lesson(canceled = true)), listOf(lesson()), setOf(day))
        assertNull(annotated.single().before)
    }
}
