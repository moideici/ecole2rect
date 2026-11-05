package fr.ecole2recte.app.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ecole2recte.app.data.AuthClient
import fr.ecole2recte.app.data.BirthDate
import fr.ecole2recte.app.data.CredentialStore
import fr.ecole2recte.app.data.EcoleDirecteClient
import fr.ecole2recte.app.data.EcoleDirecteException
import fr.ecole2recte.app.data.FailureKind
import fr.ecole2recte.app.data.LoginResult
import fr.ecole2recte.app.data.SavedLogin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

enum class AuthStatus { SignedOut, SignedIn }

/** État du lien avec ÉcoleDirecte quand l'utilisateur est connecté. */
enum class ConnectionStatus { Connecting, Connected, Offline }

data class SecurityChallenge(val question: String, val choices: List<String>)

data class LoginError(val message: String, val kind: FailureKind)

data class LoginUiState(
    val identifier: String = "",
    val password: String = "",
    val challenge: SecurityChallenge? = null,
    val error: LoginError? = null,
    val diagnostic: String? = null,
    val loading: Boolean = false,
)

data class SessionState(
    val status: AuthStatus,
    val connection: ConnectionStatus,
    val connectionMessage: String? = null,
    val login: LoginUiState = LoginUiState(),
)

/**
 * Détient la session.
 *
 * - Au lancement, si des identifiants sont enregistrés, l'app s'ouvre **tout de suite** et la
 *   reconnexion se fait en arrière-plan : pas d'écran d'attente, pas de saisie.
 * - L'identifiant et le mot de passe ne sont demandés qu'une fois. La date de naissance n'est
 *   demandée que si ÉcoleDirecte pose une question de sécurité, et elle est mémorisée pour y
 *   répondre seule si la question revient.
 * - Garde-fous : un identifiant refusé n'est jamais renvoyé tel quel, un compte bloqué n'est
 *   jamais réessayé, une réponse de sécurité n'est tentée qu'une fois, et seules les pannes
 *   réseau ou serveur sont réessayées automatiquement, avec un délai croissant.
 */
