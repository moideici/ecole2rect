package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.model.Grade
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.Lesson
import fr.ecole2recte.app.core.model.MessageItem
import fr.ecole2recte.app.core.model.Period
import fr.ecole2recte.app.core.model.SubjectAverage
import fr.ecole2recte.app.core.model.SchoolSnapshot
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

    // ── Objectif et impact ───────────────────────────────────────────────────

    private val periodGrades = listOf(
        Grade("1", "Maths", "DS", 16.0, 20.0, 4.0, null, monday, "A001"),
        Grade("2", "Histoire-géo", "Composition", 10.0, 20.0, 3.0, null, monday, "A001"),
        Grade("3", "Anglais", "Oral", 14.0, 20.0, 2.0, null, monday, "A001"),
    )

    private val periodSnapshot = SchoolSnapshot(grades = periodGrades)

    @Test
    fun `sans objectif fixe, il n y en a pas`() {
        // L'application n'invente pas un objectif à la place de l'élève.
        assertNull(Insights.objective(periodSnapshot, monday, target = null))
    }

    @Test
    fun `l objectif dit l ecart et la note qui suffirait`() {
        val objective = Insights.objective(periodSnapshot, monday, target = 15.0)!!

        // (16×4 + 10×3 + 14×2) / 9 = 13,56
        assertEquals(13.56, objective.current, 0.01)
        assertEquals(1.44, objective.gap, 0.01)

        // Le levier est la matière où la note demandée est la plus basse : le coefficient
        // décide, pas la sympathie pour la matière.
        val lever = objective.lever!!
        assertEquals("Maths", lever.subject)
        assertEquals(4.0, lever.coefficient, 0.01)
        // (15 × 13 − 122) / 4 = 18,25
        assertEquals(18.25, lever.mark, 0.01)
    }

    @Test
    fun `un objectif atteint le dit, sans levier`() {
        val objective = Insights.objective(periodSnapshot, monday, target = 12.0)!!

        assertTrue(objective.reached)
        assertEquals(0.0, objective.gap, 0.001)
    }

    @Test
    fun `un objectif hors d atteinte ne propose pas un vingt-trois sur vingt`() {
        val objective = Insights.objective(periodSnapshot, monday, target = 18.0)!!

        // Aucune note seule ne suffirait : l'écran le dit au lieu d'afficher un chiffre
        // impossible, qui ferait passer la fonctionnalité pour cassée.
        assertFalse(objective.reached)
        assertNull(objective.lever)
    }

    @Test
    fun `l impact d une note est ce qu elle a vraiment fait a la moyenne`() {
        val impacts = Insights.impacts(periodGrades)

        // Sans le 10 en histoire-géo : (16×4 + 14×2) / 6 = 15,33, soit 1,78 de plus.
        assertEquals(-1.78, impacts.getValue("2"), 0.01)
        // Le 16 en maths, lui, tire la moyenne vers le haut.
        assertTrue(impacts.getValue("1") > 0)
        // La somme des trois ne vaut rien de particulier, mais aucun impact n'est nul ici.
        assertEquals(3, impacts.size)
    }

    @Test
    fun `une note seule n a pas d impact mesurable`() {
        // « Sans elle » n'existe pas : la question n'a pas de réponse, et inventer zéro
        // laisserait croire qu'elle ne compte pas.
        assertEquals(emptyMap<String, Double>(), Insights.impacts(periodGrades.take(1)))
    }

    // ── L'année en chiffres ──────────────────────────────────────────────────

    @Test
    fun `le bilan compte l annee, mais les cours de la semaine seulement`() {
        val snapshot = SchoolSnapshot(
            lessons = listOf(
                lesson("1", monday, 8),
                lesson("2", monday, 10),
                // La semaine suivante est en cache depuis qu'on l'a ouverte : elle ne doit
                // pas gonfler un total présenté comme celui de la semaine en cours.
                lesson("3", monday.plusWeeks(1), 8),
            ),
            homework = listOf(
                homework("901", monday.plusDays(1)),
                homework("902", monday.plusDays(2), done = true),
            ),
            grades = listOf(
                Grade("1", "Maths", "DS", 16.0, 20.0, 2.0, null, monday, "A001"),
                Grade("2", "Histoire-géo", "Compo", 10.0, 20.0, 2.0, null, monday, "A001"),
            ),
        )

        val stats = Insights.yearStats(snapshot, AppSettings(), monday)

        assertEquals(2, stats.gradeCount)
        assertEquals("Maths", stats.bestSubject?.first)
        assertEquals("Histoire-géo", stats.weakestSubject?.first)
        assertEquals(1, stats.homeworkDone)
        assertEquals(2, stats.homeworkTotal)
        // Deux cours cette semaine, pas trois : annoncer un total depuis septembre à partir
        // de ce qui traîne en cache serait un chiffre inventé.
        assertEquals(2, stats.lessonsThisWeek)
        assertEquals(120L, stats.minutesThisWeek)
        assertEquals(monday, stats.busiestDay?.first)
    }

    @Test
    fun `un bilan vide ne plante pas et n invente rien`() {
        val stats = Insights.yearStats(SchoolSnapshot(), AppSettings(), monday)

        assertEquals(0, stats.gradeCount)
        assertNull(stats.average)
        assertNull(stats.bestSubject)
        assertNull(stats.busiestDay)
    }

    // ── Professeurs ──────────────────────────────────────────────────────────

    @Test
    fun `un professeur se reconstitue depuis les cours et les moyennes`() {
        val snapshot = SchoolSnapshot(
            lessons = listOf(
                lesson("1", monday, 8, "Mathématiques").copy(teacher = "M. Petit", room = "C011"),
                lesson("2", monday, 10, "Mathématiques").copy(teacher = "M. Petit", room = "801"),
                lesson("3", monday, 14, "Anglais").copy(teacher = "Mme Cresson"),
                // La semaine suivante ne compte pas dans « cette semaine ».
                lesson("4", monday.plusWeeks(1), 8, "Mathématiques").copy(teacher = "M. Petit"),
            ),
            subjectAverages = listOf(
                SubjectAverage("A001", "Spécialité maths", 15.0, teachers = listOf("M. Petit")),
            ),
            periods = listOf(Period("A001", "Trimestre 1", headTeacher = "M. Petit")),
        )

        val profile = Insights.teacherProfile(snapshot, monday, "M. Petit")

        assertEquals(listOf("Mathématiques", "Spécialité maths"), profile.subjects)
        assertEquals(listOf("801", "C011"), profile.rooms)
        // Deux cours cette semaine, pas trois : additionner des semaines chargées au hasard
        // donnerait un « par semaine » qui n'en est pas un.
        assertEquals(2, profile.lessonsThisWeek)
        assertEquals(120L, profile.minutesThisWeek)
        assertTrue(profile.headTeacher)
    }

    @Test
    fun `la liste des professeurs ne garde chaque nom qu une fois`() {
        val snapshot = SchoolSnapshot(
            lessons = listOf(
                lesson("1", monday, 8).copy(teacher = "M. Petit"),
                lesson("2", monday, 10).copy(teacher = "M. Petit"),
                lesson("3", monday, 14).copy(teacher = ""),
            ),
            subjectAverages = listOf(
                SubjectAverage("A001", "Anglais", 14.0, teacher = "Mme Cresson"),
            ),
        )

        // Un nom vide n'est pas un professeur, et le même nom n'apparaît pas deux fois.
        assertEquals(listOf("M. Petit", "Mme Cresson"), Insights.teachers(snapshot, monday).map { it.name })
    }

    @Test
    fun `la recherche ignore accents et casse`() {
        assertTrue(Insights.matches("mathematiques", "MATHÉMATIQUES", "DS n°1"))
        assertTrue(Insights.matches("", "quoi que ce soit"))
        assertFalse(Insights.matches("anglais", "Mathématiques"))
    }

    @Test
    fun `une conversation rangee a la corbeille cesse de compter comme non lue`() {
        val date = LocalDateTime.of(2026, 9, 7, 9, 0)
        val messages = listOf(
            MessageItem("1", "Objet", "Vie scolaire", date),
            MessageItem("2", "Autre", "M. Gay-Vidal", date),
        )
        val settings = AppSettings().withMessageBinned("2", binned = true)

        // Ranger, c'est dire qu'on ne veut plus la voir compter : ni sur l'onglet, ni dans
        // les widgets, ni dans les notifications, qui passent tous par ici.
        assertEquals(listOf("1"), Insights.unreadMessages(messages, settings).map { it.id })
        assertEquals(2, Insights.unreadMessages(messages, AppSettings()).size)
    }
}
