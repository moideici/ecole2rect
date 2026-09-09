package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.Lesson
import fr.ecole2recte.app.core.repo.Insights
import fr.ecole2recte.app.core.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Le plan de travail dans les trous de la journée.
 *
 * Un emploi du temps montre les blancs sans dire ce qu'on peut y mettre. La règle qui compte
 * ici n'est pas de remplir les trous — c'est de ne pas mettre le **même** devoir dans les
 * trois, ce qui ne serait pas un plan mais un rappel répété, et de laisser le déjeuner
 * tranquille.
 */
class WorkPlanTest {

    private val monday: LocalDate = LocalDate.of(2026, 9, 7)

    private fun lesson(id: String, from: Pair<Int, Int>, to: Pair<Int, Int>) = Lesson(
        id = id,
        subject = "Cours $id",
        start = monday.atTime(from.first, from.second),
        end = monday.atTime(to.first, to.second),
    )

    private fun homework(id: String, subject: String, due: LocalDate, exam: Boolean = false) =
        HomeworkItem(id = id, subject = subject, dueDate = due, exam = exam)

    /** Une permanence le matin, le déjeuner, un trou l'après-midi. */
    private val day = listOf(
        lesson("1", 8 to 5, 9 to 0),
        lesson("2", 10 to 10, 11 to 55),
        lesson("3", 13 to 5, 14 to 45),
        lesson("4", 16 to 0, 16 to 55),
    )

    private val homework = listOf(
        homework("901", "Mathématiques", monday.plusDays(1)),
        homework("902", "Anglais", monday.plusDays(2)),
        homework("903", "Histoire-géo", monday.plusDays(3), exam = true),
    )

    private fun plan(items: List<HomeworkItem> = homework, settings: AppSettings = AppSettings()) =
        Insights.workPlan(day, items, settings, monday)

    @Test
    fun `chaque trou utilisable recoit un devoir different`() {
        val slots = plan()

        // Trois trous : 09:00–10:10, le déjeuner 11:55–13:05, et 14:45–16:00.
        assertEquals(3, slots.size)
        val assigned = slots.mapNotNull { it.homework?.id }
        assertEquals("aucun devoir ne doit servir deux fois", assigned.size, assigned.distinct().size)
    }

    @Test
    fun `le dejeuner reste libre`() {
        val midday = plan().single { it.midday }

        // Déjeuner est aussi une occupation : un plan qui prend l'heure du repas ne sera
        // pas suivi, et prétendre le contraire décrédibilise le reste.
        assertNull(midday.homework)
        assertEquals(70L, midday.interval.minutes)
    }

    @Test
    fun `un long creneau va aux revisions, un court a un exercice`() {
        val slots = plan().filterNot { it.midday }

        // 09:00–10:10 fait soixante-dix minutes : c'est là qu'on révise une évaluation.
        val long = slots.first { it.interval.minutes >= 55 }
        assertEquals("903", long.homework?.id)
        // 14:45–16:00 en fait soixante-quinze, mais l'évaluation est déjà placée : le plus
        // urgent des exercices restants prend la suite.
        val next = slots.last()
        assertEquals("901", next.homework?.id)
    }

    @Test
    fun `un trou de dix minutes ne sert qu a changer de salle`() {
        val tight = listOf(
            lesson("1", 8 to 5, 9 to 0),
            lesson("2", 9 to 10, 10 to 5),
        )

        assertTrue(Insights.workPlan(tight, homework, AppSettings(), monday).isEmpty())
    }

    @Test
    fun `sans devoir a faire, un trou reste un trou`() {
        val slots = Insights.workPlan(day, emptyList(), AppSettings(), monday)

        // Le dire vaut mieux que de recycler un devoir déjà coché pour remplir la case.
        assertEquals(3, slots.size)
        assertTrue(slots.all { it.homework == null })
    }

    @Test
    fun `un devoir coche ne revient pas dans le plan`() {
        val done = AppSettings().withHomeworkDone("903", true)

        val slots = plan(settings = done)

        assertTrue(slots.none { it.homework?.id == "903" })
    }

    @Test
    fun `une journee continue n a pas de plan`() {
        val continuous = listOf(
            lesson("1", 8 to 5, 12 to 0),
            lesson("2", 12 to 0, 16 to 0),
        )

        assertTrue(Insights.workPlan(continuous, homework, AppSettings(), monday).isEmpty())
    }
}
