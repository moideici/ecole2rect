package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.diag.ErrorLog
import fr.ecole2recte.app.core.export.Ics
import fr.ecole2recte.app.core.model.Grade
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.Lesson
import fr.ecole2recte.app.core.model.MessageItem
import fr.ecole2recte.app.core.model.Period
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.repo.Insights
import fr.ecole2recte.app.core.repo.Search
import fr.ecole2recte.app.core.repo.Simulator
import fr.ecole2recte.app.core.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Simulateur, historique, recherche globale, export ICS et journal d'erreurs. */
class ToolsTest {

    private val monday = LocalDate.of(2026, 9, 7)

    private val snapshot = SchoolSnapshot(
        lessons = listOf(
            Lesson("1", "Mathématiques", "M. Petit", "C011", monday.atTime(10, 15), monday.atTime(12, 0)),
            Lesson("2", "Philosophie", room = "B204", start = monday.atTime(8, 0), end = monday.atTime(9, 0), canceled = true),
        ),
        homework = listOf(HomeworkItem("901", "Mathématiques", monday.plusDays(1), content = "Exercices 12 à 18")),
        grades = listOf(
            Grade("1", "Mathématiques", "DS n°1", 16.0, 20.0, 2.0, null, monday.minusDays(10), "A001"),
            Grade("2", "Mathématiques", "Interro", 10.0, 20.0, 1.0, null, monday.minusDays(3), "A001"),
            Grade("3", "Philosophie", "Dissertation", 12.0, 20.0, 1.0, null, monday.minusDays(1), "A001"),
        ),
        periods = listOf(Period("A001", "Trimestre 1", start = monday.minusDays(30), end = monday.plusDays(60))),
        messages = listOf(MessageItem("3001", "Réunion parents", "Mme Legrand", monday.atTime(9, 0))),
    )

    @Test
    fun `le simulateur pondere la note hypothetique`() {
        val outcome = Simulator.simulate(
            snapshot,
            Simulator.Hypothesis("Mathématiques", value = 20.0, coefficient = 1.0),
            monday,
        )!!

        // Maths avant : (16×2 + 10×1) / 3 = 14 ; après : (16×2 + 10 + 20) / 4 = 15,5
        assertEquals(14.0, outcome.subjectBefore!!, 0.001)
        assertEquals(15.5, outcome.subjectAfter!!, 0.001)
        assertTrue(outcome.generalDelta!! > 0)
    }

    @Test
    fun `une hypothese impossible est refusee plutot qu arrondie`() {
        assertNull(Simulator.simulate(snapshot, Simulator.Hypothesis("Mathématiques", 25.0), monday))
        assertNull(Simulator.simulate(snapshot, Simulator.Hypothesis("Mathématiques", 12.0, coefficient = 0.0), monday))
    }

    @Test
    fun `la note necessaire pour un objectif est calculee, ou declaree hors d atteinte`() {
        // Maths totalise 42 points pour un poids de 3 ; viser 15 avec un coefficient 1
        // demande 15 × 4 − 42 = 18/20.
        val needed = Simulator.requiredGrade(snapshot, "Mathématiques", target = 15.0, coefficient = 1.0, scale = 20.0, today = monday)
        assertEquals(18.0, needed!!, 0.001)

        // Viser 19 de moyenne avec un seul devoir de coefficient 1 est hors d'atteinte.
        assertNull(Simulator.requiredGrade(snapshot, "Mathématiques", 19.0, 1.0, 20.0, monday))
    }

    @Test
    fun `l historique suit la moyenne note apres note`() {
        val history = Insights.averageHistory(Insights.gradesForPeriod(snapshot.grades, "A001"))

        assertEquals(3, history.size)
        assertEquals(16.0, history.first().second, 0.001)
        assertEquals(14.0, history[1].second, 0.001)
        assertEquals(13.5, history.last().second, 0.001)
    }

