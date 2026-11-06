package fr.ecole2recte.app.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProblemReportTest {
    private val device = DeviceInfo("0.2.0-dev", "15 (API 35)", "motorola moto g56 5G")

    @Test
    fun `le rapport contient le type, la version, l'appareil et le texte`() {
        val text = ProblemReport.build(ReportCategory.WrongData, "  La moyenne est vide  ", device, "session : oui")
        assertTrue("Type : Données fausses ou vides" in text)
        assertTrue("ecole2recte 0.2.0-dev" in text)
        assertTrue("motorola moto g56 5G" in text)
        assertTrue("La moyenne est vide" in text)
        assertTrue("Diagnostic technique" in text)
        assertTrue("session : oui" in text)
    }

    @Test
    fun `sans diagnostic joint, la section disparait`() {
        val text = ProblemReport.build(ReportCategory.Idea, "Un widget de plus", device, null)
        assertFalse("Diagnostic technique" in text)
    }

    @Test
    fun `une description vide est signalee comme telle`() {
        assertTrue("(non renseigné)" in ProblemReport.build(ReportCategory.Other, "   ", device, null))
    }

    @Test
    fun `la description est tronquee`() {
        val text = ProblemReport.build(ReportCategory.Crash, "x".repeat(ProblemReport.MAX_DESCRIPTION + 500), device, null)
        assertEquals(ProblemReport.MAX_DESCRIPTION, text.count { it == 'x' })
    }

    @Test
    fun `le titre porte la categorie`() {
        assertEquals("[ecole2recte] Plantage ou blocage", ProblemReport.title(ReportCategory.Crash))
    }
}
