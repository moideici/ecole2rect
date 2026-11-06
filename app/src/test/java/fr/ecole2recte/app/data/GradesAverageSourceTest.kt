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
        assertEquals("15,8", overview.average)
        val maths = overview.bySubject.first { it.subject.name == "Maths" }
        val english = overview.bySubject.first { it.subject.name == "Anglais" }
        assertTrue(maths.averageOfficial)
        assertFalse(english.averageOfficial)
        assertEquals("12,0", english.average)
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
        assertEquals("15,4", overview.average)
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
        assertEquals("14,0", overview.average)
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
        assertEquals(listOf("1er trimestre" to "14,2", "2e trimestre" to null), overview.periods.map { it.name to it.average })
        // Choisie par les dates : le 2e trimestre, calculé faute de moyenne officielle.
        assertEquals("A002", overview.periodCode)
        assertEquals(AverageSource.FromGrades, overview.averageSource)
    }
}
