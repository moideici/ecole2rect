package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.protocol.HttpBinaryResponse
import fr.ecole2recte.app.core.protocol.HttpEngine
import fr.ecole2recte.app.core.protocol.HttpRequest
import fr.ecole2recte.app.core.protocol.HttpResponse
import fr.ecole2recte.app.core.protocol.NetworkFailure
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.core.store.CredentialStore
import fr.ecole2recte.app.core.store.SavedCredentials
import fr.ecole2recte.app.core.store.SettingsStore
import fr.ecole2recte.app.core.store.SnapshotStore

/**
 * Doublures de test : un réseau qui rejoue des réponses enregistrées et des stockages en
 * mémoire. Toute la chaîne connexion → synchronisation → cache est ainsi vérifiable sans
 * compte réel, ce qui est la seule façon d'avoir des tests utiles en intégration continue.
 */
class FakeHttpEngine : HttpEngine {

    /** Réponses à servir, dans l'ordre, pour les URL contenant la clé. */
    private val routes = mutableListOf<Route>()
    val requests = mutableListOf<HttpRequest>()
    var offline: Boolean = false

    private data class Route(
        val match: String,
        val method: String,
        val response: HttpResponse,
        var used: Boolean = false,
    )

    fun on(
        urlContains: String,
        body: String,
        status: Int = 200,
        headers: Map<String, List<String>> = emptyMap(),
        method: String = "POST",
    ): FakeHttpEngine {
        routes += Route(urlContains, method, HttpResponse(status, headers, body))
        return this
    }

    /** Fichiers binaires servis par [download], indexés par fragment d'URL. */
    private val files = mutableListOf<Pair<String, ByteArray>>()

    fun onDownload(urlContains: String, bytes: ByteArray): FakeHttpEngine {
        files += urlContains to bytes
        return this
    }

    override fun download(request: HttpRequest): HttpBinaryResponse {
        if (offline) throw NetworkFailure("réseau coupé")
        requests += request
        val file = files.firstOrNull { it.first in request.url }
            ?: error("Aucun fichier enregistré pour ${request.url}")
        return HttpBinaryResponse(200, emptyMap(), file.second)
    }

    override fun execute(request: HttpRequest): HttpResponse {
        if (offline) throw NetworkFailure("réseau coupé")
        requests += request
        // La méthode fait partie du filtre : l'appel GTK et la connexion partagent la même
        // URL, seul le verbe les distingue.
        val candidates = routes.filter { it.match in request.url && it.method == request.method }
        val route = candidates.firstOrNull { !it.used }
            ?: candidates.lastOrNull()
            ?: error("Aucune réponse enregistrée pour ${request.method} ${request.url}")
        route.used = true
        return route.response
    }
}

class InMemoryCredentialStore(private var value: SavedCredentials? = null) : CredentialStore {
    var cleared = false
        private set

    override fun read(): SavedCredentials? = value
    override fun save(credentials: SavedCredentials) {
        value = credentials
    }

    override fun clear() {
        value = null
        cleared = true
    }
}

class InMemorySnapshotStore(initial: SchoolSnapshot? = null, initialProfile: String? = null) : SnapshotStore {
    private val profiles = mutableMapOf<String, SchoolSnapshot>()

    var writes = 0
        private set

    init {
        initial?.let { profiles[initialProfile.orEmpty()] = it }
    }

    override fun read(profile: String?): SchoolSnapshot? = profiles[profile.orEmpty()]

    override fun write(profile: String?, snapshot: SchoolSnapshot) {
        profiles[profile.orEmpty()] = snapshot
        writes++
    }

    override fun clear() {
        profiles.clear()
    }
}

class InMemorySettingsStore(private var value: AppSettings = AppSettings()) : SettingsStore {
    override fun read(): AppSettings = value
    override fun write(settings: AppSettings) {
        value = settings
    }
}

/** En-têtes de réponse, écrits comme le serveur les renvoie. */
fun headers(vararg pairs: Pair<String, String>): Map<String, List<String>> =
    pairs.groupBy({ it.first }, { it.second })
