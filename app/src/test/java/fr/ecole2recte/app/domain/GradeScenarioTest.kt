package fr.ecole2recte.app.domain

import org.junit.Assert.*
import org.junit.Test

class GradeScenarioTest {
    private val grade = GradeEntry("g", "Maths", 0, "DS", 10.0, 20.0, 2.0, null, null, null, null, true, "T1")
    private val maths = SubjectAverage(Subject("Maths", "", "", 0), "14,00", grades = listOf(grade), coefficient = 3.0, id = "m")
    private val overview = GradeOverview("13,00", "", listOf(maths, SubjectAverage(Subject("Anglais", "", "", 1), "10,00", coefficient = 1.0, id = "a")), periodCode = "T1", averageSource = AverageSource.FromSubjects)
    private fun project(vararg edits: ScenarioEdit) = GradeScenario.project(overview, edits.toList()) as ScenarioResult.Value

    @Test fun `plusieurs devoirs utilisent la moyenne affichee et leurs baremes`() {
        val result = project(ScenarioEdit("m", value = 8.0, scale = 10.0), ScenarioEdit("m", value = 32.0, scale = 40.0, coefficient = 2.0))
        assertEquals(15.2, result.subjects.getValue("m")!!, .00001)
        assertEquals(13.9, result.general!!, .00001)
    }

    @Test fun `modifier remplace les points et le poids de la note reelle`() {
        val result = GradeScenario.project(overview.copy(bySubject = listOf(maths.copy(average = "10,00"))), listOf(ScenarioEdit("m", "g", value = 9.0, scale = 10.0, coefficient = 1.0))) as ScenarioResult.Value
        assertEquals(18.0, result.subjects.getValue("m")!!, .00001)
        assertTrue(GradeScenario.project(overview, listOf(ScenarioEdit("m", "g", value = 9.0, scale = 10.0))) is ScenarioResult.Invalid)
    }

    @Test fun `retrait et ajout sont appliques simultanement`() {
        val line = maths.copy(average = "10,00")
        val result = GradeScenario.project(overview.copy(bySubject = listOf(line)), listOf(ScenarioEdit("m", "g", removed = true), ScenarioEdit("m", value = 8.0, scale = 10.0))) as ScenarioResult.Value
        assertEquals(16.0, result.subjects["m"]!!, .00001)
    }

    @Test fun `retirer la derniere note ne fabrique pas un zero`() {
        val result = project(ScenarioEdit("m", "g", removed = true))
        assertNull(result.subjects["m"])
        assertNull(result.general)
    }

    @Test fun `les effets de deux matieres se cumulent sur une generale officielle`() {
        val result = GradeScenario.project(overview.copy(average = "15,00", averageSource = AverageSource.Official), listOf(ScenarioEdit("m", value = 20.0), ScenarioEdit("a", value = 12.0))) as ScenarioResult.Value
        assertEquals(17.0, result.general!!, .00001)
    }

    @Test fun `matiere exclue et sous matiere ne changent pas la generale`() {
        for (line in listOf(maths.copy(coefficient = 0.0), maths.copy(isSubSubject = true))) {
            val result = GradeScenario.project(overview.copy(bySubject = listOf(line)), listOf(ScenarioEdit("m", value = 20.0))) as ScenarioResult.Value
            assertEquals(13.0, result.general!!, .00001)
        }
    }

    @Test fun `un scenario perime ou incoherent est refuse`() {
        for (edits in listOf(
            listOf(ScenarioEdit("absente", value = 10.0)),
            listOf(ScenarioEdit("m", "disparue", removed = true)),
            listOf(ScenarioEdit("m", "g", removed = true), ScenarioEdit("m", "g", removed = true)),
            listOf(ScenarioEdit("m", value = Double.NaN)),
            listOf(ScenarioEdit("m", value = 21.0)),
            listOf(ScenarioEdit("m", value = 10.0, scale = 0.0)),
            listOf(ScenarioEdit("m", value = 10.0, coefficient = 0.0)),
        )) assertTrue(GradeScenario.project(overview, edits) is ScenarioResult.Invalid)
    }

    @Test fun `les champs acceptent la virgule et refusent les valeurs non finies`() {
        assertEquals(ScenarioEdit("m", value = 7.5, scale = 10.0, coefficient = 2.0), GradeScenario.edit("m", null, "7,5", "10", "2"))
        assertNull(GradeScenario.edit("m", null, "NaN", "20", "1"))
    }

    @Test fun `la disposition respecte les seuils et la hauteur disponible`() {
        assertEquals(AnalysisLayout.Stacked, AnalysisLayout.choose(599, 900, false))
        assertEquals(AnalysisLayout.Columns, AnalysisLayout.choose(600, 900, false))
        assertEquals(AnalysisLayout.Wide, AnalysisLayout.choose(841, 900, false))
        assertEquals(AnalysisLayout.Stacked, AnalysisLayout.choose(900, 350, true))
    }
}
