package fr.ecole2recte.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.session.AuthStatus
import fr.ecole2recte.app.session.SessionViewModel
import fr.ecole2recte.app.ui.common.SplashScreen
import fr.ecole2recte.app.ui.common.appViewModel
import fr.ecole2recte.app.ui.login.LoginScreen
import fr.ecole2recte.app.ui.navigation.MainScaffold

/** Racine de l'interface : choisit entre chargement, connexion et application selon la session. */
@Composable
fun AppRoot() {
    val session = appViewModel { SessionViewModel(it.credentialStore) }
    val state by session.state.collectAsStateWithLifecycle()
    when (state.status) {
        AuthStatus.Checking -> SplashScreen()
        AuthStatus.SignedOut -> LoginScreen(state = state.login, onLogin = session::login)
        AuthStatus.SignedIn -> MainScaffold()
    }
}
