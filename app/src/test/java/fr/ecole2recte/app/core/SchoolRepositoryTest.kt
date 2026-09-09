package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.auth.AnswerMemory
import fr.ecole2recte.app.core.auth.BirthDates
import fr.ecole2recte.app.core.auth.SecurityAnswer
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
import org.junit.Assert.assertNull
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

    /** Connexion qui bute sur la double authentification avant d'aboutir. */
    private fun twoFactorEngine(answer: String = Payloads.DOUBLE_AUTH_ANSWER) = FakeHttpEngine()
        .on("gtk=1", body = "", headers = headers("X-GTK" to "g"), method = "GET")
        // Les jetons de la vérification arrivent en en-tête, comme sur le vrai serveur.
        .on(
            "/login.awp",
            Payloads.LOGIN_TWO_FACTOR,
            headers = headers("X-Token" to "t", "2FA-Token" to "f"),
        )
        .on("doubleauth", Payloads.DOUBLE_AUTH_QUESTION)
        .on("doubleauth", answer)
        .on("/login.awp", Payloads.LOGIN_SUCCESS)

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
        // Les modules facultatifs ne sont pas de la synchronisation courante : ils se
        // chargent à l'ouverture de leur écran.
        assertEquals(DataSection.ESSENTIAL.size, snapshot.syncedAt.size)
        assertFalse(repository.sync.value.offline)
        assertTrue("le cache doit être écrit après la synchronisation", snapshots.writes > 0)
    }

    // ── Emploi du temps, semaine par semaine ─────────────────────────────────

    /** Les corps de requête d'emploi du temps, dans l'ordre où ils sont partis. */
    private fun FakeHttpEngine.timetableCalls(): List<String> =
        requests.filter { "emploidutemps" in it.url }.mapNotNull { it.body }

    @Test
    fun `la synchronisation ne demande qu une semaine d emploi du temps`() = runTest {
        val engine = fullEngine()
        val repository = repository(engine)

        repository.start()
        repository.refresh()

        val calls = engine.timetableCalls()
        assertEquals(1, calls.size)
        // Du lundi au samedi : demander le dimanche ne rapporte jamais rien.
        assertTrue(calls.single(), calls.single().contains("2026-09-07"))
        assertTrue(calls.single(), calls.single().contains("2026-09-12"))
    }

    @Test
    fun `ouvrir une semaine non chargee la demande, et une seule fois`() = runTest {
        val engine = fullEngine()
        val repository = repository(engine)
        repository.start()
        repository.refresh()

        val next = today.plusWeeks(1)
        repository.loadWeek(next)
        repository.loadWeek(next)

        val calls = engine.timetableCalls()
        assertEquals("la semaine déjà chargée ne doit pas être redemandée", 2, calls.size)
        assertTrue(calls[1], calls[1].contains("2026-09-14"))
        // La semaine en cours reste connue : charger la suivante n'efface rien.
        assertEquals(2, repository.snapshot.value.lessons.size)
    }

    @Test
    fun `le bouton de synchronisation redemande la semaine affichee`() = runTest {
        val engine = fullEngine()
        val repository = repository(engine)
        repository.start()
        repository.refresh()

        repository.loadWeek(today, force = true)

        assertEquals(2, engine.timetableCalls().size)
    }

    @Test
    fun `des le vendredi, la semaine suivante arrive avec la synchronisation`() = runTest {
        val engine = fullEngine()
        val friday = LocalDate.of(2026, 9, 11)
        val repository = SchoolRepository(
            api = EcoleDirecteApi(engine),
            credentials = InMemoryCredentialStore(credentials),
            snapshots = InMemorySnapshotStore(),
            settingsStore = InMemorySettingsStore(),
            io = Dispatchers.Unconfined,
            now = { 1_700_000_000_000L },
            today = { friday },
        )

        repository.start()
        repository.refresh()

        // Sans ça, « demain » et l'écran d'accueil seraient vides tout le week-end.
        val calls = engine.timetableCalls()
        assertEquals(2, calls.size)
        assertTrue(calls[0], calls[0].contains("2026-09-07"))
        assertTrue(calls[1], calls[1].contains("2026-09-14"))
    }

    // ── Question de sécurité ─────────────────────────────────────────────────

    @Test
    fun `la date de naissance enregistree franchit seule la question de securite`() = runTest {
        val store = InMemoryCredentialStore(
            credentials.copy(birthDates = BirthDates.of(SecurityAnswer.BirthDate(month = "6"))),
        )
        val repository = repository(twoFactorEngine(), credentialStore = store)

        repository.start()

        val auth = repository.auth.value
        assertTrue("la question doit être franchie sans intervention", auth is AuthState.SignedIn)
        assertEquals("identifiant-question", store.read()?.trustedAnswer?.cn)
    }

    @Test
    fun `sans rien pour repondre, la question revient a l utilisateur`() = runTest {
        val repository = repository(twoFactorEngine(), credentialStore = InMemoryCredentialStore(credentials))

        repository.start()

        val auth = repository.auth.value
        assertTrue(auth is AuthState.SecurityQuestion)
        assertEquals("Quel est ton mois de naissance ?", (auth as AuthState.SecurityQuestion).question)
        assertNull("aucune tentative n'a été faite, donc rien à expliquer", auth.notice)
    }

    @Test
    fun `la reponse choisie est retenue jusqu a la rentree suivante`() = runTest {
        val store = InMemoryCredentialStore(credentials)
        val repository = repository(twoFactorEngine(), credentialStore = store)

        repository.start()
        repository.answerSecurityQuestion("Juin")

        assertTrue(repository.auth.value is AuthState.SignedIn)
        val remembered = store.read()?.answers?.answers.orEmpty()
        assertEquals(1, remembered.size)
        assertEquals("Juin", remembered.single().answer)
        assertEquals(LocalDate.of(2027, 8, 31), remembered.single().validUntil)
        assertEquals("Quel est ton mois de naissance ?", remembered.single().readableQuestion)
    }

    @Test
    fun `une reponse memorisee refusee est oubliee et la main rendue`() = runTest {
        val store = InMemoryCredentialStore(
            credentials.copy(
                answers = AnswerMemory().remembering("Quel est ton mois de naissance ?", "Mars", today),
            ),
        )
        val repository = repository(
            twoFactorEngine(answer = Payloads.DOUBLE_AUTH_REFUSED),
            credentialStore = store,
        )

        repository.start()

        val auth = repository.auth.value
        assertTrue("l'utilisateur doit reprendre la main", auth is AuthState.SecurityQuestion)
        assertNotNull("il faut dire pourquoi la question revient", (auth as AuthState.SecurityQuestion).notice)
        assertTrue(
            "une réponse devenue fausse ne doit pas être rejouée à la connexion suivante",
            store.read()?.answers?.answers.orEmpty().isEmpty(),
        )
    }

    @Test
    fun `une seule reponse automatique par connexion`() = runTest {
        val engine = FakeHttpEngine()
            .on("gtk=1", body = "", headers = headers("X-GTK" to "g"), method = "GET")
            .on(
                "/login.awp",
                Payloads.LOGIN_TWO_FACTOR,
                headers = headers("X-Token" to "t", "2FA-Token" to "f"),
            )
            .on("doubleauth", Payloads.DOUBLE_AUTH_QUESTION)
            .on("doubleauth", Payloads.DOUBLE_AUTH_ANSWER)
            // Le serveur repose la même question : insister ressemblerait à du forçage.
            .on(
                "/login.awp",
                Payloads.LOGIN_TWO_FACTOR,
                headers = headers("X-Token" to "t", "2FA-Token" to "f"),
            )
            .on("doubleauth", Payloads.DOUBLE_AUTH_QUESTION)
        val repository = repository(
            engine,
            credentialStore = InMemoryCredentialStore(
                credentials.copy(birthDates = BirthDates.of(SecurityAnswer.BirthDate(month = "6"))),
            ),
        )

        repository.start()

        assertTrue(repository.auth.value is AuthState.SecurityQuestion)
        assertEquals(
            "une seule réponse doit avoir été postée",
            1,
            engine.requests.count { it.url.contains("verbe=post") },
        )
    }

    // ── Compte parent ────────────────────────────────────────────────────────

    @Test
    fun `un compte parent ouvre sur le premier enfant et garde la messagerie de la famille`() = runTest {
        val engine = FakeHttpEngine()
            .on("gtk=1", body = "", headers = headers("X-GTK" to "g"), method = "GET")
            .on("/login.awp", Payloads.LOGIN_FAMILY)
            .on("emploidutemps", Payloads.TIMETABLE)
            .on("cahierdetexte", Payloads.HOMEWORK_LIST)
            .on("notes.awp", Payloads.GRADES)
            .on("messages.awp", Payloads.MESSAGES)
            .on("viescolaire", Payloads.SCHOOL_LIFE)
            .on("elevesDocuments", Payloads.DOCUMENTS)
        val settings = InMemorySettingsStore()
        val repository = repository(engine, settingsStore = settings)

        repository.start()
        repository.refresh()

        val auth = repository.auth.value as AuthState.SignedIn
        assertEquals("Martin", auth.account.firstName)
        assertEquals("777", auth.account.familyId)
        assertEquals(2, auth.accounts.size)
        assertTrue("les réglages doivent retenir qu'on est côté parent", settings.read().parentAccount)
        assertTrue(
            "la boîte aux lettres d'un parent est celle de la famille",
            engine.requests.any { it.url.contains("/familles/777/messages.awp") },
        )
        assertTrue(
            "les données scolaires restent celles de l'enfant",
            engine.requests.any { it.url.contains("/E/4242/emploidutemps.awp") },
        )
    }

    @Test
    fun `un devoir coche depuis un compte parent ne part pas au serveur`() = runTest {
        val engine = FakeHttpEngine()
            .on("gtk=1", body = "", headers = headers("X-GTK" to "g"), method = "GET")
            .on("/login.awp", Payloads.LOGIN_FAMILY)
        val repository = repository(engine)

        repository.start()
        repository.toggleHomeworkDone(HomeworkItem("901", "Maths", today))

        assertTrue(
            "le cahier de textes de l'élève ne doit pas être modifié depuis la vue parent",
            engine.requests.none { it.url.contains("idDevoir") || it.url.contains("cahierdetexte") },
        )
        assertTrue(repository.settings.value.isHomeworkDone("901", remoteDone = false))
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
        assertEquals(DataSection.ESSENTIAL.size, repository.sync.value.errors.size)
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
