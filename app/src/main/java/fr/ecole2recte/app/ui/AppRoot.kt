package fr.ecole2recte.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.data.EcoleDirecteClient
import fr.ecole2recte.app.session.AuthStatus
import fr.ecole2recte.app.session.SessionViewModel
import fr.ecole2recte.app.ui.accounts.AccountActions
import fr.ecole2recte.app.ui.common.appViewModel
import fr.ecole2recte.app.ui.common.rememberAppContainer
import fr.ecole2recte.app.ui.login.LoginScreen
import fr.ecole2recte.app.ui.login.NoticeScreen
import fr.ecole2recte.app.ui.login.Onboarding
import fr.ecole2recte.app.ui.login.OnboardingScreen
import fr.ecole2recte.app.ui.login.needsOnboarding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import fr.ecole2recte.app.ui.login.needsNotice
import fr.ecole2recte.app.ui.navigation.MainScaffold

/** Racine de l'interface : connexion ou application selon la session, sans écran d'attente. */
@Composable
fun AppRoot() {
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val session = appViewModel { SessionViewModel(
            it.credentialStore,
            clientFactory = { EcoleDirecteClient(apiVersion = it.settings.apiVersion.value) },
            apiSession = it.apiSession,
            tokenLog = it.tokenLog,
            onAccountRemoved = { id ->
                it.responseCache.clear(id)
                it.homeworkStatus.clear(id)
                it.subjectColors.clear(id)
                it.messageMarks.clear(id)
                it.homeworkFilter.clear(id)
                it.seenGrades.clear(id)
                fr.ecole2recte.app.widget.GoalStore.at(appContext).clearAccount(id)
                fr.ecole2recte.app.widget.WidgetConfigStore.at(appContext).clearAccount(id)
                fr.ecole2recte.app.sync.WidgetSnapshotStore(appContext).clearAccount(id)
                fr.ecole2recte.app.widget.WidgetRefresh.requestAll(appContext)
                it.forgetRepositories(id)
            },
        ) }
    val state by session.state.collectAsStateWithLifecycle()
    // Le jeton renouvelé est gardé (chiffré) pour éviter une connexion au prochain lancement.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { session.saveSession() }
    val container = rememberAppContainer()
    val replay by container.replayOnboarding.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
    Crossfade(targetState = state.status == AuthStatus.SignedOut || state.adding, animationSpec = tween(300), label = "root") { showLogin ->
    val noticeAccepted by container.settings.noticeAccepted.collectAsStateWithLifecycle()
    val onboardingSeen by container.settings.onboardingSeen.collectAsStateWithLifecycle()
    if (showLogin && needsNotice(noticeAccepted, loginVisible = true)) {
        NoticeScreen(onAccept = container.settings::acceptNotice)
    } else if (needsOnboarding(noticeAccepted, onboardingSeen, loginVisible = showLogin)) {
        OnboardingScreen(onFinish = container.settings::markOnboardingSeen)
    } else if (showLogin) {
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
        // Qui arrive dans l'application sans l'avoir vue (installation antérieure) n'a pas à la subir ensuite.
        androidx.compose.runtime.LaunchedEffect(Unit) { container.settings.markOnboardingSeen() }
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
    // « Revoir la présentation » (Plus > À propos) : par-dessus l'application, sans toucher à la session.
    if (replay) OnboardingScreen(onFinish = { container.replayOnboarding.value = false }, closeLabel = Onboarding.CLOSE)
    }
}
