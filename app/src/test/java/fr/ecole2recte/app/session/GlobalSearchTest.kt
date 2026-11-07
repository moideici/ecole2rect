package fr.ecole2recte.app.session

import fr.ecole2recte.app.domain.GlobalSearch
import fr.ecole2recte.app.domain.GradeEntry
import fr.ecole2recte.app.domain.Homework
import fr.ecole2recte.app.domain.MessagePreview
import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.domain.SearchCorpus
import fr.ecole2recte.app.domain.SearchKind
import fr.ecole2recte.app.domain.Subject
import fr.ecole2recte.app.domain.TextSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GlobalSearchTest {
    private val today = LocalDate.of(2026, 11, 5)

    private fun grade(id: String, subject: String, label: String, value: Double? = 14.0) =
        GradeEntry(id, subject, 0, label, value, 20.0, 1.0, "2026-11-03", null, null, null, true)

    private fun lesson(date: String, start: String, name: String, teacher: String, room: String = "B05", canceled: Boolean = false) =
        ScheduleEntry(Subject(name, teacher, room, 0), "Jeudi · $start", canceled, date, start, "10:00")

    private val corpus = SearchCorpus(
        homework = listOf(
            Homework("Rédiger l'éloge de l'été", "Français", "Vendredi 6 novembre", 0, "h1", "2026-11-06", content = "Lire le chapitre 3 du roman"),
            Homework("Exercices 12", "Mathématiques", "Lundi 9 novembre", 0, "h2", "2026-11-09", done = true),
        ),
        messages = listOf(
            MessagePreview("Mme Éléonore Dupont", "Sortie à la bibliothèque", true, "m1", "10:15"),
            MessagePreview("Vie scolaire", "Réunion parents", false, "m2", "4 nov."),
        ),
        grades = listOf(
            grade("g1", "Mathématiques", "Contrôle fractions"),
            grade("g2", "Anglais", "Oral", value = null),
        ),
        lessons = listOf(
            lesson("2026-11-05", "08:00", "Mathématiques", "M. Petit"),
            lesson("2026-11-12", "08:00", "Mathématiques", "M. Petit"),
            lesson("2026-11-06", "09:00", "Anglais", "Mme Robert"),
        ),
    )

    private fun kinds(query: String) = GlobalSearch.search(corpus, query, today).map { it.kind }

    @Test
    fun `la recherche ignore la casse et les accents dans les deux sens`() {
        assertEquals(listOf(SearchKind.Message), kinds("ELEONORE"))
        assertEquals(listOf(SearchKind.Message), kinds("éléonore"))
        assertEquals(listOf(SearchKind.Homework), kinds("eloge ete"))
        assertTrue(TextSearch.matches(TextSearch.tokens("ÉTÉ"), listOf("été")))
        assertEquals("oeuvre l'ete", TextSearch.normalize("  Œuvre   l’été "))
    }

    @Test
    fun `tous les mots doivent se retrouver, dans n'importe quel ordre`() {
        assertEquals(listOf(SearchKind.Homework), kinds("roman chapitre"))
        assertEquals(emptyList<SearchKind>(), kinds("roman inconnu"))
    }

    @Test
    fun `une recherche vide ne montre rien`() {
        assertEquals(emptyList<SearchKind>(), kinds(""))
        assertEquals(emptyList<SearchKind>(), kinds("   "))
    }

    @Test
    fun `les resultats sont groupes dans l'ordre des onglets`() {
        val groups = GlobalSearch.search(corpus, "math", today)
        assertEquals(listOf(SearchKind.Homework, SearchKind.Grade, SearchKind.Lesson), groups.map { it.kind })
    }

    @Test
    fun `une note se cherche par matiere ou par libelle`() {
        assertEquals("Mathématiques", GlobalSearch.search(corpus, "fractions", today).single().hits.single().title)
        assertEquals(1, GlobalSearch.search(corpus, "oral", today).single().total)
    }

    @Test
    fun `un cours qui revient chaque semaine n'est montre qu'une fois, avec son prochain creneau`() {
        val group = GlobalSearch.search(corpus, "petit", today).single()
        assertEquals(SearchKind.Lesson, group.kind)
        assertEquals(1, group.hits.size)
        assertEquals("Mathématiques · M. Petit", group.hits.single().title)
    }

    @Test
    fun `le prochain creneau d'un cours est ignore s'il est annule`() {
        val data = SearchCorpus(
            lessons = listOf(
                lesson("2026-11-06", "08:00", "Physique", "M. Roux", canceled = true),
                lesson("2026-11-13", "08:00", "Physique", "M. Roux"),
            ),
        )
        val hit = GlobalSearch.search(data, "physique", today).single().hits.single()
        assertTrue(hit.subtitle.contains("08:00"))
        assertFalse(hit.subtitle.contains("annulé"))
    }

    @Test
    fun `le nombre de resultats montres est limite mais le total est compte`() {
        val many = SearchCorpus(messages = (1..12).map { MessagePreview("Vie scolaire", "Info $it", true, "m$it") })
        val group = GlobalSearch.search(many, "info", today, perKind = 5).single()
        assertEquals(5, group.hits.size)
        assertEquals(12, group.total)
    }

    @Test
    fun `un mot qui commence un champ passe avant un mot au milieu`() {
        val data = SearchCorpus(
            messages = listOf(
                MessagePreview("Secrétariat", "Rappel du carnet", true, "m1"),
                MessagePreview("M. Carnot", "Bonjour", true, "m2"),
            ),
        )
        val hits = GlobalSearch.search(data, "carn", today).single().hits
        assertEquals(2, hits.size)
        // Les deux commencent un mot : l'ordre d'origine est conservé.
        assertEquals(listOf("Secrétariat", "M. Carnot"), hits.map { it.title })
    }
}
