package fr.ecole2recte.app.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ecole2recte.app.data.EcoleDirecteClient
import fr.ecole2recte.app.data.EcoleDirecteException
import fr.ecole2recte.app.data.LoginResult
import fr.ecole2recte.app.data.SavedLogin
import fr.ecole2recte.app.data.SecureCredentialStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AuthStatus { Checking, SignedOut, SignedIn }

data class LoginUiState(
    val initialLogin: SavedLogin? = null,
    val error: String? = null,
    val diagnostic: String? = null,
    val securityQuestion: String? = null,
    val securityChoices: List<String> = emptyList(),
    val loading: Boolean = false,
)

data class SessionState(val status: AuthStatus, val login: LoginUiState)

/**
 * Détient la session : reconnexion silencieuse au lancement, connexion manuelle et question
 * de sécurité. Il survit aux rotations d'écran, contrairement à l'ancien état local de l'écran.
 */
class SessionViewModel(
    private val store: SecureCredentialStore,
    private val clientFactory: () -> EcoleDirecteClient = { EcoleDirecteClient() },
) : ViewModel() {
    private var pendingClient: EcoleDirecteClient? = null
    private val savedLogin: SavedLogin? = store.read()

    private val mutableState = MutableStateFlow(
        SessionState(
            status = if (savedLogin != null) AuthStatus.Checking else AuthStatus.SignedOut,
            login = LoginUiState(initialLogin = savedLogin),
        ),
    )
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    init {
        if (savedLogin != null) viewModelScope.launch { restore(savedLogin) }
    }

    private suspend fun restore(saved: SavedLogin) {
        val client = clientFactory()
        val result = safely {
            withContext(Dispatchers.IO) { client.login(saved.identifier, saved.password, saved.trustedAnswer) }
        }
        when (result) {
            is LoginResult.Success -> mutableState.update { it.copy(status = AuthStatus.SignedIn) }
            else -> {
                apply(result, client)
                mutableState.update { it.copy(status = AuthStatus.SignedOut) }
            }
        }
    }

    fun login(identifier: String, password: String, securityAnswer: String?) {
        if (mutableState.value.login.loading) return
        mutableState.update { it.copy(login = it.login.copy(loading = true, error = null, diagnostic = null)) }
        viewModelScope.launch {
            val pending = pendingClient
            val client = pending ?: clientFactory()
            val result = safely {
                withContext(Dispatchers.IO) {
                    if (securityAnswer != null && pending != null) {
                        client.completeSecurityQuestion(securityAnswer, identifier, password)
                    } else {
                        val savedAnswer = store.read()
                            ?.takeIf { it.identifier == identifier && it.password == password }
                            ?.trustedAnswer
                        client.login(identifier, password, savedAnswer)
                    }
                }
            }
            if (result is LoginResult.Success) {
                store.save(SavedLogin(identifier, password, client.approvedAnswer))
                pendingClient = null
                mutableState.update {
                    SessionState(AuthStatus.SignedIn, LoginUiState(initialLogin = store.read()))
                }
            } else {
                apply(result, client)
                mutableState.update { it.copy(login = it.login.copy(loading = false)) }
            }
        }
    }

    /** Reporte un résultat qui n'est pas un succès dans l'état de l'écran de connexion. */
    private fun apply(result: LoginResult, client: EcoleDirecteClient) {
        when (result) {
            is LoginResult.Success -> Unit
            is LoginResult.SecurityQuestionRequired -> {
                pendingClient = client
                mutableState.update {
                    it.copy(
                        login = it.login.copy(
                            securityQuestion = result.question ?: DEFAULT_QUESTION,
                            securityChoices = result.choices,
                        ),
                    )
                }
            }
            is LoginResult.Failure -> mutableState.update {
                it.copy(login = it.login.copy(error = result.message, diagnostic = result.diagnostic))
            }
        }
    }

    private suspend fun safely(block: suspend () -> LoginResult): LoginResult = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        LoginResult.Failure(
            (error as? EcoleDirecteException)?.message
                ?: "Connexion impossible. Vérifie ton réseau et réessaie.",
        )
    }

    private companion object {
        const val DEFAULT_QUESTION = "Réponds à la question demandée par ÉcoleDirecte."
    }
}
