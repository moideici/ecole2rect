package fr.ecole2recte.app.export

import fr.ecole2recte.app.export.ExportFixtures.alternation
import fr.ecole2recte.app.export.ExportFixtures.twoWeeks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class ScheduleExportTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun choices(format: ExportFormat = ExportFormat.Ics, weeks: Set<Int> = setOf(1, 2), until: String = "2027-06-30") =
        ExportChoices(format, weeks, LocalDate.of(2026, 11, 6), LocalDate.parse(until))

    @Test
    fun `un emploi du temps complet ne declenche que l'avertissement sur les vacances pour l'ICS`() {
        val schedule = TypicalWeeks.build(twoWeeks, alternation)
        assertEquals(1, ScheduleExport.warnings(schedule, choices()).size)
        assertTrue(ScheduleExport.warnings(schedule, choices()).single().contains("vacances"))
        assertEquals(emptyList<String>(), ScheduleExport.warnings(schedule, choices(ExportFormat.Pdf)))
    }

    @Test
    fun `une semaine inconnue est annoncee, sans inventer`() {
        val schedule = TypicalWeeks.build(twoWeeks.filter { it.date < "2026-11-09" }, alternation)
        val warnings = ScheduleExport.warnings(schedule, choices(ExportFormat.Pdf))
        assertEquals(1, warnings.size)
        assertTrue(warnings.single().startsWith("La semaine 2 n'est pas connue"))
        // Si l'on n'exporte que la semaine connue, rien à signaler.
        assertEquals(emptyList<String>(), ScheduleExport.warnings(schedule, choices(ExportFormat.Pdf, weeks = setOf(1))))
    }

    @Test
    fun `sans cours enregistres l'export est refuse avec une explication, une fin avant le debut est signalee`() {
        val empty = TypicalWeeks.build(emptyList(), Alternation())
        assertTrue(ScheduleExport.warnings(empty, choices()).single().startsWith("Aucun cours"))
        val schedule = TypicalWeeks.build(twoWeeks, alternation)
        assertTrue(ScheduleExport.warnings(schedule, choices(until = "2026-10-01")).any { it.contains("avant") })
    }

    @Test
    fun `le nom de fichier est sans accent, sans espace et dit la semaine`() {
        assertEquals("emploi-du-temps-jean-semaines-1-et-2.pdf", ScheduleExport.fileName(ExportFormat.Pdf, "Jean", true, setOf(1, 2)))
        assertEquals("emploi-du-temps-eloise-semaine-2.ics", ScheduleExport.fileName(ExportFormat.Ics, "Éloïse", true, setOf(2)))
        assertEquals("emploi-du-temps-jean-pierre.pdf", ScheduleExport.fileName(ExportFormat.Pdf, "Jean Pierre", false, setOf(1, 2)))
        assertEquals("emploi-du-temps.pdf", ScheduleExport.fileName(ExportFormat.Pdf, "", false, setOf(1)))
        assertEquals("emploi-du-temps-x.pdf", ScheduleExport.fileName(ExportFormat.Pdf, "../x", false, setOf(1)))
    }

    @Test
    fun `les fichiers exportes vont dans un dossier prive qui s'efface`() {
        val files = ExportFiles(File(folder.root, "exports"))
        val first = files.write("a".toByteArray(), "emploi-du-temps.ics")
        val second = files.write("b".toByteArray(), "emploi-du-temps.ics")
        assertFalse(first.parentFile == second.parentFile)
        assertTrue(first.canonicalPath.startsWith(File(folder.root, "exports").canonicalPath))
        assertEquals("a", first.readText())
        // Un nom piégé ne sort pas du dossier prévu.
        val trapped = files.write(byteArrayOf(1), "../../evil.ics")
        assertEquals("evil.ics", trapped.name)
        assertTrue(trapped.canonicalPath.startsWith(File(folder.root, "exports").canonicalPath))
        files.clear()
        assertFalse(File(folder.root, "exports").exists())
    }

    @Test
    fun `le dossier d'export est declare au FileProvider`() {
        val xml = File("src/main/res/xml/file_paths.xml").readText()
        assertTrue(xml.contains("path=\"exports/\""))
    }
}
