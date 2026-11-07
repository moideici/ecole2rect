package fr.ecole2recte.app.data

import fr.ecole2recte.app.data.cache.InMemoryResponseCache
import fr.ecole2recte.app.session.ActiveProfile
import fr.ecole2recte.app.session.ApiSession
import fr.ecole2recte.app.session.toActiveProfile
import fr.ecole2recte.app.ui.navigation.AppTab
import fr.ecole2recte.app.ui.navigation.isAvailable
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class ModulesParsingTest {
    private fun parse(json: String) = parseModules(JSONArray(json))

    @Test
    fun `le drapeau peut etre un booleen, un nombre ou un texte`() {
        val modules = parse(
            """[{"code":"notes","enable":false},{"code":"EDT","enable":true},{"code":"MESSAGERIE","enable":0},
               {"code":"VIE_SCOLAIRE","enable":"1"},{"code":"CLOUD","enable":"false"}]""",
        )
        assertEquals(mapOf("NOTES" to false, "EDT" to true, "MESSAGERIE" to false, "VIE_SCOLAIRE" to true, "CLOUD" to false), modules)
    }

    @Test
    fun `sans drapeau lisible ou sans liste le module reste ouvert`() {
        assertEquals(mapOf("NOTES" to true), parse("""[{"code":"NOTES"},{"enable":false},{"code":""}]"""))
        assertEquals(emptyMap<String, Boolean>(), parseModules(null))
    }

    @Test
    fun `seul un module explicitement ferme est considere ferme`() {
        assertEquals(emptySet<SchoolModule>(), closedModules(emptyMap()))
        assertEquals(setOf(SchoolModule.Grades), closedModules(mapOf("NOTES" to false, "EDT" to true)))
        assertEquals(setOf(SchoolModule.Schedule), closedModules(mapOf("EMPLOI_DU_TEMPS" to false)))
        // Une seule des deux écritures ouverte suffit à garder le module.
        assertEquals(emptySet<SchoolModule>(), closedModules(mapOf("EDT" to false, "EMPLOI_DU_TEMPS" to true)))
    }

    @Test
    fun `la liste des modules se garde dans le stockage`() {
        val modules = mapOf("NOTES" to false, "EDT" to true)
        assertEquals(modules, modules.toModulesJson().toModuleMap())
        assertEquals(emptyMap<String, Boolean>(), (null as JSONObject?).toModuleMap())
    }

    @Test
    fun `les modules suivent le profil et la session dans le stockage chiffre`() {
        val student = Student("20", "Léa", "DUPONT", "6e A", mapOf("NOTES" to false))
        val account = Account("10", "1", "Marie", "DUPONT", listOf(student), mapOf("MESSAGERIE" to false))
        val storage = MapBlobStorage()
        SecureCredentialStore(storage, FakeCipher()).save(
            SavedLogin(
                "marie", "pw",
                profile = AccountProfile("Marie", "DUPONT", "1", listOf(student), account.modules),
                session = SavedSession("jeton", emptyMap(), "g", "ua", 1, 2, listOf(account)),
            ),
        )
        val kept = SecureCredentialStore(storage, FakeCipher()).find("marie")!!
        assertEquals(account.modules, kept.profile?.modules)
        assertEquals(mapOf("NOTES" to false), kept.profile?.students?.single()?.modules)
        assertEquals(listOf(account), kept.session?.accounts)
    }

    @Test
    fun `le profil actif sait ce qui est ferme, par eleve d'un compte famille`() {
        val first = Student("20", "Léa", "DUPONT", "6e A", mapOf("NOTES" to false))
        val second = Student("21", "Tom", "DUPONT", "4e B")
        val login = SavedLogin(
            "marie", "pw",
            profile = AccountProfile("Marie", "DUPONT", "1", listOf(first, second), mapOf("MESSAGERIE" to false)),
            activeStudentId = "20",
        )
        assertEquals(setOf(SchoolModule.Grades), login.toActiveProfile().closedModules)
        // Tom n'a pas sa propre liste : celle du compte s'applique.
        assertEquals(setOf(SchoolModule.Messages), login.copy(activeStudentId = "21").toActiveProfile().closedModules)
    }

    @Test
    fun `un onglet disparait avec son module, l'agenda seulement si les deux sont fermes`() {
        assertTrue(AppTab.Grades.isAvailable(emptySet()))
        assertFalse(AppTab.Grades.isAvailable(setOf(SchoolModule.Grades)))
        assertFalse(AppTab.Messages.isAvailable(setOf(SchoolModule.Messages)))
        assertTrue(AppTab.Agenda.isAvailable(setOf(SchoolModule.Schedule)))
        assertFalse(AppTab.Agenda.isAvailable(setOf(SchoolModule.Schedule, SchoolModule.Homework)))
        assertTrue(AppTab.Home.isAvailable(SchoolModule.entries.toSet()))
        assertTrue(AppTab.More.isAvailable(SchoolModule.entries.toSet()))
    }
}

