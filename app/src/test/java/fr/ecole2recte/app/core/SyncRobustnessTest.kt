package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.MessageFolder
import fr.ecole2recte.app.core.model.MessageItem
import fr.ecole2recte.app.core.repo.SyncState
import fr.ecole2recte.app.core.protocol.EcoleDirecteApi
import fr.ecole2recte.app.core.repo.Merge
import fr.ecole2recte.app.core.repo.SchoolRepository
import fr.ecole2recte.app.core.repo.statusLabel
import fr.ecole2recte.app.core.store.SavedCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Ce que la synchronisation ne doit pas détruire, et ce qu'elle ne doit pas prétendre.
 *
 * Trois promesses, toutes nées d'un défaut constaté. Une synchronisation de fond ne jette pas
 * ce que l'utilisateur a remonté à la main. Un appel qui échoue se refait. Et l'application ne
 * se déclare pas « à jour » quand elle n'a jamais rien synchronisé.
 */
class SyncRobustnessTest {

    private val credentials = SavedCredentials("eleve", "secret")
    private val today = LocalDate.of(2026, 9, 7)
    private val noon = today.atTime(12, 0)

    private fun message(id: String, daysAgo: Long, folder: MessageFolder = MessageFolder.RECEIVED) =
        MessageItem(
            id = id,
            subject = "Message $id",
            sender = "Mme Legrand",
            date = noon.minusDays(daysAgo),
            folder = folder,
        )

    // ── La pagination de la messagerie ───────────────────────────────────────

    @Test
    fun `une page fraiche ne jette pas les pages deja remontees`() {
        // L'élève a fait défiler jusqu'à un message d'il y a trente jours.
        val existing = listOf(
            message("recent", 0),
            message("moyen", 10),
            message("vieux", 30),
            message("envoye", 5, MessageFolder.SENT),
        )
        // La synchronisation ne redemande que le haut de la liste.
        val fetched = listOf(message("neuf", 0), message("recent", 0))

        val merged = Merge.messages(existing, fetched, MessageFolder.RECEIVED)

        assertEquals(
            "les pages profondes doivent rester : les rejeter les faisait repayer en données",
            setOf("neuf", "recent", "moyen", "vieux", "envoye"),
            merged.map { it.id }.toSet(),
        )
    }

    @Test
    fun `un dossier vide cote serveur se vide aussi ici`() {
        val existing = listOf(message("a", 1), message("b", 2), message("envoye", 3, MessageFolder.SENT))

        val merged = Merge.messages(existing, emptyList(), MessageFolder.RECEIVED)

        // Une page vide n'est pas une page manquante : le dossier est vide, on le suit —
        // sinon un message supprimé sur le site resterait là indéfiniment.
        assertEquals(listOf("envoye"), merged.map { it.id })
    }

    @Test
    fun `le corps deja lu survit au rafraichissement du haut de liste`() {
        val existing = listOf(message("a", 1).copy(body = "Bonjour"), message("vieux", 40))
        val fetched = listOf(message("a", 1))

        val merged = Merge.messages(existing, fetched, MessageFolder.RECEIVED)

        assertEquals("Bonjour", merged.first { it.id == "a" }.body)
        assertTrue(merged.any { it.id == "vieux" })
    }

    // ── Ce que l'état de synchronisation déclare ─────────────────────────────

    @Test
    fun `sans synchronisation reussie, rien n est a jour`() {
        val label = SyncState().statusLabel(lastSyncMillis = null, nowMillis = 1_700_000_000_000L)

        // « À jour · jamais synchronisé » : la phrase se contredisait elle-même, et c'est
        // celle que lisait l'utilisateur au premier lancement.
        assertFalse(label, label.contains("À jour"))
        assertEquals("Pas encore synchronisé", label)
    }

    @Test
    fun `une synchronisation reussie se dit a jour`() {
        val at = 1_700_000_000_000L
        val label = SyncState().statusLabel(lastSyncMillis = at, nowMillis = at + 60_000)

        assertTrue(label, label.startsWith("À jour"))
    }

    // ── Un appel qui échoue se refait ────────────────────────────────────────

