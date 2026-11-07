package fr.ecole2recte.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.domain.Subject
import fr.ecole2recte.app.export.Alternation
import fr.ecole2recte.app.export.PdfStyle
import fr.ecole2recte.app.export.TimetableLayout
import fr.ecole2recte.app.export.TimetablePdf
import fr.ecole2recte.app.export.TypicalWeeks
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Le PDF se dessine pour de bon (couleur et niveaux de gris) : seul un appareil dispose de PdfDocument. */
@RunWith(AndroidJUnit4::class)
class ExportRenderingTest {
    private fun lesson(date: String, start: String, end: String, name: String, room: String) =
        ScheduleEntry(Subject(name, "M. Test", room, 1), "", false, date, start, end)

    private val week1 = LocalDate.of(2026, 11, 2)
    private val entries = listOf(
        lesson("2026-11-02", "08:00", "08:55", "Maths", "B12"),
        lesson("2026-11-02", "08:55", "09:50", "Maths", "B12"),
        lesson("2026-11-03", "10:00", "11:00", "Anglais", "A1"),
        lesson("2026-11-11", "14:00", "15:00", "Chimie", "L2"),
    )

    @Test
    fun alternatingScheduleRendersTwoPdfPages() {
        val schedule = TypicalWeeks.build(entries, Alternation(true, week1))
        val pages = TimetableLayout.pages(schedule, listOf(1, 2), "Emploi du temps", "Test", "Pied de page")
        listOf(PdfStyle(false), PdfStyle(true)).forEach { style ->
            val bytes = TimetablePdf.render(pages, style) { _, _ -> 0xFF3366CC.toInt() }
            assertTrue(String(bytes.copyOf(5), Charsets.ISO_8859_1) == "%PDF-")
            assertTrue(bytes.size > 1000)
        }
    }
}
