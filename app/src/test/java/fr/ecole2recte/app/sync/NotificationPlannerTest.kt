package fr.ecole2recte.app.sync

import fr.ecole2recte.app.domain.GradeEntry
import fr.ecole2recte.app.domain.GradeOverview
import fr.ecole2recte.app.domain.Homework
import fr.ecole2recte.app.domain.MessagePreview
import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.domain.ScheduleWeek
import fr.ecole2recte.app.domain.Subject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class NotificationPlannerTest {
    // Jeudi 5 novembre 2026.
    private val today = LocalDate.of(2026, 11, 5)

    private fun grade(id: String, value: Double? = 16.0, subject: String = "Maths", label: String = "DS 1") =
        GradeEntry(id, subject, 0, label, value, 20.0, 1.0, "2026-11-04", null, null, null, true)

    private fun message(id: String, unread: Boolean = true) = MessagePreview("Vie scolaire", "Réunion", unread, id, "10:00")

    private fun lesson(date: String, canceled: Boolean, start: String = "13:30") =
        ScheduleEntry(Subject("Maths", "", "", 0), "", canceled, date, start, "14:30")

    private fun task(id: String, due: String, done: Boolean = false) =
        Homework("Exercices 12", "Maths", due, 0, id, due, done)

    private fun snapshot(
        grades: List<GradeEntry> = emptyList(),
        messages: List<MessagePreview> = emptyList(),
        lessons: List<ScheduleEntry> = emptyList(),
        homework: List<Homework> = emptyList(),
    ) = NotificationPlanner.snapshot(
        GradeOverview("15", "", emptyList(), recent = grades),
        messages,
        ScheduleWeek("", lessons, "2026-11-02", today.toString()),
        homework,
        today,
    )

    @Test
    fun `le premier passage retient l'existant sans rien notifier`() {
        val result = NotificationPlanner.plan(
            null,
            snapshot(grades = listOf(grade("1")), messages = listOf(message("m1")), lessons = listOf(lesson("2026-11-05", true))),
            today,
        )
        assertEquals(emptyList<NotificationEvent>(), result.events)
        assertEquals(setOf("1"), result.next.grades)
        assertEquals(setOf("m1"), result.next.messages)
    }

    @Test
    fun `une nouvelle note est notifiee`() {
        val first = NotificationPlanner.plan(null, snapshot(grades = listOf(grade("1"))), today)
        val second = NotificationPlanner.plan(first.next, snapshot(grades = listOf(grade("2", 14.5, "Anglais", "Oral"), grade("1"))), today)
        val event = second.events.single()
        assertEquals(EventKind.Grade, event.kind)
        assertEquals("Nouvelle note en Anglais", event.title)
        assertEquals("14,5 / 20 · Oral", event.text)
    }

    @Test
    fun `une note absente n'est jamais notifiee`() {
        val first = NotificationPlanner.plan(null, snapshot(), today)
        val second = NotificationPlanner.plan(first.next, snapshot(grades = listOf(grade("2", value = null))), today)
        assertEquals(emptyList<NotificationEvent>(), second.events)
    }

    @Test
    fun `un nouveau message non lu est notifie, un message lu non`() {
        val first = NotificationPlanner.plan(null, snapshot(), today)
        val second = NotificationPlanner.plan(first.next, snapshot(messages = listOf(message("m1"), message("m2", unread = false))), today)
        val event = second.events.single()
        assertEquals(EventKind.Message, event.kind)
        assertEquals("Vie scolaire", event.title)
        assertEquals("Réunion", event.text)
    }

    @Test
    fun `un cours annule aujourd'hui ou demain est notifie, pas dans une semaine`() {
        val first = NotificationPlanner.plan(null, snapshot(), today)
        val second = NotificationPlanner.plan(
            first.next,
            snapshot(
                lessons = listOf(
                    lesson("2026-11-05", true),
                    lesson("2026-11-06", true, "08:00"),
                    lesson("2026-11-12", true),
                    lesson("2026-11-05", false, "15:00"),
                ),
            ),
            today,
        )
        assertEquals(listOf("Cours annulé : Maths", "Cours annulé : Maths"), second.events.map { it.title })
        assertEquals(listOf("aujourd'hui à 13:30", "demain à 08:00"), second.events.map { it.text })
    }

    @Test
    fun `un devoir pour demain non fait est notifie`() {
        val first = NotificationPlanner.plan(null, snapshot(), today)
        val second = NotificationPlanner.plan(
            first.next,
            snapshot(homework = listOf(task("a", "2026-11-06"), task("b", "2026-11-06", done = true), task("c", "2026-11-09"))),
            today,
        )
        val event = second.events.single()
        assertEquals(EventKind.Homework, event.kind)
        assertEquals("Pour demain : Maths", event.title)
    }

    @Test
    fun `rien n'est notifie deux fois`() {
        val data = snapshot(grades = listOf(grade("2")), messages = listOf(message("m2")), lessons = listOf(lesson("2026-11-05", true)))
        val first = NotificationPlanner.plan(NotifiedIds(), data, today)
        assertEquals(3, first.events.size)
        val again = NotificationPlanner.plan(first.next, data, today)
        assertEquals(emptyList<NotificationEvent>(), again.events)
    }

    @Test
    fun `un message relu puis de nouveau non lu ne notifie pas une seconde fois`() {
        val first = NotificationPlanner.plan(NotifiedIds(), snapshot(messages = listOf(message("m1"))), today)
        val read = NotificationPlanner.plan(first.next, snapshot(), today)
        val again = NotificationPlanner.plan(read.next, snapshot(messages = listOf(message("m1"))), today)
        assertEquals(emptyList<NotificationEvent>(), again.events)
    }

    @Test
    fun `les evenements sont ordonnes du plus urgent au moins urgent`() {
        val result = NotificationPlanner.plan(
            NotifiedIds(),
            snapshot(
                grades = listOf(grade("2")),
                messages = listOf(message("m2")),
                lessons = listOf(lesson("2026-11-05", true)),
                homework = listOf(task("a", "2026-11-06")),
            ),
            today,
        )
        assertEquals(listOf(EventKind.Canceled, EventKind.Homework, EventKind.Grade, EventKind.Message), result.events.map { it.kind })
    }

    @Test
    fun `l'instantane des widgets survit a l'aller-retour JSON`() {
        val snapshot = WidgetSnapshot(
            updatedAt = 42L,
            nextClass = WidgetLesson("Maths", "10:15  ·  dans 25 min", "Salle C011"),
            tasks = listOf(WidgetTask("Anglais", "Reading", "Vendredi 6 novembre")),
        )
        assertEquals(snapshot, WidgetSnapshot.fromJson(snapshot.toJson()))
        val empty = WidgetSnapshot(1L, null, emptyList())
        assertEquals(empty, WidgetSnapshot.fromJson(empty.toJson()))
        assertNull(WidgetSnapshot.fromJson("pas du json"))
        assertNotNull(WidgetSnapshot.fromJson(empty.toJson())?.tasks)
    }
}
