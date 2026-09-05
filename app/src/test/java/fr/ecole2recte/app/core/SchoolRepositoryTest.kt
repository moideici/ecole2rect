package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.model.Account
import fr.ecole2recte.app.core.model.DataSection
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.protocol.EcoleDirecteApi
import fr.ecole2recte.app.core.repo.AuthState
import fr.ecole2recte.app.core.repo.SchoolRepository
import fr.ecole2recte.app.core.store.SavedCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Le comportement promis par le dépôt, vérifié de bout en bout : afficher tout de suite,
 * ne rien perdre quand une section échoue, se reconnecter en silence.
 */
class SchoolRepositoryTest {

    private val credentials = SavedCredentials("eleve", "secret")
    private val today = LocalDate.of(2026, 9, 7)

    private fun fullEngine() = FakeHttpEngine()
        .on("gtk=1", body = "", headers = headers("X-GTK" to "g"), method = "GET")
        .on("/login.awp", Payloads.LOGIN_SUCCESS)
        .on("emploidutemps", Payloads.TIMETABLE)
        .on("cahierdetexte", Payloads.HOMEWORK_LIST)
        .on("notes.awp", Payloads.GRADES)
        .on("messages.awp", Payloads.MESSAGES)
        .on("viescolaire", Payloads.SCHOOL_LIFE)
        .on("elevesDocuments", Payloads.DOCUMENTS)

    private fun repository(
        engine: FakeHttpEngine,
        credentialStore: InMemoryCredentialStore = InMemoryCredentialStore(credentials),
        snapshotStore: InMemorySnapshotStore = InMemorySnapshotStore(),
        settingsStore: InMemorySettingsStore = InMemorySettingsStore(),
    ) = SchoolRepository(
        api = EcoleDirecteApi(engine),
        credentials = credentialStore,
        snapshots = snapshotStore,
        settingsStore = settingsStore,
        io = Dispatchers.Unconfined,
        now = { 1_700_000_000_000L },
        today = { today },
    )

    @Test
    fun `sans identifiants enregistres, l application demande la connexion`() = runTest {
        val repository = repository(FakeHttpEngine(), credentialStore = InMemoryCredentialStore(null))

        repository.start()

        assertEquals(AuthState.SignedOut, repository.auth.value)
    }

    @Test
    fun `au lancement, la reconnexion est silencieuse puis les donnees arrivent`() = runTest {
        val snapshots = InMemorySnapshotStore()
        val repository = repository(fullEngine(), snapshotStore = snapshots)

        repository.start()
        repository.refresh()

        val auth = repository.auth.value
        assertTrue(auth is AuthState.SignedIn)
        assertEquals("Martin", (auth as AuthState.SignedIn).account.firstName)

        val snapshot = repository.snapshot.value
        assertEquals(2, snapshot.lessons.size)
        assertEquals(2, snapshot.homework.size)
        assertEquals(2, snapshot.grades.size)
        assertEquals(2, snapshot.messages.size)
        assertEquals(3, snapshot.schoolLife.size)
        assertEquals(2, snapshot.documents.size)
        assertEquals(DataSection.entries.size, snapshot.syncedAt.size)
        assertFalse(repository.sync.value.offline)
        assertTrue("le cache doit être écrit après la synchronisation", snapshots.writes > 0)
    }

    @Test
    fun `hors ligne avec un cache, l application s ouvre quand meme`() = runTest {
        val cached = SchoolSnapshot(
            account = Account("4242", "E", "Martin", "Leroy"),
            homework = listOf(HomeworkItem("901", "Maths", today)),
            syncedAt = mapOf(DataSection.HOMEWORK to 1_699_000_000_000L),
        )
        val engine = FakeHttpEngine().apply { offline = true }
        val repository = repository(engine, snapshotStore = InMemorySnapshotStore(cached))

        repository.start()
        repository.refresh()

        val auth = repository.auth.value
        assertTrue("le cache doit ouvrir l'application, pas l'écran de connexion", auth is AuthState.SignedIn)
        assertEquals(1, repository.snapshot.value.homework.size)
        assertTrue(repository.sync.value.offline)
        assertEquals(DataSection.entries.size, repository.sync.value.errors.size)
    }

    @Test
    fun `une section en echec ne vide pas les autres`() = runTest {
        val engine = fullEngine()
        val repository = repository(engine)
        repository.start()
        repository.refresh()

        // Deuxième passage : les notes tombent en erreur, le reste répond normalement.
        val cassé = FakeHttpEngine()
            .on("gtk=1", body = "", headers = headers("X-GTK" to "g"), method = "GET")
            .on("/login.awp", Payloads.LOGIN_SUCCESS)
            .on("emploidutemps", Payloads.TIMETABLE)
            .on("cahierdetexte", Payloads.HOMEWORK_LIST)
            .on("notes.awp", """{"code":403,"message":"Accès refusé"}""")
            .on("messages.awp", Payloads.MESSAGES)
            .on("viescolaire", Payloads.SCHOOL_LIFE)
            .on("elevesDocuments", Payloads.DOCUMENTS)
        val second = repository(cassé)
        second.start()
        second.refresh()
        val avantErreur = second.snapshot.value.grades.size

        second.refresh(setOf(DataSection.GRADES))

        assertEquals("les notes déjà connues restent affichées", avantErreur, second.snapshot.value.grades.size)
        assertNotNull(second.sync.value.errorFor(DataSection.GRADES))
        assertFalse(second.sync.value.offline)
    }

    @Test
    fun `un jeton expire declenche une reconnexion et un seul reessai`() = runTest {
        val engine = FakeHttpEngine()
            .on("gtk=1", body = "", headers = headers("X-GTK" to "g"), method = "GET")
            .on("/login.awp", Payloads.LOGIN_SUCCESS)
            .on("emploidutemps", Payloads.SESSION_EXPIRED)
            .on("emploidutemps", Payloads.TIMETABLE)
        val repository = repository(engine)

        repository.start()
        repository.refresh(setOf(DataSection.TIMETABLE))

        assertEquals(2, repository.snapshot.value.lessons.size)
        assertTrue(repository.sync.value.errors.isEmpty())
        // GTK + connexion, appel refusé, GTK + reconnexion, appel rejoué.
        assertEquals(6, engine.requests.size)
    }

    @Test
    fun `cocher un devoir repond immediatement, meme sans reseau`() = runTest {
        val engine = fullEngine()
        val repository = repository(engine)
        repository.start()
        repository.refresh(setOf(DataSection.HOMEWORK))
        val devoir = repository.snapshot.value.homework.first()

        engine.offline = true
        repository.toggleHomeworkDone(devoir)

        assertTrue(repository.settings.value.isHomeworkDone(devoir.id, devoir.done))
    }

    @Test
    fun `se deconnecter efface identifiants et cache`() = runTest {
        val credentialStore = InMemoryCredentialStore(credentials)
        val snapshots = InMemorySnapshotStore()
        val repository = repository(fullEngine(), credentialStore, snapshots)
        repository.start()
        repository.refresh()

        repository.signOut()

        assertEquals(AuthState.SignedOut, repository.auth.value)
        assertTrue(credentialStore.cleared)
        assertTrue(repository.snapshot.value.isEmpty)
    }
}
