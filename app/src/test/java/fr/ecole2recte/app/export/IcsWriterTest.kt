package fr.ecole2recte.app.export

import fr.ecole2recte.app.export.ExportFixtures.alternation
import fr.ecole2recte.app.export.ExportFixtures.lesson
import fr.ecole2recte.app.export.ExportFixtures.twoWeeks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class IcsWriterTest {
    private val stamp = Instant.parse("2026-11-06T09:30:00Z")
    private val end = LocalDate.of(2027, 6, 30)

    private fun options(from: String = "2026-11-05", weeks: Set<Int> = setOf(1, 2), owner: String = "jdupont/10") =
        IcsOptions(LocalDate.parse(from), end, owner, weeks, stamp = stamp)

    private fun alternatingIcs(options: IcsOptions = options()) = IcsWriter.write(TypicalWeeks.build(twoWeeks, alternation), options)

    /** Relit le texte comme un lecteur iCalendar : déplie les lignes, vérifie les fins de ligne. */
    private fun unfold(text: String): List<String> {
        assertTrue("chaque ligne se termine par CRLF", text.endsWith("\r\n"))
        assertFalse("pas de saut de ligne seul", text.replace("\r\n", "").contains('\n'))
        return text.removeSuffix("\r\n").split("\r\n").fold(mutableListOf<String>()) { lines, raw ->
            if (raw.startsWith(" ") || raw.startsWith("\t")) lines[lines.lastIndex] = lines.last() + raw.drop(1) else lines += raw
            lines
        }
    }

    private fun events(text: String): List<Map<String, List<String>>> {
        val result = mutableListOf<MutableMap<String, MutableList<String>>>()
        var current: MutableMap<String, MutableList<String>>? = null
        for (line in unfold(text)) {
            when (line) {
                "BEGIN:VEVENT" -> current = mutableMapOf()
                "END:VEVENT" -> { result += current!!; current = null }
                else -> current?.getOrPut(line.substringBefore(':').substringBefore(';')) { mutableListOf() }?.add(line)
            }
        }
        return result
    }

    private fun Map<String, List<String>>.value(name: String) = getValue(name).single().substringAfter(':')

    private fun byTitle(text: String, title: String) = events(text).first { it.value("SUMMARY") == title }

    @Test
    fun `le fichier est un calendrier valide avec fuseau, version et evenements equilibres`() {
        val text = alternatingIcs()
        val lines = unfold(text)
        assertEquals("BEGIN:VCALENDAR", lines.first())
        assertEquals("END:VCALENDAR", lines.last())
        assertTrue("VERSION:2.0" in lines)
        assertTrue(lines.any { it.startsWith("PRODID:") })
        assertTrue("TZID:Europe/Paris" in lines)
        assertEquals(lines.count { it.startsWith("BEGIN:") }, lines.count { it.startsWith("END:") })
        assertEquals(5, events(text).size)
        assertEquals(5, IcsWriter.eventCount(TypicalWeeks.build(twoWeeks, alternation), options()))
    }

    @Test
    fun `aucune ligne ne depasse 75 octets et les fins de ligne sont CRLF`() {
        val long = lesson("2026-11-02", "08:00", "09:00", "Éducation physique et sportive — natation, escalade; course d'orientation", "Gymnase Jean-Jaurès, bâtiment ouest", "Mme Éloïse Quentin-Lefèvre")
        val text = IcsWriter.write(TypicalWeeks.build(listOf(long), alternation), options("2026-11-01"))
        text.removeSuffix("\r\n").split("\r\n").forEach { assertTrue(it.toByteArray(Charsets.UTF_8).size <= 75) }
        // Une fois déplié, le texte est intact.
        assertEquals("Éducation physique et sportive — natation\\, escalade\\; course d'orientation", events(text).single().value("SUMMARY"))
        assertEquals("Gymnase Jean-Jaurès\\, bâtiment ouest", events(text).single().value("LOCATION"))
    }

    @Test
    fun `le pliage ne coupe jamais un caractere et se deplie a l'identique`() {
        val line = "SUMMARY:" + "é€😀".repeat(40)
        val folded = IcsWriter.fold(line)
        folded.split("\r\n").forEach { assertTrue(it.toByteArray(Charsets.UTF_8).size <= 75) }
        assertEquals(line, folded.replace("\r\n ", ""))
        assertEquals("court", IcsWriter.fold("court"))
    }

    @Test
    fun `virgules, points-virgules, barres obliques et sauts de ligne sont echappes`() {
        assertEquals("a\\,b\\;c\\\\d\\ne", IcsWriter.escape("a,b;c\\d\ne"))
        assertEquals("sans retour", IcsWriter.escape("sans re\rtour"))
    }

    @Test
    fun `les creneaux de toutes les semaines se repetent chaque semaine, les autres une semaine sur deux`() {
        val text = alternatingIcs()
        assertTrue(byTitle(text, "Maths").value("RRULE").startsWith("FREQ=WEEKLY;INTERVAL=1;UNTIL="))
        assertTrue(byTitle(text, "Physique").value("RRULE").startsWith("FREQ=WEEKLY;INTERVAL=2;UNTIL="))
        assertTrue(byTitle(text, "Chimie").value("RRULE").startsWith("FREQ=WEEKLY;INTERVAL=2;UNTIL="))
    }

    @Test
    fun `la premiere occurrence respecte la periode et la parite de la semaine`() {
        val text = alternatingIcs(options(from = "2026-11-05"))
        // Maths (lundi, chaque semaine) : le lundi 2 est avant la période, le suivant est le 9.
        assertEquals("TZID=Europe/Paris:20261109T080000", byTitle(text, "Maths").getValue("DTSTART").single().substringAfter("DTSTART;"))
        // Physique (mercredi, semaine 1) : le 4 est avant la période, la semaine 1 suivante est le 18.
        assertEquals("20261118T140000", byTitle(text, "Physique").value("DTSTART"))
        // Chimie (mercredi, semaine 2) : le 11 est dans la période.
        assertEquals("20261111T140000", byTitle(text, "Chimie").value("DTSTART"))
        assertEquals("20261111T150000", byTitle(text, "Chimie").value("DTEND"))
    }

    @Test
    fun `chaque premiere occurrence tombe sur le bon jour de la semaine et dans la bonne semaine`() {
        val text = alternatingIcs(options(from = "2026-12-01"))
        for (event in events(text)) {
            val start = LocalDate.parse(event.value("DTSTART").take(8), java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
            assertFalse(start.isBefore(LocalDate.of(2026, 12, 1)))
            val slotDay = when (event.value("SUMMARY")) { "Maths" -> 1; "Anglais" -> 2; "Physique", "Chimie" -> 3; else -> 4 }
            assertEquals(slotDay, start.dayOfWeek.value)
            if (event.value("SUMMARY") == "Physique") assertEquals(1, alternation.cycleOf(start))
            if (event.value("SUMMARY") == "Chimie") assertEquals(2, alternation.cycleOf(start))
        }
    }

    @Test
    fun `la fin de periode est en UTC comme l'exige un debut avec fuseau`() {
        val rule = byTitle(alternatingIcs(), "Maths").value("RRULE")
        // 30 juin 2027, 23 h 59 min 59 s à Paris (UTC+2) = 21 h 59 min 59 s UTC.
        assertTrue(rule.endsWith("UNTIL=20270630T215959Z"))
    }

    @Test
    fun `un cours annule est exclu de sa date`() {
        val svt = byTitle(alternatingIcs(options(from = "2026-11-02")), "SVT")
        assertEquals(listOf("EXDATE;TZID=Europe/Paris:20261105T090000"), svt.getValue("EXDATE"))
        // Hors période, aucune exclusion inutile.
        val later = byTitle(alternatingIcs(options(from = "2026-11-09")), "SVT")
        assertFalse(later.containsKey("EXDATE"))
    }

    @Test
    fun `une seule semaine choisie ne garde que ses creneaux, une semaine sur deux`() {
        val text = alternatingIcs(options(weeks = setOf(1)))
        val titles = events(text).map { it.value("SUMMARY") }
        assertEquals(listOf("Maths", "Anglais", "Physique", "SVT"), titles)
        for (event in events(text)) {
            assertTrue(event.value("RRULE").contains("INTERVAL=2"))
            val start = LocalDate.parse(event.value("DTSTART").take(8), java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
            assertEquals(1, alternation.cycleOf(start))
        }
        val second = events(alternatingIcs(options(weeks = setOf(2)))).map { it.value("SUMMARY") }
        assertEquals(listOf("Maths", "Anglais", "Chimie", "SVT"), second)
    }

    @Test
    fun `sans alternance tout est hebdomadaire et la semaine 2 n'existe pas`() {
        val text = IcsWriter.write(TypicalWeeks.build(twoWeeks, Alternation()), options())
        assertTrue(events(text).all { it.value("RRULE").contains("INTERVAL=1") })
        assertFalse(text.contains("Chimie"))
        assertFalse(text.contains("Semaine 1"))
    }

    @Test
    fun `les UID sont stables d'un export a l'autre et propres a chaque profil et creneau`() {
        val first = alternatingIcs(options())
        val second = IcsWriter.write(TypicalWeeks.build(twoWeeks, alternation), options().copy(stamp = Instant.parse("2027-01-01T00:00:00Z"), from = LocalDate.of(2026, 12, 1)))
        fun uids(text: String) = events(text).associate { it.value("SUMMARY") to it.value("UID") }
        assertEquals(uids(first), uids(second))
        assertEquals(5, uids(first).values.toSet().size)
        assertTrue(uids(first).values.all { it.endsWith("@ecole2recte") })
        val other = uids(alternatingIcs(options(owner = "autre/11")))
        assertTrue(other.keys.all { other.getValue(it) != uids(first).getValue(it) })
    }

    @Test
    fun `DTSTAMP est celui de l'export`() {
        assertTrue(events(alternatingIcs()).all { it.value("DTSTAMP") == "20261106T093000Z" })
    }

    @Test
    fun `salle, professeur et semaine sont dans le lieu et la description`() {
        val physique = byTitle(alternatingIcs(), "Physique")
        assertEquals("L1", physique.value("LOCATION"))
        assertEquals("Professeur : M. Newton\\nSemaine 1", physique.value("DESCRIPTION"))
        assertEquals("Professeur : M. Petit\\nToutes les semaines", byTitle(alternatingIcs(), "Maths").value("DESCRIPTION"))
    }

    @Test
    fun `une periode vide ne produit aucun evenement`() {
        val none = IcsWriter.write(TypicalWeeks.build(twoWeeks, alternation), IcsOptions(LocalDate.of(2027, 7, 1), end, "x", stamp = stamp))
        assertEquals(0, events(none).size)
        assertEquals("BEGIN:VCALENDAR", unfold(none).first())
    }
}