class ClosedModulesRepositoryTest {
    private lateinit var server: MockWebServer
    private val paths = mutableListOf<String>()
    private val clock = Clock.fixed(Instant.parse("2026-11-05T08:50:00Z"), ZoneId.of("Europe/Paris"))

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                paths += path
                val body = when {
                    path.endsWith("/emploidutemps.awp") -> """{"code":200,"data":[{"id":1,"matiere":"MATHS","start_date":"2026-11-05 10:15","end_date":"2026-11-05 11:00"}]}"""
                    path.endsWith("/cahierdetexte.awp") -> """{"code":200,"data":{"2026-11-06":[{"matiere":"MATHS","idDevoir":5,"effectue":false}]}}"""
                    path.endsWith("/notes.awp") -> """{"code":200,"data":{"periodes":[],"notes":[{"id":1,"libelleMatiere":"MATHS","valeur":"15","noteSur":"20","codePeriode":"A1","date":"2026-11-04"}]}}"""
                    path.endsWith("/messages.awp") -> """{"code":200,"data":{"messages":{"received":[{"id":1,"from":{"nom":"X"},"subject":"S","read":false}]}}}"""
                    path.endsWith("/viescolaire.awp") -> """{"code":200,"data":{"absencesRetards":[]}}"""
                    else -> """{"code":404}"""
                }
                return MockResponse().setBody(body)
            }
        }
        server.start()
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private fun repository(account: Account, studentId: String? = null): EcoleDirecteRepository {
        val session = ApiSession()
        session.open("jdupont", EcoleDirecteClient(apiBase = server.url("/v3").toString().trimEnd('/')), listOf(account))
        return EcoleDirecteRepository(session, ActiveProfile("jdupont", studentId, "Jean", "Jean Dupont", "JD"), InMemoryResponseCache(), clock = clock)
    }

    private fun student(modules: Map<String, Boolean> = emptyMap()) = Account("10", "E", "Jean", "DUPONT", modules = modules)

    @Test
    fun `un module ferme n'est jamais appele et ne montre rien`() = runTest {
        val repository = repository(student(mapOf("NOTES" to false, "MESSAGERIE" to false)))
        assertEquals(emptyList<Any>(), repository.messagesPage(0).items)
        assertEquals("–", repository.home().grades.average)
        assertEquals(0, paths.count { it.endsWith("/notes.awp") || it.endsWith("/messages.awp") })
        // Les modules ouverts, eux, répondent normalement.
        assertEquals(1, repository.schedule().entries.size)
        assertEquals(1, paths.count { it.endsWith("/emploidutemps.awp") })
    }

    @Test
    fun `sans liste de modules tout est appele comme avant`() = runTest {
        val repository = repository(student())
        assertEquals(1, repository.messagesPage(0).items.size)
        repository.home()
        assertTrue(paths.any { it.endsWith("/notes.awp") })
    }

    @Test
    fun `les modules d'un enfant l'emportent sur ceux du compte famille`() = runTest {
        val family = Account(
            "1", "1", "Marie", "DUPONT",
            students = listOf(
                Student("20", "Léa", "DUPONT", modules = mapOf("EDT" to false)),
                Student("21", "Tom", "DUPONT"),
            ),
            modules = mapOf("MESSAGERIE" to true),
        )
        assertEquals(0, repository(family, studentId = "20").schedule().entries.size)
        assertEquals(0, paths.count { it.endsWith("/emploidutemps.awp") })
        assertEquals(1, repository(family, studentId = "21").schedule().entries.size)
        assertEquals(1, paths.count { it.endsWith("/emploidutemps.awp") })
    }

    @Test
    fun `la vie scolaire fermee n'est pas appelee`() = runTest {
        val repository = repository(student(mapOf("VIE_SCOLAIRE" to false)))
        assertEquals(0, repository.schoolLife().events.size)
        assertEquals(0, paths.count { it.endsWith("/viescolaire.awp") })
    }

    @Test
    fun `la connexion lit les modules du compte et de chaque enfant`() {
        val body = """{"code":200,"token":"t","data":{"accounts":[{"id":"1","typeCompte":"1","prenom":"Marie","nom":"DUPONT",
            "modules":[{"code":"MESSAGERIE","enable":true},{"code":"NOTES","enable":false}],
            "profile":{"eleves":[{"id":20,"prenom":"Léa","nom":"DUPONT","classe":{"libelle":"6e A"},
              "modules":[{"code":"EDT","enable":false}]}]}}]}}"""
        val login = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val gtk = "gtk=1" in request.requestUrl!!.encodedQuery.orEmpty()
                return MockResponse().addHeader("X-GTK", "g").setBody(if (gtk) "{}" else body)
            }
        }
        server.dispatcher = login
        val result = EcoleDirecteClient(apiBase = server.url("/v3").toString().trimEnd('/')).login("marie", "pw", null) as LoginResult.Success
        val account = result.accounts.single()
        assertEquals(mapOf("MESSAGERIE" to true, "NOTES" to false), account.modules)
        assertEquals(mapOf("EDT" to false), account.students.single().modules)
    }
}
