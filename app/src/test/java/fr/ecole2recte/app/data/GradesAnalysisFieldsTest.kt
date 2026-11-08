package fr.ecole2recte.app.data

import fr.ecole2recte.app.data.api.Parsers
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GradesAnalysisFieldsTest {
    @Test fun `rang effectif mediane et extremes sont lus pour matiere et generale`() {
        val parsed = Parsers.grades(JSONObject("""{"periodes":[{"idPeriode":"T1",
            "ensembleMatieres":{"rang":"3","effectif":28,"mediane":"13,4","moyenneMin":"6,1","moyenneMax":19,"moyenneClasse":"12,5",
            "disciplines":[{"discipline":"MATHS","rang":2,"effectif":"27","moyenneMediane":"14,5","moyenneMin":0,"moyenneClasse":13}]}}]}"""))
        val general = parsed.periods.single().classPosition!!
        assertEquals(3, general.rank)
        assertEquals(28, general.size)
        assertEquals(13.4, general.median!!, 0.0)
        assertEquals(6.1, general.minimum!!, 0.0)
        val subject = parsed.subjectAverages.single().classPosition!!
        assertEquals(2, subject.rank)
        assertEquals(27, subject.size)
        assertEquals(14.5, subject.median!!, 0.0)
        assertEquals(0.0, subject.minimum!!, 0.0)
    }

    @Test fun `rang nul non entier et effectif absent ne sont pas inventes`() {
        val parsed = Parsers.grades(JSONObject("""{"periodes":[{"idPeriode":"T1","rang":2.5,"ensembleMatieres":{"disciplines":[
            {"discipline":"MATHS","rang":0,"effectif":28},{"discipline":"ANGLAIS"}]}}]}"""))
        assertNull(parsed.periods.single().classPosition?.rank)
        assertNull(parsed.subjectAverages.first().classPosition?.rank)
        assertNull(parsed.subjectAverages.last().classPosition?.size)
    }

    @Test fun `coefficients de periode connus nuls et absents sont distingues`() {
        val periods = Parsers.grades(JSONObject("""{"periodes":[
            {"idPeriode":"T1","coef":"2,5"},
            {"idPeriode":"T2","coefficient":0},
            {"idPeriode":"T3"}
        ]}""")).periods
        assertEquals(2.5, periods[0].coefficient!!, 0.0)
        assertEquals(0.0, periods[1].coefficient!!, 0.0)
        assertNull(periods[2].coefficient)
    }
}
