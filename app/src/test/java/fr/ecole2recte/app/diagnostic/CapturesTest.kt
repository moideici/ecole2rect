package fr.ecole2recte.app.diagnostic

import fr.ecole2recte.app.data.api.ParseReport
import fr.ecole2recte.app.data.api.Parsers
import fr.ecole2recte.app.data.cache.CacheOwner
import fr.ecole2recte.app.data.cache.InMemoryResponseCache
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Les lecteurs sur des formes documentées de l'API, chargées depuis `src/test/resources/captures`. */
class CapturesTest {
    private fun capture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource("captures/$name")) { "fichier manquant : $name" }.readText()

    private fun data(name: String): Any? = JSONTokener(capture(name)).nextValue()

    @Test
    fun `l'emploi du temps documente est lu, les permanences reconnues et l'entree sans heure comptee`() {
        val report = ParseReport()
        val lessons = Parsers.timetable(data("timetable_documented.json"), report)
        assertEquals(5, lessons.size)
        assertEquals(1, report.rejected)
        assertEquals("PERMANENCE", lessons.first { it.id == "104" }.kind)
        assertEquals(true, lessons.first { it.id == "103" }.modified)
        assertEquals(true, lessons.first { it.id == "105" }.canceled)
        assertEquals("2026-11-05T08:00:00", lessons.first().start)
    }

    @Test
    fun `les notes documentees avec moyenne dans ensembleMatieres et valeurs textuelles gardees`() {
        val grades = Parsers.grades(data("grades_documented.json"))
        assertEquals(2, grades.periods.size)
        assertEquals(14.52, grades.periods.first { it.code == "A001" }.average!!, 0.0001)
        assertTrue(grades.periods.first { it.code == "A000" }.annual)
        assertEquals(2, grades.subjectAverages.size)
        assertEquals("Très bon travail, des progrès constants.", grades.subjectAverages.first().appreciation)
        assertEquals(5.0, grades.subjectAverages.first().minimum!!, 0.0001)
        assertEquals(19.0, grades.subjectAverages.first().maximum!!, 0.0001)
        val abs = grades.grades.first { it.id == "2" }
        assertEquals(null, abs.value)
        assertEquals("Abs", abs.rawValue)
        assertEquals("Disp", grades.grades.first { it.id == "3" }.rawValue)
        assertEquals(8.5, grades.grades.first { it.id == "4" }.value!!, 0.0001)
        assertEquals("Bon trimestre, continue ainsi.", grades.periods.first { it.code == "A001" }.appreciation)
    }

    @Test
    fun `le cahier de textes documente`() {
        val items = Parsers.homeworkSummary(data("homework_summary.json"))
        assertEquals(listOf("5501", "5502", "5503"), items.map { it.id })
        assertTrue(items.first { it.id == "5502" }.done)
        assertTrue(items.first { it.id == "5501" }.exam)
    }

    @Test
    fun `la vie scolaire documentee compte l'entree sans date`() {
        val report = ParseReport()
        val events = Parsers.schoolLife(data("schoollife.json"), report)
        assertEquals(3, events.size)
        assertEquals(1, report.rejected)
        assertEquals("encouragement", events.first().kind)
    }

    @Test
    fun `la liste des messages documentee`() {
        val messages = Parsers.messages(data("messages_list.json"))
        assertEquals(listOf(false, true), messages.map { it.read })
        assertEquals("9001", messages.first().id)
    }

    @Test
    fun `la forme decrite ne contient aucune valeur`() {
        val shape = describeShape(data("timetable_documented.json"))
        listOf("MATHEMATIQUES", "PETIT", "C011", "2026-11-05", "2NDE", "#7779E8", "HISTOIRE", "ANGLAIS").forEach {
            assertFalse("la forme ne doit pas contenir « $it »", shape.contains(it))
        }
        assertTrue(shape.contains("start_date: <texte:16>"))
        assertTrue(shape.contains("[6 élément(s)]"))
    }

    @Test
    fun `les cles qui sont des dates ou des identifiants sont masquees`() {
        val shape = describeShape(data("homework_summary.json"))
        assertFalse(shape.contains("2026-11-06"))
        assertTrue(shape.contains("<date> (×2)"))
        val ids = describeShape(JSONTokener("""{"12345":{"nom":"Dupont"}}""").nextValue())
        assertTrue(ids.contains("<id>"))
        assertFalse(ids.contains("12345"))
        assertFalse(ids.contains("Dupont"))
    }

    @Test
    fun `le diagnostic relit le cache, compte les entrees ecartees et ne montre aucune valeur`() {
        val cache = InMemoryResponseCache()
        val owner = CacheOwner("eleve", null)
        cache.put(owner, "lessons", capture("timetable_documented.json"), 1_000_000L)
        cache.put(owner, "grades", capture("grades_documented.json"), 1_000_000L)
        val clock = Clock.fixed(Instant.ofEpochMilli(1_000_000L + 5 * 60_000), ZoneOffset.UTC)
        val sections = DataDiagnostic(cache, clock).sections(owner)
        assertEquals(listOf("lessons", "grades"), sections.map { it.name })
        assertEquals("il y a 5 min", sections.first().ageLabel)
        assertEquals(1, sections.first().rejected)
        assertFalse(sections.joinToString { it.shape }.contains("MATHEMATIQUES"))
    }
}
