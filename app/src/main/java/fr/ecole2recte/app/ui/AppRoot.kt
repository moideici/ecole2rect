package fr.ecole2recte.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.session.AuthStatus
import fr.ecole2recte.app.session.SessionViewModel
import fr.ecole2recte.app.ui.accounts.AccountActions
import fr.ecole2recte.app.ui.common.appViewModel
import fr.ecole2recte.app.ui.login.LoginScreen
import fr.ecole2recte.app.ui.navigation.MainScaffold

/** Racine de l'interface : connexion ou application selon la session, sans écran d'attente. */
@Composable
fun AppRoot() {
    val session = appViewModel { SessionViewModel(it.credentialStore) }
    val state by session.state.collectAsStateWithLifecycle()
    if (state.status == AuthStatus.SignedOut || state.adding) {
        val other = state.accounts.firstOrNull { !it.isActive }
        // Sortie de secours de l'écran de connexion : annuler un ajout, ou revenir à un autre compte.
        val (cancelLabel, onCancel) = when {
            state.adding -> "Annuler" to session::cancelAddAccount
            other != null -> "Revenir à ${other.displayName}" to { session.switchAccount(other.identifier) }
            else -> null to null
        }
        LoginScreen(
            state = state.login,
            onLogin = session::login,
            onAnswerSecurity = session::answerSecurity,
            onCancelSecurity = session::cancelSecurity,
            cancelLabel = cancelLabel,
            onCancel = onCancel,
        )
    } else {
        MainScaffold(
            connection = state.connection,
            connectionMessage = state.connectionMessage,
            onRetryConnection = session::retryConnection,
            accounts = state.accounts,
            activeProfile = state.active,
            accountActions = AccountActions(
                onSwitch = session::switchAccount,
                onAdd = session::startAddAccount,
                onRemove = session::removeAccount,
                onSelectStudent = session::selectStudent,
            ),
        )
    }
}
