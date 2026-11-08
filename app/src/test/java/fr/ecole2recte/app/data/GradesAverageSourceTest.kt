package fr.ecole2recte.app.data

import fr.ecole2recte.app.data.cache.InMemoryResponseCache
import fr.ecole2recte.app.domain.AverageSource
import fr.ecole2recte.app.session.ActiveProfile
import fr.ecole2recte.app.session.ApiSession
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Une moyenne est « officielle » ou « calculée par l'app » : ce que dit l'écran doit être ce que fait le code. */
class GradesAverageSourceTest {
    @Test
    fun `les statistiques de classe et coefficients de periode arrivent au modele affiche`() = runTest {
        body = payload("""{"idPeriode":"A001","periode":"T1",$dates,"coef":2,"ensembleMatieres":{
            "moyenneGenerale":"14","rang":3,"effectif":28,"mediane":"13,5","moyenneMin":6,"moyenneClasse":12,
            "disciplines":[{"discipline":"MATHS","moyenne":"14","rang":2,"effectif":27,"moyenneMediane":13}]}}""", "")
        val overview = repository().grades()
        assertEquals(2.0, overview.periods.single().coefficient!!, 0.0)
        assertEquals("Rang : 3 sur 28", overview.classPosition!!.rankLabel())
        assertEquals(13.5, overview.classPosition!!.median!!, 0.0)
        assertEquals("Rang : 2 sur 27", overview.bySubject.single().classPosition!!.rankLabel())
        assertEquals(13.0, overview.bySubject.single().classPosition!!.median!!, 0.0)
    }

    @Test
    fun `la capture garde les homonymes et range la specialite sous sa mere sans double comptage`() = runTest {
        val fixture = javaClass.getResource("/captures/grades_subjects.json")!!.readText()
        body = """{"code":200,"data":$fixture}"""
        val overview = repository().grades()
        assertEquals("13,00", overview.average)
        assertEquals(4, overview.bySubject.size)
        assertEquals(listOf(false, true, false, false), overview.bySubject.map { it.isSubSubject })
        assertEquals(1, overview.bySubject.first().grades.size)
        assertEquals(0, overview.bySubject[2].grades.size)
        assertEquals(4, overview.bySubject.map { it.id }.distinct().size)
    }

    @Test
    fun `une note rapprochee par code ne cree pas de ligne en double`() = runTest {
        body = payload("""{"idPeriode":"A001","periode":"T1",$dates,"ensembleMatieres":{"disciplines":[
            {"codeMatiere":"FR","discipline":"Français","moyenne":"12"}]}}""",
            """{"id":1,"codeMatiere":"FR","libelleMatiere":"FRANCAIS ","valeur":"12","codePeriode":"A001"}""")
        assertEquals(1, repository().grades().bySubject.size)
    }

    @Test
    fun `une matiere en retard recalcule la generale avec les coefficients et garde le bulletin`() = runTest {
        body = payload("""{"idPeriode":"A001","periode":"T1",$dates,"ensembleMatieres":{"dateCalcul":"2026-11-04","moyenneGenerale":"14,26","disciplines":[
            {"codeMatiere":"FR","discipline":"Français","moyenne":"12","coef":2},
            {"codeMatiere":"M","discipline":"Maths","moyenne":"16","coef":1}]}}""",
            """{"id":1,"codeMatiere":"FR","libelleMatiere":"FRANCAIS ","valeur":"12","codePeriode":"A001","date":"2026-11-01"},
            {"id":2,"codeMatiere":"FR","libelleMatiere":"FRANCAIS ","valeur":"18","codePeriode":"A001","date":"2026-11-05"}""")
        val overview = repository().grades()
        assertEquals(AverageSource.Recomputed, overview.averageSource)
        assertEquals("15,33", overview.average)
        assertEquals("15,00", overview.bySubject.first().average)
        assertFalse(overview.bySubject.first().averageOfficial)
        assertEquals(14.26, overview.verdict!!.official!!, .0001)
        assertEquals(listOf("2"), overview.verdict!!.behind.map { it.id })
    }

    @Test
    fun `sans matiere admissible les notes prennent le relais`() = runTest {
        body = payload("""{"idPeriode":"A001","periode":"T1",$dates,"ensembleMatieres":{"disciplines":[
            {"discipline":"Maths","moyenne":"20","coef":0}]}}""",
            """{"id":1,"libelleMatiere":"Maths","valeur":"14","codePeriode":"A001"}""")
        val overview = repository().grades()
        assertEquals(AverageSource.FromGrades, overview.averageSource)
        assertEquals("14,00", overview.average)
    }
    private lateinit var server: MockWebServer
    private var body = ""

    private val clock = Clock.fixed(Instant.parse("2026-11-05T08:50:00Z"), ZoneId.of("Europe/Paris"))

