package fr.ecole2recte.app.ui

import fr.ecole2recte.app.domain.Homework
import fr.ecole2recte.app.ui.homework.HomeworkFilter
import fr.ecole2recte.app.ui.homework.countByFilter
import fr.ecole2recte.app.ui.homework.filterHomework
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeworkFilterTest {
    private fun task(id: String, done: Boolean = false, exam: Boolean = false) =
        Homework("Titre $id", "Maths", "demain", 0, id, "2026-11-06", done, exam)

    private val items = listOf(
        task("a"),
        task("b", done = true),
        task("c", exam = true),
        task("d", done = true, exam = true),
    )

    private fun ids(filter: HomeworkFilter) = filterHomework(items, filter).map { it.id }

    @Test
    fun `chaque filtre garde les bons devoirs`() {
        assertEquals(listOf("a", "b", "c", "d"), ids(HomeworkFilter.All))
        assertEquals(listOf("a", "c"), ids(HomeworkFilter.Todo))
        assertEquals(listOf("b", "d"), ids(HomeworkFilter.Done))
        assertEquals(listOf("c", "d"), ids(HomeworkFilter.Exams))
    }

    @Test
    fun `les compteurs des puces suivent les filtres`() {
        val counts = countByFilter(items)
        assertEquals(4, counts[HomeworkFilter.All])
        assertEquals(2, counts[HomeworkFilter.Todo])
        assertEquals(2, counts[HomeworkFilter.Done])
        assertEquals(2, counts[HomeworkFilter.Exams])
    }

    @Test
    fun `un filtre retenu inconnu ou absent donne Tous`() {
        assertEquals(HomeworkFilter.All, HomeworkFilter.parse(null))
        assertEquals(HomeworkFilter.All, HomeworkFilter.parse("n'importe quoi"))
        assertEquals(HomeworkFilter.Exams, HomeworkFilter.parse("Exams"))
    }
}
