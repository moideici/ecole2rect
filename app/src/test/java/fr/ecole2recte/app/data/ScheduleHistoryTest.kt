package fr.ecole2recte.app.data

import fr.ecole2recte.app.data.cache.InMemoryResponseCache
import fr.ecole2recte.app.session.ActiveProfile
import fr.ecole2recte.app.session.ApiSession
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** L'agenda garde ce qu'un cours était avant d'être déplacé, contre un faux serveur. */
class ScheduleHistoryTest {
    private lateinit var server: MockWebServer
    private var timetable = ""

    // Jeudi 5 novembre 2026, 9 h 50 à Paris.
    private val clock = Clock.fixed(Instant.parse("2026-11-05T08:50:00Z"), ZoneId.of("Europe/Paris"))
    private val cache = InMemoryResponseCache()

    private fun day(start: String, end: String, room: String) =
        """{"code":200,"data":[{"id":1,"matiere":"MATHS","prof":"M. PETIT","salle":"$room","start_date":"2026-11-05 $start","end_date":"2026-11-05 $end"},
            {"id":2,"matiere":"ANGLAIS","start_date":"2026-11-05 11:00","end_date":"2026-11-05 12:00"}]}"""

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setBody(timetable)
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
        return EcoleDirecteRepository(session, ActiveProfile("jdupont", null, "Jean", "Jean Dupont", "JD"), cache, clock = clock)
    }

    @Test
    fun `un cours deplace entre deux lectures porte son ancien horaire et sa salle`() = runTest {
        val repository = repository()
        timetable = day("10:00", "10:55", "B12")
        val first = repository.schedule()
        assertNull(first.entries.first { it.subject.name == "Maths" }.before)

        repository.invalidate()
        timetable = day("14:00", "14:55", "C03")
        val second = repository.schedule()
        val maths = second.entries.first { it.subject.name == "Maths" }
        assertEquals("10:00", maths.before?.start)
        assertEquals("B12", maths.before?.room)
        assertEquals("14:00", maths.start)
        assertNull(second.entries.first { it.subject.name == "Anglais" }.before)
    }

    @Test
    fun `sans changement l'agenda n'annote rien`() = runTest {
        val repository = repository()
        timetable = day("10:00", "10:55", "B12")
        repository.schedule()
        repository.invalidate()
        assertEquals(listOf(null, null), repository.schedule().entries.map { it.before })
    }

    @Test
    fun `l'ancienne valeur reste affichee aux lectures suivantes tant que rien ne rechange`() = runTest {
        val repository = repository()
        timetable = day("10:00", "10:55", "B12")
        repository.schedule()
        repository.invalidate()
        timetable = day("14:00", "14:55", "C03")
        repository.schedule()
        repository.invalidate()
        val again = repository.schedule().entries.first { it.subject.name == "Maths" }
        assertEquals("10:00", again.before?.start)
    }
}
