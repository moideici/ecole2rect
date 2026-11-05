package fr.ecole2recte.app.session

import fr.ecole2recte.app.data.Account
import fr.ecole2recte.app.data.EcoleDirecteClient
import fr.ecole2recte.app.data.EcoleDirecteException
import fr.ecole2recte.app.data.FailureKind
import fr.ecole2recte.app.data.TokenLifetimeLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

sealed interface ApiSessionState {
    data object Closed : ApiSessionState
    data class Opening(val identifier: String) : ApiSessionState
    data class Ready(val identifier: String, val client: EcoleDirecteClient, val accounts: List<Account>) : ApiSessionState
    data class Failed(val identifier: String, val message: String, val kind: FailureKind) : ApiSessionState
}

/**
 * La session authentifiée que les dépôts de données utilisent.
 *
 * [SessionViewModel] l'ouvre, la ferme ou la déclare en échec ; les dépôts attendent qu'elle
 * soit prête au lieu de se connecter eux-mêmes — il n'y a ainsi qu'un seul endroit qui connaît
 * le mot de passe.
 */
class ApiSession(
    private val now: () -> Long = System::currentTimeMillis,
    private val minReauthIntervalMs: Long = 60_000,
    private val awaitTimeoutMs: Long = 60_000,
    /** Retient depuis combien de temps un jeton a été trouvé expiré : sert à décider de rejouer ou non. */
    private val tokenLog: TokenLifetimeLog? = null,
) {
    private val mutableState = MutableStateFlow<ApiSessionState>(ApiSessionState.Closed)
    val state: StateFlow<ApiSessionState> = mutableState.asStateFlow()

    /** Rouvre la session avec les identifiants enregistrés. Posée par [SessionViewModel]. */
    @Volatile
    var reauthenticator: (suspend () -> Boolean)? = null

    private val reauthLock = Mutex()
    private var lastReauthAt = 0L

    fun opening(identifier: String) {
        mutableState.value = ApiSessionState.Opening(identifier)
    }

    fun open(identifier: String, client: EcoleDirecteClient, accounts: List<Account>) {
        mutableState.value = ApiSessionState.Ready(identifier, client, accounts)
    }

    fun fail(identifier: String, message: String, kind: FailureKind) {
        mutableState.value = ApiSessionState.Failed(identifier, message, kind)
    }

    fun close() {
        mutableState.value = ApiSessionState.Closed
    }

    /** Attend la session de ce compte. Lève une [EcoleDirecteException] claire si elle n'arrive pas. */
    suspend fun await(identifier: String): ApiSessionState.Ready {
        val settled = withTimeoutOrNull(awaitTimeoutMs) {
            state.first { current ->
                when (current) {
                    is ApiSessionState.Ready -> current.identifier.equals(identifier, ignoreCase = true)
                    is ApiSessionState.Failed -> current.identifier.equals(identifier, ignoreCase = true)
                    ApiSessionState.Closed -> true
                    is ApiSessionState.Opening -> false
                }
            }
        }
        return when (settled) {
            is ApiSessionState.Ready -> settled
            is ApiSessionState.Failed -> throw EcoleDirecteException(settled.message, kind = settled.kind)
            ApiSessionState.Closed -> throw EcoleDirecteException("Aucun compte connecté.")
            else -> throw EcoleDirecteException("La connexion à ÉcoleDirecte prend trop de temps.", kind = FailureKind.Network)
        }
    }

    /** Un appel a trouvé le jeton expiré : on retient son âge, sans aucune donnée personnelle. */
    fun noteExpired() {
        val ready = state.value as? ApiSessionState.Ready ?: return
        val current = now()
        val lastOk = ready.client.lastOkAt.takeIf { it > 0 } ?: ready.client.issuedAt
        if (lastOk <= 0 || ready.client.issuedAt <= 0) return
        tokenLog?.recordExpiry(idleMs = (current - lastOk).coerceAtLeast(0), ageMs = (current - ready.client.issuedAt).coerceAtLeast(0))
    }

    /**
     * Rouvre la session après une expiration de jeton. Au plus une fois par minute : insister
     * sur une connexion qui échoue est exactement ce qui fait bloquer un compte.
     */
    suspend fun reauthenticate(): Boolean = reauthLock.withLock {
        val current = now()
        if (current - lastReauthAt < minReauthIntervalMs) return false
        lastReauthAt = current
        reauthenticator?.invoke() ?: false
    }
}
