package fr.ecole2recte.app.sync

import fr.ecole2recte.app.domain.GradeOverview
import fr.ecole2recte.app.domain.Homework
import fr.ecole2recte.app.domain.ScheduleWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Le rappel « Devoirs pour demain » ne part qu'à partir de l'heure choisie, une seule fois. */
class EveningReminderTest {
    // Jeudi 5 novembre 2026 : demain, c'est le 6.
    private val today = LocalDate.of(2026, 11, 5)
    private val rules = NotificationRules(eveningHour = 18)

    private fun task(id: String, due: String = "2026-11-06", done: Boolean = false) =
        Homework("Exercices 12", "Maths", due, 0, id, due, done)

    private fun snapshot(vararg tasks: Homework) =
        NotificationPlanner.snapshot(GradeOverview("–", "", emptyList()), emptyList(), ScheduleWeek("", emptyList()), tasks.toList(), today)

    private fun start(): NotifiedIds = NotificationPlanner.plan(null, snapshot(), today, rules, hour = 9).next

    @Test
    fun `avant l'heure choisie rien ne part et rien n'est retenu`() {
        val result = NotificationPlanner.plan(start(), snapshot(task("a")), today, rules, hour = 17)
        assertEquals(emptyList<NotificationEvent>(), result.events)
        assertTrue(result.next.homework.isEmpty())
    }

    @Test
    fun `a partir de l'heure choisie le rappel part`() {
        val result = NotificationPlanner.plan(start(), snapshot(task("a")), today, rules, hour = 18)
        val event = result.events.single()
        assertEquals(EventKind.Homework, event.kind)
        assertEquals("Pour demain : Maths", event.title)
    }

    @Test
    fun `un rappel manque l'apres-midi part au premier passage du soir`() {
        val afternoon = NotificationPlanner.plan(start(), snapshot(task("a")), today, rules, hour = 14)
        val evening = NotificationPlanner.plan(afternoon.next, snapshot(task("a")), today, rules, hour = 19)
        assertEquals(1, evening.events.size)
    }

    @Test
    fun `pas de doublon dans la meme soiree`() {
        val first = NotificationPlanner.plan(start(), snapshot(task("a")), today, rules, hour = 19)
        val second = NotificationPlanner.plan(first.next, snapshot(task("a")), today, rules, hour = 21)
        assertEquals(1, first.events.size)
        assertEquals(emptyList<NotificationEvent>(), second.events)
    }

    @Test
    fun `un devoir fait avant le soir ne rappelle rien`() {
        val result = NotificationPlanner.plan(start(), snapshot(task("a", done = true)), today, rules, hour = 20)
        assertEquals(emptyList<NotificationEvent>(), result.events)
    }

    @Test
    fun `l'heure choisie est respectee pour chacun des choix`() {
        NotificationRules.EVENING_HOURS.forEach { chosen ->
            val custom = NotificationRules(eveningHour = chosen)
            assertFalse(custom.reminderOpen(chosen - 1))
            assertTrue(custom.reminderOpen(chosen))
            assertTrue(custom.reminderOpen(23))
        }
    }

    @Test
    fun `une heure inconnue ne restreint rien et les valeurs abimees sont ramenees aux choix`() {
        assertTrue(rules.reminderOpen(null))
        assertEquals(16, NotificationRules.clampEveningHour(3))
        assertEquals(20, NotificationRules.clampEveningHour(23))
        assertEquals(18, NotificationRules().eveningHour)
    }

    @Test
    fun `le rappel desactive ne notifie pas`() {
        val off = NotificationRules(homework = false)
        val result = NotificationPlanner.plan(start(), snapshot(task("a")), today, off, hour = 19)
        assertEquals(emptyList<NotificationEvent>(), result.events)
    }
}
