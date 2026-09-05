package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.model.DataSection
import fr.ecole2recte.app.core.model.Grade
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.Lesson
import fr.ecole2recte.app.core.model.MessageItem
import fr.ecole2recte.app.core.model.Period
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.repo.Insights
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.core.widget.WidgetContent
import fr.ecole2recte.app.core.widget.WidgetKind
import fr.ecole2recte.app.core.widget.WidgetShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Les quinze widgets d'écran d'accueil.
 *
 * Un widget qui plante n'affiche rien du tout, parfois définitivement jusqu'au redémarrage
 * du lanceur : ces tests vérifient surtout qu'aucun ne dépend d'une donnée présente.
 */
class WidgetContentTest {

    private val monday = LocalDate.of(2026, 9, 7)
    private val morning = monday.atTime(9, 51)

    private val snapshot = SchoolSnapshot(
        lessons = listOf(
            Lesson("1", "Philosophie", "Mme Laurent", "B204", monday.atTime(8, 0), monday.atTime(9, 0)),
            Lesson("2", "Mathématiques", "M. Petit", "C011", monday.atTime(10, 15), monday.atTime(12, 0)),
            Lesson("3", "Anglais", room = "A112", start = monday.plusDays(1).atTime(8, 0), end = monday.plusDays(1).atTime(9, 0)),
        ),
        homework = listOf(
            HomeworkItem("901", "Philosophie", monday, content = "Dissertation sur la liberté"),
            HomeworkItem("902", "Mathématiques", monday.plusDays(1), exam = true),
        ),
        grades = listOf(
            Grade("5001", "Mathématiques", "DS n°1", 17.5, 20.0, 2.0, 13.2, monday, "A001"),
            Grade("5002", "Philosophie", "Interro", 12.0, 20.0, 1.0, null, monday.minusDays(3), "A001"),
        ),
        periods = listOf(
            Period("A001", "Trimestre 1", average = 15.8, start = monday.minusDays(7), end = monday.plusDays(60)),
        ),
        messages = listOf(
            MessageItem("3001", "Réunion", "Mme Legrand", monday.atTime(9, 42)),
        ),
        syncedAt = mapOf(DataSection.TIMETABLE to morning.minusMinutes(4).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()),
    )

    @Test
    fun `chaque widget produit un contenu, meme sans aucune donnee`() {
        WidgetKind.entries.forEach { kind ->
            val full = WidgetContent.payload(kind, snapshot, AppSettings(), morning)
            val empty = WidgetContent.payload(kind, SchoolSnapshot(), AppSettings(), morning)

            assertTrue("${kind.name} : étiquette vide", full.label.isNotBlank())
            assertTrue("${kind.name} : titre vide", full.title.isNotBlank())
            assertTrue("${kind.name} : titre vide sans données", empty.title.isNotBlank())
            assertTrue("${kind.name} : devrait être signalé vide", empty.empty || empty.lines.isEmpty())
        }
    }

    @Test
    fun `les quinze widgets couvrent les quatre gabarits`() {
        assertEquals(15, WidgetKind.entries.size)
        assertEquals(6, WidgetKind.entries.count { it.shape == WidgetShape.TILE })
        assertEquals(4, WidgetKind.entries.count { it.shape == WidgetShape.ROW })
        assertEquals(4, WidgetKind.entries.count { it.shape == WidgetShape.LIST })
        assertEquals(1, WidgetKind.entries.count { it.shape == WidgetShape.WEEK })
    }

    @Test
    fun `la tuile de moyenne affiche celle de l etablissement`() {
        val payload = WidgetContent.payload(WidgetKind.AVERAGE, snapshot, AppSettings(), morning)

        assertEquals("15,8", payload.title)
        assertEquals("Trimestre 1", payload.subtitle)
        assertFalse(payload.empty)
    }

    @Test
    fun `le compte a rebours parle en minutes puis en heures`() {
        val payload = WidgetContent.payload(WidgetKind.COUNTDOWN, snapshot, AppSettings(), morning)

        assertEquals("24 min", payload.title)
        assertEquals("Mathématiques", payload.subtitle)
        assertNotNull("la couleur de la matière doit suivre", payload.accent)
    }

    @Test
    fun `le prochain cours donne salle, professeur et heure`() {
        val payload = WidgetContent.payload(WidgetKind.NEXT_LESSON, snapshot, AppSettings(), morning)

        assertEquals("Mathématiques", payload.title)
        assertEquals("10:15", payload.trailing)
        assertTrue(payload.subtitle.contains("C011"))
        assertTrue(payload.subtitle.contains("M. Petit"))
    }

    @Test
    fun `la journee ne montre que les cours restants`() {
        val payload = WidgetContent.payload(WidgetKind.TODAY, snapshot, AppSettings(), morning)

        assertEquals(1, payload.lines.size)
        assertEquals("Mathématiques", payload.lines.single().text)
        assertEquals("10:15", payload.lines.single().trailing)
    }

    @Test
    fun `la liste de devoirs suit l etat local`() {
        val settings = AppSettings().withHomeworkDone("901", true)

        val payload = WidgetContent.payload(WidgetKind.HOMEWORK_LIST, snapshot, settings, morning)

        assertEquals("1 devoir", payload.title)
        assertEquals("Mathématiques", payload.lines.single().text)
        assertEquals("évaluation", payload.lines.single().secondary)
    }

    @Test
    fun `la semaine compte les cours de chaque jour`() {
        val payload = WidgetContent.payload(WidgetKind.WEEK, snapshot, AppSettings(), morning)

        assertEquals(6, payload.days.size)
        assertEquals("2", payload.days.first().value)
        assertTrue(payload.days.first().today)
        assertEquals("1", payload.days[1].value)
        assertTrue(payload.days[2].empty)
    }

    @Test
    fun `la tuile de synchronisation dit l age des donnees`() {
        val payload = WidgetContent.payload(WidgetKind.SYNC, snapshot, AppSettings(), morning)

        assertEquals("4 min", payload.title)
        assertEquals("—", WidgetContent.payload(WidgetKind.SYNC, SchoolSnapshot(), AppSettings(), morning).title)
    }

    @Test
    fun `les journees et les croisements devoirs-cours sont justes`() {
        val summary = Insights.daySummary(snapshot.lessons, monday)
        assertEquals(2, summary.lessons)
        assertEquals(165L, summary.minutes)
        assertEquals(monday.atTime(8, 0), summary.start)
        assertEquals(monday.atTime(12, 0), summary.end)

        val gaps = Insights.gaps(Insights.lessonsOn(snapshot.lessons, monday))
        assertEquals(1, gaps.size)
        assertEquals(75L, gaps.single().second)

        val philosophy = snapshot.lessons.first { it.subject == "Philosophie" }
        assertEquals(listOf("901"), Insights.homeworkForLesson(snapshot.homework, philosophy).map { it.id })
        assertEquals(listOf("902"), Insights.upcomingExams(snapshot.homework, AppSettings(), monday).map { it.id })
    }

    @Test
    fun `la moyenne calculee est signalee comme telle`() {
        val withoutOfficial = snapshot.copy(periods = listOf(Period("A001", "Trimestre 1")))

        val official = Insights.generalAverage(snapshot, monday)!!
        val computed = Insights.generalAverage(withoutOfficial, monday)!!

        assertTrue(official.official)
        assertEquals(15.8, official.value, 0.001)
        assertFalse(computed.official)
        // (17,5 × 2 + 12 × 1) / 3
        assertEquals(15.666, computed.value, 0.01)
    }
}
