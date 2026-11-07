package fr.ecole2recte.app.data

import fr.ecole2recte.app.data.cache.InMemoryResponseCache
import fr.ecole2recte.app.data.demo.DemoSchoolRepository
import fr.ecole2recte.app.domain.GradeOverview
import fr.ecole2recte.app.domain.RawGrade
import fr.ecole2recte.app.domain.SchoolRepository
import fr.ecole2recte.app.session.ActiveProfile
import fr.ecole2recte.app.session.ApiSession
import fr.ecole2recte.app.ui.common.GradesViewModel
import fr.ecole2recte.app.ui.common.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Lot I : choisir la période, appréciations, valeurs textuelles, extrêmes de la classe. */
class GradesPeriodsTest {
    private lateinit var server: MockWebServer
    private val gradeRequests = mutableListOf<String>()

    // Jeudi 5 novembre 2026 : le 2e trimestre est en cours.
    private val clock = Clock.fixed(Instant.parse("2026-11-05T08:50:00Z"), ZoneId.of("Europe/Paris"))

    private val grades = """{"code":200,"data":{"periodes":[
        {"idPeriode":"A001","periode":"1er trimestre","cloture":true,"dateDebut":"2026-09-01","dateFin":"2026-10-31",
         "ensembleMatieres":{"moyenneGenerale":"14,2","moyenneClasse":"12,9","appreciationPP":"Bon début d'année.","disciplines":[
            {"discipline":"MATHS","moyenne":"16","moyenneClasse":"12,5","moyenneMin":"6,5","moyenneMax":"18","coef":"4",
             "appreciations":["Très bon trimestre."],"professeurs":[{"civilite":"M.","prenom":"Paul","nom":"PETIT"}]},
            {"discipline":"ANGLAIS","moyenne":"13","moyenneClasse":"11"}]}},
        {"idPeriode":"A002","periode":"2e trimestre","cloture":false,"isCurrent":true,"dateDebut":"2026-11-01","dateFin":"2027-01-31",
         "ensembleMatieres":{"moyenneGenerale":"15,8","disciplines":[{"discipline":"ANGLAIS","moyenne":"13"}]}}],
        "notes":[
         {"id":1,"libelleMatiere":"MATHS","devoir":"DS 1","valeur":"16","noteSur":"20","coef":"2","codePeriode":"A001","date":"2026-10-03"},
         {"id":2,"libelleMatiere":"MATHS","devoir":"DS 2","valeur":"Abs","noteSur":"20","codePeriode":"A001","date":"2026-10-20"},
         {"id":3,"libelleMatiere":"ANGLAIS","devoir":"Oral","valeur":"Disp","noteSur":"20","codePeriode":"A001","date":"2026-10-21"},
         {"id":4,"libelleMatiere":"ANGLAIS","devoir":"Oral 2","valeur":"13","noteSur":"20","codePeriode":"A002","date":"2026-11-03"}]}}"""

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                if (path.endsWith("/notes.awp")) gradeRequests += path
                return MockResponse().setBody(if (path.endsWith("/notes.awp")) grades else """{"code":404}""")
            }
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

    @Test
    fun `par defaut la periode en cours et la liste de toutes les periodes`() = runTest {
        val overview = repository().grades()
        assertEquals("A002", overview.periodCode)
        assertEquals(listOf("1er trimestre" to false, "2e trimestre" to true), overview.periods.map { it.name to it.selected })
        assertEquals("15,8", overview.average)
    }

    @Test
    fun `choisir une autre periode change les notes et les moyennes, sans nouvelle requete`() = runTest {
        val repository = repository()
        repository.grades()
        val first = repository.grades("A001")
        assertEquals("A001", first.periodCode)
        assertEquals("14,2", first.average)
        assertEquals(listOf("Maths", "Anglais"), first.bySubject.map { it.subject.name })
        assertEquals(1, gradeRequests.size)
        assertEquals(listOf(true, false), repository.grades("A001").periods.map { it.selected })
    }

    @Test
    fun `une periode inconnue retombe sur la periode en cours`() = runTest {
        assertEquals("A002", repository().grades("n'importe quoi").periodCode)
        assertEquals("A002", repository().grades(null).periodCode)
    }

    @Test
    fun `les appreciations et les extremes de la classe sont montres`() = runTest {
        val overview = repository().grades("A001")
        assertEquals("Bon début d'année.", overview.appreciation)
        val maths = overview.bySubject.first { it.subject.name == "Maths" }
        assertEquals("Très bon trimestre.", maths.appreciation)
        assertEquals("6,5", maths.minimum)
        assertEquals("18,0", maths.maximum)
        assertEquals("12,5", maths.classAverage)
        val english = overview.bySubject.first { it.subject.name == "Anglais" }
        assertEquals("", english.appreciation)
        assertNull(english.minimum)
    }

    @Test
    fun `Abs et Disp restent distincts au lieu d'un Abs partout`() = runTest {
        val overview = repository().grades("A001")
        val maths = overview.bySubject.first { it.subject.name == "Maths" }.grades
        assertEquals(setOf("", "Abs"), maths.map { it.rawValue }.toSet())
        val english = overview.bySubject.first { it.subject.name == "Anglais" }.grades
        assertEquals("Disp", english.single().rawValue)
        assertNull(english.single().value)
        assertEquals("Dispensé", RawGrade.describe("Disp"))
        assertEquals("Absent", RawGrade.describe("Abs"))
        assertEquals("Non évalué", RawGrade.describe("NE"))
        assertEquals("Bizarre", RawGrade.describe(" Bizarre "))
    }

    @Test
    fun `une valeur textuelle ne compte pas dans la moyenne`() = runTest {
        val maths = repository().grades("A001").bySubject.first { it.subject.name == "Maths" }.grades
        assertEquals(1, maths.count { it.value != null })
        assertNotNull(maths.firstOrNull { it.rawValue == "Abs" })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GradesViewModelPeriodTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun before() = Dispatchers.setMain(dispatcher)

    @After
    fun after() = Dispatchers.resetMain()

    private val asked = mutableListOf<String?>()

    private val repository: SchoolRepository = object : SchoolRepository by DemoSchoolRepository() {
        override suspend fun grades(periodCode: String?): GradeOverview {
            asked += periodCode
            return GradeOverview("15", "", emptyList(), periodCode = periodCode ?: "en cours")
        }
    }

    @Test
    fun `choisir une periode recharge les notes de cette periode`() = runTest(dispatcher) {
        val viewModel = GradesViewModel(repository)
        advanceUntilIdle()
        assertEquals("en cours", (viewModel.state.value as UiState.Content).value.periodCode)

        viewModel.selectPeriod("A001")
        advanceUntilIdle()
        assertEquals("A001", (viewModel.state.value as UiState.Content).value.periodCode)
        assertEquals(listOf<String?>(null, "A001"), asked)
    }

    @Test
    fun `tirer pour rafraichir garde la periode choisie`() = runTest(dispatcher) {
        val viewModel = GradesViewModel(repository)
        advanceUntilIdle()
        viewModel.selectPeriod("A001")
        advanceUntilIdle()
        viewModel.pullToRefresh()
        advanceUntilIdle()
        assertEquals("A001", (viewModel.state.value as UiState.Content).value.periodCode)
    }
}
