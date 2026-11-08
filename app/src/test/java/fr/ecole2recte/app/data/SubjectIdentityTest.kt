package fr.ecole2recte.app.data

import fr.ecole2recte.app.data.api.Parsers
import fr.ecole2recte.app.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SubjectIdentityTest {
    @Test fun `dateCalcul est lue dans la capture documentee et reste facultative`() {
        val parsed = Parsers.grades(JSONObject(javaClass.getResource("/captures/grades_documented.json")!!.readText()))
        assertEquals("2026-11-04", parsed.periods.first().computedOn)
        assertNull(parsed.periods.last().computedOn)
    }
    @Test fun `la capture conserve codes sous-matieres groupes et homonymes`() {
        val parsed = Parsers.grades(JSONObject(javaClass.getResource("/captures/grades_subjects.json")!!.readText()))
        assertEquals(4, parsed.subjectAverages.size)
        val child = parsed.subjectAverages.first { it.subCode == "SPE" }
        assertTrue(child.isSubSubject)
        assertEquals("FR", child.subjectCode)
        assertTrue(parsed.subjectAverages.last().isGroup)
        assertEquals("FR", parsed.grades.single().subjectCode)
        assertEquals(13.0, AverageEngine.weightedSubjects(parsed.subjectAverages.map {
            AverageEngine.SubjectWeight(it.subjectCode + it.subCode, it.average, it.coefficient, it.isSubSubject)
        })!!, .0001)
    }

    @Test fun `rapprochement par code puis nom sans accents casse ou espaces multiples`() {
        val rows = listOf(SubjectIdentity("FR", "", "Français"), SubjectIdentity("AUTRE", "", "Français"))
        assertEquals(1, SubjectIdentity.match("AUTRE", "FRANCAIS", rows))
        assertEquals(0, SubjectIdentity.match("", "  FRANCAIS   ", rows))
        assertNull(SubjectIdentity.match("", "Maths", rows))
    }
}