    @Test
    fun `un jour dont le chargement echoue est redemande plus tard`() = runTest {
        val engine = FakeHttpEngine()
            .on("gtk=1", body = "", headers = headers("X-GTK" to "g"), method = "GET")
            .on("/login.awp", Payloads.LOGIN_SUCCESS)
        val repository = SchoolRepository(
            api = EcoleDirecteApi(engine),
            credentials = InMemoryCredentialStore(credentials),
            snapshots = InMemorySnapshotStore(),
            settingsStore = InMemorySettingsStore(),
            io = Dispatchers.Unconfined,
            now = { 1_700_000_000_000L },
            today = { today },
        )
        repository.start()

        // Dans le métro : l'appel part et se perd.
        engine.offline = true
        repository.loadHomeworkDetails(today)
        assertTrue(repository.snapshot.value.sessionContents.isEmpty())

        // De retour en ligne, le même jour doit pouvoir être redemandé sans `force` —
        // c'est ce que fera l'écran en réaffichant la fiche.
        engine.offline = false
        engine.on("cahierdetexte", Payloads.HOMEWORK_DAY)
        repository.loadHomeworkDetails(today)

        assertTrue(
            "le jour était marqué chargé avant la réponse : il ne revenait jamais",
            repository.snapshot.value.sessionContents.isNotEmpty(),
        )
    }

    // ── L'écriture vers ÉcoleDirecte ─────────────────────────────────────────

    @Test
    fun `un identifiant fabrique ici ne part jamais au serveur`() = runTest {
        val engine = FakeHttpEngine()
            .on("gtk=1", body = "", headers = headers("X-GTK" to "g"), method = "GET")
            .on("/login.awp", Payloads.LOGIN_SUCCESS)
        val repository = SchoolRepository(
            api = EcoleDirecteApi(engine),
            credentials = InMemoryCredentialStore(credentials),
            snapshots = InMemorySnapshotStore(),
            settingsStore = InMemorySettingsStore(),
            io = Dispatchers.Unconfined,
            now = { 1_700_000_000_000L },
            today = { today },
        )
        repository.start()
        val before = engine.requests.size

        // Un devoir sans identifiant côté serveur : l'analyse lui en a fabriqué un, qui ne
        // désigne rien chez ÉcoleDirecte. Le poster serait inventer une clé.
        val local = HomeworkItem(
            id = "${HomeworkItem.LOCAL_HOMEWORK_ID_PREFIX}1f3a9",
            subject = "Maths",
            dueDate = today,
        )
        repository.toggleHomeworkDone(local)

        assertFalse(local.idFromServer)
        assertEquals("aucune requête ne doit partir", before, engine.requests.size)
        // La coche locale, elle, reste : c'est tout ce qu'on a hors ligne.
        assertTrue(repository.settings.value.isHomeworkDone(local.id, false))
    }

    @Test
    fun `un refus du serveur sur une coche ne passe plus inapercu`() = runTest {
        val engine = FakeHttpEngine()
            .on("gtk=1", body = "", headers = headers("X-GTK" to "g"), method = "GET")
            .on("/login.awp", Payloads.LOGIN_SUCCESS)
            // L'écriture passe par un POST avec `verbe=put` : c'est la forme du service.
            .on("verbe=put", """{ "code": 520, "message": "Accès refusé" }""")
        val repository = SchoolRepository(
            api = EcoleDirecteApi(engine),
            credentials = InMemoryCredentialStore(credentials),
            snapshots = InMemorySnapshotStore(),
            settingsStore = InMemorySettingsStore(),
            io = Dispatchers.Unconfined,
            now = { 1_700_000_000_000L },
            today = { today },
        )
        repository.start()

        repository.toggleHomeworkDone(HomeworkItem(id = "912345", subject = "Maths", dueDate = today))

        // Le résultat était jeté : la case restait cochée et personne ne savait que le
        // serveur avait dit non. Le journal de diagnostic est le seul endroit où le
        // constater — encore faut-il y écrire.
        assertTrue(
            repository.errorLog.entries.value.joinToString(),
            repository.errorLog.entries.value.any { it.section == "Devoir coché" },
        )
    }
}
