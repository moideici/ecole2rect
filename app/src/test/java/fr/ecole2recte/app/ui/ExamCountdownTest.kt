package fr.ecole2recte.app.ui

import fr.ecole2recte.app.domain.ExamCountdown
import fr.ecole2recte.app.domain.Homework
import fr.ecole2recte.app.settings.HomeLayout
import fr.ecole2recte.app.settings.HomeSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExamCountdownTest {
    private val today = LocalDate.of(2026, 11, 5)

    private fun task(subject: String, date: String, exam: Boolean = true, content: String = "", done: Boolean = false) =
        Homework("t", subject, "", 0, "$subject-$date", date, done, exam, content)

    @Test
    fun `seuls les devoirs marques evaluation comptent, du plus proche au plus lointain`() {
        val list = ExamCountdown.upcoming(
            listOf(task("Anglais", "2026-11-20"), task("Maths", "2026-11-08"), task("SVT", "2026-11-06", exam = false), task("Histoire", "2026-11-12")),
            today,
        )
        assertEquals(listOf("Maths", "Histoire", "Anglais"), list.map { it.subject })
    }

    @Test
    fun `un controle passe ou trop lointain est ecarte, celui d'aujourd'hui est garde`() {
        val list = ExamCountdown.upcoming(
            listOf(task("Maths", "2026-11-04"), task("Physique", "2026-11-05"), task("Latin", "2027-03-01"), task("Grec", "pas une date")),
            today,
        )
        assertEquals(listOf("Physique"), list.map { it.subject })
    }

    @Test
    fun `deux annonces du meme controle ne comptent qu'une fois et la liste est limitee`() {
        val doubles = ExamCountdown.upcoming(listOf(task("Maths", "2026-11-08"), task("MATHS", "2026-11-08")), today)
        assertEquals(1, doubles.size)
        val many = ExamCountdown.upcoming((6..12).map { task("M$it", "2026-11-%02d".format(it)) }, today, limit = 3)
        assertEquals(3, many.size)
    }

    @Test
    fun `un controle deja coche fait reste annonce tant que sa date n'est pas passee`() {
        assertEquals(1, ExamCountdown.upcoming(listOf(task("Maths", "2026-11-08", done = true)), today).size)
    }

    @Test
    fun `le texte dit aujourd'hui, demain ou dans N jours`() {
        val exam = ExamCountdown.upcoming(listOf(task("Maths", "2026-11-08")), today).single()
        assertEquals("Contrôle de Maths dans 3 jours", ExamCountdown.label(exam, today))
        assertEquals("Contrôle de Maths demain", ExamCountdown.label(exam, LocalDate.of(2026, 11, 7)))
        assertEquals("Contrôle de Maths aujourd'hui", ExamCountdown.label(exam, LocalDate.of(2026, 11, 8)))
        assertEquals(3, ExamCountdown.daysUntil(exam, today))
    }

    @Test
    fun `le detail est la premiere ligne non vide de l'enonce`() {
        val exam = ExamCountdown.upcoming(listOf(task("Maths", "2026-11-08", content = "\n  Chapitre 4 : dérivées\nExercices")), today).single()
        assertEquals("Chapitre 4 : dérivées", exam.detail)
    }

    @Test
    fun `la section Prochain controle se deplace et se masque comme les autres`() {
        assertTrue(HomeSection.Exams in HomeLayout().visible)
        val moved = HomeLayout().move(HomeSection.Exams, -1)
        assertEquals(HomeSection.Exams, moved.order[HomeSection.entries.indexOf(HomeSection.Exams) - 1])
        val hidden = HomeLayout().toggle(HomeSection.Exams)
        assertTrue(HomeSection.Exams !in hidden.visible)
        assertEquals(hidden, HomeLayout.parse(hidden.serialize()))
        // Un réglage enregistré avant l'ajout de la section la retrouve, visible, à la fin.
        val old = HomeLayout.parse("Family,NextClass,Today,Highlights,Reminder,Grades;")
        assertEquals(HomeSection.Exams, old.order.last())
        assertTrue(HomeSection.Exams in old.visible)
    }
}
