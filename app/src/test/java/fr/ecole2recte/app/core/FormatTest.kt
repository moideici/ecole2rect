package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.format.Text
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class FormatTest {

    private val monday = LocalDate.of(2026, 9, 7)

    @Test
    fun `les dates s ecrivent en francais sans dependre de la locale`() {
        assertEquals("lundi 7 septembre 2026", Dates.fullDate(monday))
        assertEquals("lun. 7", Dates.compactDay(monday))
        assertEquals("jeudi 1er octobre", Dates.dayAndMonth(LocalDate.of(2026, 10, 1)))
        assertEquals(monday, Dates.startOfWeek(LocalDate.of(2026, 9, 12)))
    }

    @Test
    fun `la position d un cours se lit d un coup d oeil`() {
        val start = LocalDateTime.of(2026, 9, 7, 10, 15)
        val end = LocalDateTime.of(2026, 9, 7, 12, 0)

        assertEquals("dans 24 min", Dates.relativeToNow(start, end, start.minusMinutes(24)))
        assertEquals("en cours", Dates.relativeToNow(start, end, start.plusMinutes(30)))
        assertEquals("terminé", Dates.relativeToNow(start, end, end.plusMinutes(1)))
        assertEquals(0.5f, Dates.progress(start, end, LocalDateTime.of(2026, 9, 7, 11, 7)), 0.02f)
    }

    @Test
    fun `une echeance dit clairement si elle est depassee`() {
        assertEquals("pour aujourd'hui", Dates.dueLabel(monday, monday))
        assertEquals("pour demain", Dates.dueLabel(monday.plusDays(1), monday))
        assertEquals("en retard · samedi 5 septembre", Dates.dueLabel(LocalDate.of(2026, 9, 5), monday))
    }

    @Test
    fun `la fraicheur de synchronisation est explicite`() {
        val now = 1_700_000_000_000L

        assertEquals("jamais synchronisé", Dates.freshness(null, now))
        assertEquals("à l'instant", Dates.freshness(now - 30_000, now))
        assertEquals("il y a 4 min", Dates.freshness(now - 4 * 60_000, now))
        assertEquals("il y a 2 h", Dates.freshness(now - 120 * 60_000, now))
    }

    @Test
    fun `le HTML devient du texte lisible`() {
        val html = "<p>Bonjour&nbsp;!</p><ul><li>Exercice 1</li><li>Exercice 2</li></ul>"

        assertEquals("Bonjour !\n• Exercice 1\n• Exercice 2", Text.fromHtml(html))
        assertEquals("", Text.fromHtml(""))
        assertEquals("a < b", Text.fromHtml("a < b"))
    }

    @Test
    fun `les matieres retrouvent leur orthographe`() {
        assertEquals("Mathématiques", Text.prettySubject("MATHEMATIQUES"))
        assertEquals("Histoire-Géographie", Text.prettySubject("HISTOIRE-GEOGRAPHIE"))
        assertEquals("SVT", Text.prettySubject("svt"))
        // Une matière hors du dictionnaire garde son nom, simplement remis en casse de titre.
        assertEquals("Atelier Theatre", Text.prettySubject("ATELIER THEATRE"))
        assertEquals("Philosophie", Text.prettySubject("Philosophie"))
    }

    @Test
    fun `les cles de matiere ignorent casse accents et ponctuation`() {
        assertEquals(Text.normalizeKey("Physique-Chimie"), Text.normalizeKey("PHYSIQUE CHIMIE"))
        assertEquals("ML", Text.initials("Martin Leroy"))
        assertEquals("Un texte un peu…", Text.preview("Un texte un peu long", maxLength = 17))
    }

    @Test
    fun `un horodatage de liste donne l heure aujourd hui et la date ensuite`() {
        val today = LocalDate.of(2026, 9, 7)

        assertEquals("10:42", Dates.listStamp(today.atTime(10, 42), today))
        assertEquals("hier", Dates.listStamp(today.minusDays(1).atTime(18, 5), today))
        assertEquals("3 sept.", Dates.listStamp(today.minusDays(4).atTime(8, 0), today))
        // Une année différente doit être écrite, sinon « 3 sept. » ment d'un an.
        assertEquals("3 sept. 2025", Dates.listStamp(LocalDate.of(2025, 9, 3).atTime(8, 0), today))
    }
}