class SessionViewModel(
    private val store: CredentialStore,
    private val clientFactory: () -> AuthClient = { EcoleDirecteClient() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val retryDelaysMs: List<Long> = listOf(5_000L, 20_000L),
) : ViewModel() {
    private class Pending(
        val client: AuthClient,
        val identifier: String,
        val password: String,
        val saved: SavedLogin?,
    )

    private class Outcome(val client: AuthClient, val result: LoginResult)

    private var pending: Pending? = null
    private var rejected: Pair<String, String>? = null
    private var restoreJob: Job? = null

    private val mutableState = MutableStateFlow(initialState(store.read()))
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    init {
        store.read()?.let(::restore)
    }

    private fun initialState(saved: SavedLogin?): SessionState =
        if (saved != null) {
            SessionState(AuthStatus.SignedIn, ConnectionStatus.Connecting)
        } else {
            SessionState(AuthStatus.SignedOut, ConnectionStatus.Connected)
        }

    // --- Reconnexion silencieuse -------------------------------------------------------------

    private fun restore(saved: SavedLogin) {
        restoreJob?.cancel()
        restoreJob = viewModelScope.launch {
            var attempt = 0
            while (true) {
                setConnection(ConnectionStatus.Connecting)
                val outcome = authenticate(saved.identifier, saved.password, saved)
                when (val result = outcome.result) {
                    is LoginResult.Success -> {
                        persist(outcome.client, saved.identifier, saved.password, saved, birthDate = null)
                        setConnection(ConnectionStatus.Connected)
                        return@launch
                    }
                    is LoginResult.SecurityQuestionRequired -> {
                        pending = Pending(outcome.client, saved.identifier, saved.password, saved)
                        signOut(
                            LoginUiState(
                                identifier = saved.identifier,
                                password = saved.password,
                                challenge = result.toChallenge(),
                            ),
                        )
                        return@launch
                    }
                    is LoginResult.Failure -> when (result.kind) {
                        FailureKind.Network, FailureKind.Service -> {
                            setConnection(ConnectionStatus.Offline, result.message)
                            if (attempt >= retryDelaysMs.size) return@launch
                            delay(retryDelaysMs[attempt++])
                        }
                        FailureKind.InvalidCredentials, FailureKind.Blocked -> {
                            rejected = saved.identifier to saved.password
                            signOut(
                                LoginUiState(
                                    identifier = saved.identifier,
                                    error = LoginError(
                                        if (result.kind == FailureKind.Blocked) result.message
                                        else "ÉcoleDirecte n'accepte plus ce mot de passe. Saisis le nouveau.",
                                        result.kind,
                                    ),
                                    diagnostic = result.diagnostic,
                                ),
                            )
                            return@launch
                        }
                        FailureKind.SecurityRefused -> {
                            signOut(
                                LoginUiState(
                                    identifier = saved.identifier,
                                    password = saved.password,
                                    error = LoginError(result.message, result.kind),
                                    diagnostic = result.diagnostic,
                                ),
                            )
                            return@launch
                        }
                        FailureKind.Other -> {
                            setConnection(ConnectionStatus.Offline, result.message)
                            return@launch
                        }
                    }
                }
            }
        }
    }

    /** Relance à la main la reconnexion quand l'app est hors ligne. */
    fun retryConnection() {
        val current = mutableState.value
        if (current.status != AuthStatus.SignedIn || current.connection == ConnectionStatus.Connecting) return
        store.read()?.let(::restore)
    }

    // --- Connexion manuelle ------------------------------------------------------------------

    fun login(identifier: String, password: String) {
        val id = identifier.trim()
        if (mutableState.value.login.loading || id.isBlank() || password.isBlank()) return
        if (rejected == id to password) {
            updateLogin {
                it.copy(
                    error = LoginError(
                        "Ces identifiants ont déjà été refusés. Modifie-les avant de réessayer.",
                        FailureKind.InvalidCredentials,
                    ),
                )
            }
            return
        }
        updateLogin { it.copy(loading = true, error = null, diagnostic = null) }
        viewModelScope.launch {
            val existing = store.read()
            val outcome = authenticate(id, password, existing)
            when (val result = outcome.result) {
                is LoginResult.Success -> {
                    persist(outcome.client, id, password, existing, birthDate = null)
                    signIn(id)
                }
                is LoginResult.SecurityQuestionRequired -> {
                    pending = Pending(outcome.client, id, password, existing)
                    updateLogin {
                        it.copy(loading = false, challenge = result.toChallenge(), error = null, diagnostic = null)
                    }
                }
                is LoginResult.Failure -> {
                    if (result.kind == FailureKind.InvalidCredentials || result.kind == FailureKind.Blocked) {
                        rejected = id to password
                    }
                    updateLogin {
                        it.copy(
                            loading = false,
                            error = LoginError(result.message, result.kind),
                            diagnostic = result.diagnostic,
                        )
                    }
                }
            }
        }
    }

    /**
     * Répond à la question de sécurité. Une seule tentative par question : si ÉcoleDirecte la
     * refuse, l'utilisateur doit se reconnecter pour en obtenir une nouvelle.
     * [birthDate] est mémorisée (chiffrée) pour répondre seule la prochaine fois.
     */
    fun answerSecurity(answer: String, birthDate: BirthDate? = null) {
        val current = pending ?: return
        if (mutableState.value.login.loading || answer.isBlank()) return
        updateLogin { it.copy(loading = true, error = null, diagnostic = null) }
        viewModelScope.launch {
            val result = safely {
                withContext(ioDispatcher) {
                    current.client.completeSecurityQuestion(answer, current.identifier, current.password)
                }
            }
            when (result) {
                is LoginResult.Success -> {
                    pending = null
                    persist(current.client, current.identifier, current.password, current.saved, birthDate)
                    signIn(current.identifier)
                }
                is LoginResult.SecurityQuestionRequired -> {
                    pending = null
                    updateLogin {
                        it.copy(
                            loading = false,
                            challenge = null,
                            error = LoginError(
                                "ÉcoleDirecte a redemandé une vérification. Reconnecte-toi pour obtenir une nouvelle question.",
                                FailureKind.SecurityRefused,
                            ),
                        )
                    }
                }
                is LoginResult.Failure -> {
                    val retryable = result.kind == FailureKind.Network || result.kind == FailureKind.Service
                    if (!retryable) pending = null
                    updateLogin {
                        it.copy(
                            loading = false,
                            challenge = if (retryable) it.challenge else null,
                            error = LoginError(
                                if (retryable) result.message
                                else "${result.message} Reconnecte-toi pour obtenir une nouvelle question.",
                                result.kind,
                            ),
                            diagnostic = result.diagnostic,
                        )
                    }
                }
            }
        }
    }

    /** Revient à la saisie de l'identifiant sans répondre à la question. */
    fun cancelSecurity() {
        pending = null
        updateLogin { it.copy(challenge = null, error = null, diagnostic = null, loading = false) }
    }

    // --- Mécanique interne -------------------------------------------------------------------

    private suspend fun authenticate(identifier: String, password: String, saved: SavedLogin?): Outcome {
        val client = clientFactory()
        val sameAccount = saved?.takeIf { it.identifier == identifier }
        var result = safely {
            withContext(ioDispatcher) { client.login(identifier, password, sameAccount?.trustedAnswer) }
        }
        val question = result as? LoginResult.SecurityQuestionRequired
        val birth = sameAccount?.birthDate
        if (question != null && birth != null) {
            val automatic = findBirthAnswer(question.question, question.choices, birth.day, birth.month, birth.year)
            if (automatic != null) {
                result = safely {
                    withContext(ioDispatcher) { client.completeSecurityQuestion(automatic, identifier, password) }
                }
            }
        }
        return Outcome(client, result)
    }

    /** Enregistre les identifiants sans jamais perdre la réponse de confiance ni la date de naissance. */
    private fun persist(
        client: AuthClient,
        identifier: String,
        password: String,
        previous: SavedLogin?,
        birthDate: BirthDate?,
    ) {
        val sameAccount = previous?.takeIf { it.identifier == identifier }
        store.save(
            SavedLogin(
                identifier = identifier,
                password = password,
                trustedAnswer = client.approvedAnswer ?: sameAccount?.trustedAnswer,
                birthDate = birthDate ?: sameAccount?.birthDate,
            ),
        )
    }

    private fun signIn(identifier: String) {
        rejected = null
        mutableState.value = SessionState(
            status = AuthStatus.SignedIn,
            connection = ConnectionStatus.Connected,
            login = LoginUiState(identifier = identifier),
        )
    }

    private fun signOut(login: LoginUiState) {
        mutableState.value = SessionState(AuthStatus.SignedOut, ConnectionStatus.Connected, login = login)
    }

    private fun setConnection(status: ConnectionStatus, message: String? = null) {
        mutableState.update { it.copy(connection = status, connectionMessage = message) }
    }

    private fun updateLogin(transform: (LoginUiState) -> LoginUiState) {
        mutableState.update { it.copy(login = transform(it.login)) }
    }

    private fun LoginResult.SecurityQuestionRequired.toChallenge() =
        SecurityChallenge(question ?: DEFAULT_QUESTION, choices)

    private suspend fun safely(block: suspend () -> LoginResult): LoginResult = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: EcoleDirecteException) {
        LoginResult.Failure(error.message ?: NETWORK_MESSAGE, kind = error.kind)
    } catch (error: IOException) {
        LoginResult.Failure(NETWORK_MESSAGE, kind = FailureKind.Network)
    } catch (error: Exception) {
        LoginResult.Failure("Connexion impossible pour le moment.", kind = FailureKind.Other)
    }

    private companion object {
        const val DEFAULT_QUESTION = "Réponds à la question demandée par ÉcoleDirecte."
        const val NETWORK_MESSAGE = "Pas de connexion à ÉcoleDirecte. Vérifie ton réseau puis réessaie."
    }
}
