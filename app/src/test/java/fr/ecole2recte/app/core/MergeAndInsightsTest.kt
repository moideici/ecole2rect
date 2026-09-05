package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.model.Grade
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.Lesson
import fr.ecole2recte.app.core.model.MessageItem
import fr.ecole2recte.app.core.repo.Insights
import fr.ecole2recte.app.core.repo.Merge
import fr.ecole2recte.app.core.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class MergeAndInsightsTest {

    private val monday = LocalDate.of(2026, 9, 7)

    private fun lesson(id: String, day: LocalDate, hour: Int, subject: String = "Maths") = Lesson(
        id = id,
        subject = subject,
        start = day.atTime(hour, 0),
        end = day.atTime(hour + 1, 0),
    )

    private fun homework(id: String, due: LocalDate, content: String = "", done: Boolean = false) =
        HomeworkItem(id = id, subject = "Maths", dueDate = due, content = content, done = done)

    @Test
    fun `une synchronisation ne cree pas de doublons`() {
        val existing = listOf(lesson("1", monday, 8), lesson("2", monday, 10))
        val fetched = listOf(lesson("1", monday, 8), lesson("2", monday, 10), lesson("3", monday, 14))

        val merged = Merge.lessons(existing, fetched, monday, monday.plusDays(6))

        assertEquals(listOf("1", "2", "3"), merged.map { it.id })
    }

    @Test
    fun `les cours hors de la fenetre demandee sont conserves`() {
        val lastWeek = lesson("ancien", monday.minusDays(7), 8)
        val merged = Merge.lessons(listOf(lastWeek), listOf(lesson("1", monday, 8)), monday, monday.plusDays(6))

        assertEquals(listOf("ancien", "1"), merged.map { it.id })
    }

    @Test
    fun `un contenu deja charge n est pas perdu par la liste courte`() {
        val existing = listOf(homework("901", monday, content = "Dissertation"))
        val fetched = listOf(homework("901", monday), homework("902", monday.plusDays(1)))

        val merged = Merge.homework(existing, fetched)

        assertEquals("Dissertation", merged.first { it.id == "901" }.content)
        assertFalse(merged.first { it.id == "902" }.hasContent)
    }

    @Test
    fun `le detail d un jour ne touche pas aux autres jours`() {
        val existing = listOf(homework("901", monday), homework("902", monday.plusDays(1)))
        val fetched = listOf(homework("903", monday, content = "Nouveau devoir"))

        val merged = Merge.homeworkForDate(existing, fetched, monday)

        assertEquals(listOf("903", "902"), merged.map { it.id })
    }

    @Test
    fun `le corps d un message deja lu survit a la liste`() {
        val date = LocalDateTime.of(2026, 9, 4, 9, 42)
        val existing = listOf(MessageItem("1", "Objet", "Vie scolaire", date, body = "Bonjour"))
        val fetched = listOf(MessageItem("1", "Objet", "Vie scolaire", date))

        assertEquals("Bonjour", Merge.messages(existing, fetched).single().body)
    }

    @Test
    fun `le prochain cours est celui en cours, sinon le suivant`() {
        val lessons = listOf(lesson("1", monday, 8), lesson("2", monday, 10), lesson("3", monday, 14))

        assertEquals("1", Insights.currentOrNextLesson(lessons, monday.atTime(8, 30))!!.id)
        assertEquals("2", Insights.currentOrNextLesson(lessons, monday.atTime(9, 30))!!.id)
        assertNull(Insights.currentOrNextLesson(lessons, monday.atTime(20, 0)))
    }

    @Test
    fun `un cours annule n est jamais propose comme prochain cours`() {
        val lessons = listOf(lesson("1", monday, 8).copy(canceled = true), lesson("2", monday, 10))

        assertEquals("2", Insights.currentOrNextLesson(lessons, monday.atTime(7, 0))!!.id)
    }

    @Test
    fun `les devoirs coches localement disparaissent de la liste a faire`() {
        val settings = AppSettings().withHomeworkDone("901", true)
        val items = listOf(homework("901", monday), homework("902", monday.plusDays(1)))

        val pending = Insights.pendingHomework(items, settings, monday)

        assertEquals(listOf("902"), pending.map { it.id })
    }

    @Test
    fun `un devoir decoche localement reapparait malgre le serveur`() {
        val settings = AppSettings().withHomeworkDone("901", false)
        val items = listOf(homework("901", monday, done = true))

        assertEquals(1, Insights.pendingHomework(items, settings, monday).size)
    }

    @Test
    fun `le devoir urgent est le plus proche, evaluation en priorite`() {
        val items = listOf(
            homework("901", monday.plusDays(2)),
            homework("902", monday).copy(exam = false),
            homework("903", monday).copy(exam = true),
        )

        assertEquals("903", Insights.urgentHomework(items, AppSettings(), monday)!!.id)
    }

    @Test
    fun `la moyenne pondere les coefficients et ramene sur vingt`() {
        val grades = listOf(
            Grade("1", "Maths", "DS", 18.0, 20.0, 3.0, null, monday, "A001"),
            Grade("2", "Maths", "Interro", 5.0, 10.0, 1.0, null, monday, "A001"),
            Grade("3", "Maths", "Bonus", 20.0, 20.0, 1.0, null, monday, "A001", counted = false),
        )

        // (18×3 + 10×1) / 4 = 16 ; la note non significative est écartée.
        assertEquals(16.0, Insights.weightedAverage(grades)!!, 0.001)
        assertNull(Insights.weightedAverage(emptyList()))
    }

    @Test
    fun `la recherche ignore accents et casse`() {
        assertTrue(Insights.matches("mathematiques", "MATHÉMATIQUES", "DS n°1"))
        assertTrue(Insights.matches("", "quoi que ce soit"))
        assertFalse(Insights.matches("anglais", "Mathématiques"))
    }
}