    @Test
    fun `la recherche globale traverse toutes les sections`() {
        val hits = Search.query(snapshot, AppSettings(), "math", monday)

        assertTrue(hits.any { it.kind == Search.HitKind.LESSON })
        assertTrue(hits.any { it.kind == Search.HitKind.HOMEWORK })
        assertTrue(hits.any { it.kind == Search.HitKind.GRADE })
        assertTrue(hits.none { it.kind == Search.HitKind.MESSAGE })

        // Une requête d'une seule lettre ramènerait tout : elle ne cherche rien.
        assertTrue(Search.query(snapshot, AppSettings(), "m", monday).isEmpty())
        assertTrue(Search.query(snapshot, AppSettings(), "legrand", monday).any { it.kind == Search.HitKind.MESSAGE })
    }

    @Test
    fun `l export ICS produit un calendrier valide`() {
        val ics = Ics.export(snapshot.lessons, zone = ZoneId.of("Europe/Paris"))

        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.trimEnd().endsWith("END:VCALENDAR"))
        assertEquals(2, Regex("BEGIN:VEVENT").findAll(ics).count())
        assertTrue(ics.contains("DTSTART:20260907T101500"))
        assertTrue(ics.contains("SUMMARY:Philosophie (annulé)"))
        assertTrue("un cours annulé reste dans l'agenda, barré", ics.contains("STATUS:CANCELLED"))
        assertTrue(ics.contains("LOCATION:C011"))
    }

    @Test
    fun `une panne passagere est reessayee, un refus ne l est pas`() {
        var calls = 0
        val waits = mutableListOf<Long>()
        val flaky = object : fr.ecole2recte.app.core.protocol.HttpEngine {
            override fun execute(
                request: fr.ecole2recte.app.core.protocol.HttpRequest,
            ): fr.ecole2recte.app.core.protocol.HttpResponse {
                calls++
                if (calls < 3) throw fr.ecole2recte.app.core.protocol.NetworkFailure("tunnel")
                return fr.ecole2recte.app.core.protocol.HttpResponse(200, emptyMap(), "ok")
            }
        }
        val engine = fr.ecole2recte.app.core.protocol.RetryingHttpEngine(
            delegate = flaky,
            attempts = 3,
            sleep = { waits += it },
        )

        val response = engine.execute(
            fr.ecole2recte.app.core.protocol.HttpRequest("https://exemple/x", "GET"),
        )

        assertEquals("ok", response.body)
        assertEquals(3, calls)
        assertEquals(2, waits.size)
        assertTrue("l'attente doit croître", waits[1] > waits[0])
    }

    @Test
    fun `un reseau definitivement absent finit par echouer`() {
        val dead = object : fr.ecole2recte.app.core.protocol.HttpEngine {
            override fun execute(
                request: fr.ecole2recte.app.core.protocol.HttpRequest,
            ): fr.ecole2recte.app.core.protocol.HttpResponse =
                throw fr.ecole2recte.app.core.protocol.NetworkFailure("coupé")
        }
        val engine = fr.ecole2recte.app.core.protocol.RetryingHttpEngine(dead, attempts = 2, sleep = {})

        var thrown = false
        try {
            engine.execute(fr.ecole2recte.app.core.protocol.HttpRequest("https://exemple/x", "GET"))
        } catch (error: fr.ecole2recte.app.core.protocol.NetworkFailure) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun `le journal d erreurs expurge et reste borne`() {
        val log = ErrorLog(capacity = 3)

        log.record("Notes", "motdepasse=secret refusé", detail = "token=abcdef")
        repeat(5) { index -> log.record("Devoirs", "erreur $index") }

        assertEquals(3, log.entries.value.size)
        assertTrue(log.entries.value.last().message.contains("erreur 4"))
        val first = ErrorLog().also { it.record("Notes", "motdepasse=secret") }.entries.value.single()
        assertFalse(first.message.contains("secret"))
    }
}
