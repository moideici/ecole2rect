package fr.ecole2recte.app.data

import fr.ecole2recte.app.data.api.Parsers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

/**
 * Forme réelle probable d'une réponse de notes : pas de marqueur « période courante », la
 * moyenne générale dans `ensembleMatieres`, un trimestre à venir vide, une période « Année ».
 */
class GradesRealShapeTest {
    private val payload = JSONObject(
        """{"periodes":[
            {"idPeriode":"A000","periode":"Année","annuel":true,"dateDebut":"2026-09-01","dateFin":"2027-06-30","cloture":false,
             "ensembleMatieres":{"moyenneGenerale":"","disciplines":[]}},
            {"idPeriode":"A001","periode":"1er trimestre","dateDebut":"2026-09-01","dateFin":"2026-11-30","cloture":false,
             "ensembleMatieres":{"moyenneGenerale":"14,2","moyenneClasse":"12,1","disciplines":[
                {"discipline":"MATHS","moyenne":"15,5","coef":"4","professeurs":[{"nom":"M. PETIT"}]},
                {"discipline":"ANGLAIS","moyenne":"12,9","coef":"3"}]}},
            {"idPeriode":"A002","periode":"2e trimestre","dateDebut":"2026-12-01","dateFin":"2027-02-28","cloture":false,
             "ensembleMatieres":{"moyenneGenerale":"","disciplines":[]}}],
          "notes":[{"id":1,"libelleMatiere":"MATHS","devoir":"DS 1","valeur":"16","noteSur":"20","coef":"2","codePeriode":"A001","date":"2026-10-03"}]}""",
    )

    @Test
    fun `la moyenne generale est lue dans ensembleMatieres`() {
        val periods = Parsers.grades(payload).periods
        assertEquals(14.2, periods.first { it.code == "A001" }.average!!, 0.0001)
        assertEquals(true, periods.first { it.code == "A000" }.annual)
    }

    @Test
    fun `en cours de trimestre on choisit le trimestre en cours, pas le dernier de la liste`() {
        val parsed = Parsers.grades(payload)
        val chosen = PeriodSelection.select(parsed.periods, parsed.grades, LocalDate.of(2026, 11, 5))
        assertEquals("A001", chosen?.code)
    }

    @Test
    fun `entre deux trimestres on garde le dernier qui a des donnees`() {
        val parsed = Parsers.grades(payload)
        // 10 décembre : le 2e trimestre a commencé mais n'a encore rien.
        val chosen = PeriodSelection.select(parsed.periods, parsed.grades, LocalDate.of(2026, 12, 10))
        assertEquals("A001", chosen?.code)
    }

    @Test
    fun `sans date ni marqueur on prend la derniere periode qui a des donnees`() {
        val parsed = Parsers.grades(
            JSONObject(
                """{"periodes":[{"idPeriode":"T1","periode":"T1","ensembleMatieres":{"moyenneGenerale":"13","disciplines":[]}},
                    {"idPeriode":"T2","periode":"T2","ensembleMatieres":{"moyenneGenerale":"","disciplines":[]}}],"notes":[]}""",
            ),
        )
        assertNotNull(PeriodSelection.select(parsed.periods, parsed.grades, LocalDate.of(2026, 11, 5)))
        assertEquals("T1", PeriodSelection.select(parsed.periods, parsed.grades, LocalDate.of(2026, 11, 5))?.code)
    }
}

class SchoolLifeParserTest {
    @Test
    fun `la vie scolaire reprend la duree de l'etablissement et trie du plus recent au plus ancien`() {
        val events = Parsers.schoolLife(
            JSONObject(
                """{"absencesRetards":[
                    {"id":1,"typeElement":"Absence","date":"2026-10-02","displayDate":"2 oct. 8h-10h","justifie":true,"motif":"Maladie"},
                    {"id":2,"typeElement":"Retard","date":"2026-10-20","displayDate":"20 oct. 8h05","justifie":false}],
                  "sanctionsEncouragements":[{"id":3,"typeElement":"Encouragement","libelle":"Bravo","date":"2026-10-25"}]}""",
            ),
        )
        assertEquals(listOf("encouragement", "retard", "absence"), events.map { it.kind })
        assertEquals("2 oct. 8h-10h", events.last().duration)
        assertEquals(true, events.last().justified)
    }

    @Test
    fun `une reponse vide ne donne rien`() {
        assertEquals(emptyList<Any>(), Parsers.schoolLife(JSONObject("{}")))
    }
}
