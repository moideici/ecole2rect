package fr.ecole2recte.app.sync

import fr.ecole2recte.app.domain.GradeEntry
import fr.ecole2recte.app.domain.GradeOverview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NotificationPrivacyTest {
    private val today = LocalDate.parse("2026-11-05")

    private fun grade(id: String, date: String?, subject: String = "Maths", value: Double = 14.0) =
        GradeEntry(id, subject, 0, "DS $id", value, 20.0, 1.0, date, null, null, null, true)

    private fun overview(recent: List<GradeEntry>, all: List<GradeEntry> = recent) =
        GradeOverview("15", "", emptyList(), recent = recent, all = all)

    private fun snapshot(grades: GradeOverview) =
        NotificationPlanner.snapshot(grades, emptyList(), fr.ecole2recte.app.domain.ScheduleWeek("", emptyList(), "", ""), emptyList(), today)

    @Test
    fun `une nouvelle note est reperee meme si elle n'est pas parmi les six dernieres`() {
        // Les six « dernières » sont plus récentes ; la nouvelle note date d'avant elles mais n'était pas connue.
        val known = (1..6).map { grade("k$it", "2026-11-0$it") }
        val first = NotificationPlanner.plan(null, snapshot(overview(known.take(6), known)), today)
        val withNew = known + grade("nouvelle", "2026-10-30", "Anglais")
        val second = NotificationPlanner.plan(first.next, snapshot(overview(known.take(6), withNew)), today)
        assertEquals(listOf("Nouvelle note en Anglais"), second.events.map { it.title })
    }

    @Test
    fun `l'historique de l'annee n'est pas annonce comme nouveau mais il est retenu`() {
        val first = NotificationPlanner.plan(null, snapshot(overview(listOf(grade("a", "2026-11-04")))), today)
        val old = grade("ancienne", "2026-09-20")
        val second = NotificationPlanner.plan(first.next, snapshot(overview(listOf(grade("a", "2026-11-04")), listOf(grade("a", "2026-11-04"), old))), today)
        assertEquals(emptyList<NotificationEvent>(), second.events)
        assertTrue("ancienne" in second.next.grades)
    }

    @Test
    fun `une note sans date est annoncee`() {
        val first = NotificationPlanner.plan(null, snapshot(overview(emptyList())), today)
        val second = NotificationPlanner.plan(first.next, snapshot(overview(listOf(grade("x", null)))), today)
        assertEquals(1, second.events.size)
    }

    @Test
    fun `beaucoup de nouvelles notes sont regroupees en une seule notification`() {
        val first = NotificationPlanner.plan(null, snapshot(overview(emptyList())), today)
        val many = (1..7).map { grade("n$it", "2026-11-04", "Matière $it") }
        val second = NotificationPlanner.plan(first.next, snapshot(overview(many.take(6), many)), today)
        assertEquals(1, second.events.size)
        assertEquals("7 nouvelles notes", second.events.single().title)
        assertEquals(7, second.next.grades.size)
    }

    @Test
    fun `cours et devoirs sont discrets, notes et messages se remarquent`() {
        assertEquals(setOf(EventKind.Canceled, EventKind.Changed, EventKind.Homework), EventKind.entries.filter { it.quiet }.toSet())
        assertFalse(EventKind.Grade.quiet)
        assertFalse(EventKind.Message.quiet)
    }

    @Test
    fun `les canaux ont des identifiants distincts et les anciens sont retires`() {
        val ids = EventKind.entries.map { it.channelId }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(EventKind.legacyChannelIds.none { it in ids })
    }

    @Test
    fun `le titre de l'ecran verrouille ne contient aucun detail`() {
        EventKind.entries.forEach { kind ->
            assertTrue(kind.publicTitle.isNotBlank())
            assertTrue("\\d".toRegex().find(kind.publicTitle) == null)
        }
    }
}