    private fun payload(periods: String, notes: String) = """{"code":200,"data":{"periodes":[$periods],"notes":[$notes]}}"""

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                MockResponse().setBody(if (request.requestUrl!!.encodedPath.endsWith("/notes.awp")) body else """{"code":404}""")
        }
        server.start()
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private fun repository(): EcoleDirecteRepository {
        val session = ApiSession()
        session.open("jdupont", EcoleDirecteClient(apiBase = server.url("/v3").toString().trimEnd('/')), listOf(Account("10", "E", "Jean", "DUPONT")))
        return EcoleDirecteRepository(session, ActiveProfile("jdupont", null, "Jean", "Jean Dupont", "JD"), InMemoryResponseCache(), clock = clock)
    }

    private val dates = """"dateDebut":"2026-09-01","dateFin":"2027-01-31""""

    @Test
    fun `la moyenne fournie par l'etablissement est officielle, celle d'une matiere sans moyenne est calculee`() = runTest {
        body = payload(
            """{"idPeriode":"A001","periode":"1er trimestre",$dates,"ensembleMatieres":{"moyenneGenerale":"15,8","disciplines":[
                {"discipline":"MATHS","moyenne":"16"}]}}""",
            """{"id":1,"libelleMatiere":"MATHS","devoir":"DS","valeur":"16","noteSur":"20","coef":"1","codePeriode":"A001","date":"2026-10-03"},
               {"id":2,"libelleMatiere":"ANGLAIS","devoir":"Oral","valeur":"12","noteSur":"20","coef":"1","codePeriode":"A001","date":"2026-10-04"}""",
        )
        val overview = repository().grades()
        assertEquals(AverageSource.Official, overview.averageSource)
        assertEquals("15,80", overview.average)
        val maths = overview.bySubject.first { it.subject.name == "Maths" }
        val english = overview.bySubject.first { it.subject.name == "Anglais" }
        assertTrue(maths.averageOfficial)
        assertFalse(english.averageOfficial)
        assertEquals("12,00", english.average)
    }

    @Test
    fun `sans moyenne generale, elle est calculee a partir des moyennes de matieres ponderees par leur coefficient`() = runTest {
        body = payload(
            """{"idPeriode":"A001","periode":"1er trimestre",$dates,"ensembleMatieres":{"disciplines":[
                {"discipline":"MATHS","moyenne":"16","coef":"4"},{"discipline":"ANGLAIS","moyenne":"13"}]}}""",
            """{"id":1,"libelleMatiere":"MATHS","devoir":"DS","valeur":"16","noteSur":"20","coef":"1","codePeriode":"A001","date":"2026-10-03"}""",
        )
        val overview = repository().grades()
        assertEquals(AverageSource.FromSubjects, overview.averageSource)
        // (16 x 4 + 13 x 1) / 5
        assertEquals("15,40", overview.average)
        assertFalse(overview.averageSource.isOfficial)
    }

    @Test
    fun `sans rien d'officiel, elle est calculee a partir des notes ramenees sur 20 et ponderees`() = runTest {
        body = payload(
            """{"idPeriode":"A001","periode":"1er trimestre",$dates}""",
            """{"id":1,"libelleMatiere":"MATHS","devoir":"DS 1","valeur":"16","noteSur":"20","coef":"2","codePeriode":"A001","date":"2026-10-03"},
               {"id":2,"libelleMatiere":"MATHS","devoir":"DS 2","valeur":"5","noteSur":"10","coef":"1","codePeriode":"A001","date":"2026-10-10"},
               {"id":3,"libelleMatiere":"MATHS","devoir":"DS 3","valeur":"Abs","noteSur":"20","coef":"1","codePeriode":"A001","date":"2026-10-12"},
               {"id":4,"libelleMatiere":"MATHS","devoir":"Bonus","valeur":"20","noteSur":"20","coef":"1","nonSignificatif":true,"codePeriode":"A001","date":"2026-10-13"}""",
        )
        val overview = repository().grades()
        assertEquals(AverageSource.FromGrades, overview.averageSource)
        // (16 x 2 + 10 x 1) / 3 : l'absence et la note non significative sont ignorées, 5/10 vaut 10/20.
        assertEquals("14,00", overview.average)
        assertFalse(overview.bySubject.single().averageOfficial)
    }

    @Test
    fun `sans aucune donnee, aucune moyenne n'est affichee`() = runTest {
        body = payload("""{"idPeriode":"A001","periode":"1er trimestre",$dates}""", "")
        val overview = repository().grades()
        assertEquals(AverageSource.None, overview.averageSource)
        assertEquals("–", overview.average)
    }

    @Test
    fun `les puces de periode portent la moyenne generale quand elle est connue`() = runTest {
        body = payload(
            """{"idPeriode":"A001","periode":"1er trimestre","dateDebut":"2026-09-01","dateFin":"2026-10-31","ensembleMatieres":{"moyenneGenerale":"14,2"}},
               {"idPeriode":"A002","periode":"2e trimestre","dateDebut":"2026-11-01","dateFin":"2027-01-31"}""",
            """{"id":1,"libelleMatiere":"MATHS","devoir":"DS","valeur":"12","noteSur":"20","coef":"1","codePeriode":"A002","date":"2026-11-03"}""",
        )
        val overview = repository().grades()
        assertEquals(listOf("1er trimestre" to "14,20", "2e trimestre" to null), overview.periods.map { it.name to it.average })
        // Choisie par les dates : le 2e trimestre, calculé faute de moyenne officielle.
        assertEquals("A002", overview.periodCode)
        assertEquals(AverageSource.FromGrades, overview.averageSource)
    }
}
