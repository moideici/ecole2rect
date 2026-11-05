package fr.ecole2recte.app.ui

import fr.ecole2recte.app.domain.Homework
import fr.ecole2recte.app.ui.homework.groupHomework
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HomeworkGroupsTest {
    // Jeudi 5 novembre 2026.
    private val today = LocalDate.of(2026, 11, 5)

    private fun task(id: String, due: String, subject: String = "Maths", done: Boolean = false) =
        Homework(title = id, subject = subject, due = due, colorIndex = 0, id = id, dueDate = due, done = done)

    @Test
    fun `les devoirs sont ranges par echeance`() {
        val groups = groupHomework(
            listOf(
                task("lundi", "2026-11-09"),
                task("aujourdhui", "2026-11-05"),
                task("demain", "2026-11-06"),
                task("samedi", "2026-11-07"),
            ),
            today,
        )
        assertEquals(listOf("Aujourd'hui", "Demain", "Cette semaine", "Plus tard"), groups.map { it.title })
        assertEquals(listOf("samedi"), groups[2].items.map { it.id })
        assertEquals(listOf("lundi"), groups[3].items.map { it.id })
    }

    @Test
    fun `les devoirs faits vont a la fin`() {
        val groups = groupHomework(listOf(task("a", "2026-11-05", done = true), task("b", "2026-11-06")), today)
        assertEquals(listOf("Demain", "Faits"), groups.map { it.title })
    }

    @Test
    fun `le filtre par matiere ne garde que cette matiere`() {
        val groups = groupHomework(listOf(task("a", "2026-11-05", "Maths"), task("b", "2026-11-05", "Anglais")), today, "Anglais")
        assertEquals(listOf("b"), groups.single().items.map { it.id })
    }

    @Test
    fun `un groupe vide n'apparait pas`() {
        assertEquals(emptyList<String>(), groupHomework(emptyList(), today).map { it.title })
    }
}
