package fr.ecole2recte.app.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import fr.ecole2recte.app.domain.Badges
import fr.ecole2recte.app.session.AccountSummary
import fr.ecole2recte.app.session.ActiveProfile
import fr.ecole2recte.app.session.ConnectionStatus
import fr.ecole2recte.app.ui.accounts.AccountActions
import fr.ecole2recte.app.ui.accounts.AccountButton
import fr.ecole2recte.app.ui.accounts.AccountSheet
import fr.ecole2recte.app.ui.agenda.AGENDA_HOMEWORK
import fr.ecole2recte.app.ui.agenda.AGENDA_SCHEDULE
import fr.ecole2recte.app.ui.agenda.AgendaScreen
import fr.ecole2recte.app.ui.common.BadgesViewModel
import fr.ecole2recte.app.ui.common.ConnectionBanner
import fr.ecole2recte.app.ui.common.LocalActiveProfile
import fr.ecole2recte.app.ui.common.LocalResumeTick
import fr.ecole2recte.app.ui.common.LocalScrollEvents
import fr.ecole2recte.app.data.SchoolModule
import fr.ecole2recte.app.ui.common.AdviceActions
import fr.ecole2recte.app.ui.common.FreshnessBanner
import fr.ecole2recte.app.ui.common.LocalPrivacy
import fr.ecole2recte.app.ui.common.PrivacyToggle
import fr.ecole2recte.app.domain.PrivacyMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import fr.ecole2recte.app.ui.common.LocalAdviceActions
import fr.ecole2recte.app.data.AdviceAction
import fr.ecole2recte.app.ui.common.UiState
import fr.ecole2recte.app.ui.common.appViewModel
import fr.ecole2recte.app.ui.common.rememberAppContainer
import fr.ecole2recte.app.ui.theme.LocalSubjectColorOverrides
import fr.ecole2recte.app.ui.common.rememberSchoolRepository
import fr.ecole2recte.app.ui.grades.AverageMethodScreen
import fr.ecole2recte.app.ui.grades.GradesRoute
import fr.ecole2recte.app.ui.home.HomeRoute
import fr.ecole2recte.app.ui.messages.MessagesRoute
import fr.ecole2recte.app.ui.more.MoreScreen
import fr.ecole2recte.app.ui.search.SearchRoute
import fr.ecole2recte.app.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Les cinq onglets de la barre du bas.
 * Accueil : l'essentiel d'un coup d'œil · Agenda : emploi du temps et travail à faire ·
 * Notes · Messages · Plus : compte, apparence, connexion.
 */
internal enum class AppTab(val route: String, val label: String, val icon: ImageVector) {
    Home("accueil", "Accueil", Icons.Outlined.Home),
    Agenda("agenda", "Agenda", Icons.Outlined.CalendarMonth),
    Grades("notes", "Notes", Icons.AutoMirrored.Outlined.MenuBook),
    Messages("messages", "Messages", Icons.Outlined.ChatBubbleOutline),
    More("plus", "Plus", Icons.Outlined.MoreHoriz),
}

/** Un onglet disparaît quand l'établissement ferme le module qu'il montre (l'agenda quand les deux le sont). */
internal fun AppTab.isAvailable(closed: Set<SchoolModule>): Boolean = when (this) {
    AppTab.Agenda -> SchoolModule.Schedule !in closed || SchoolModule.Homework !in closed
    AppTab.Grades -> SchoolModule.Grades !in closed
    AppTab.Messages -> SchoolModule.Messages !in closed
    else -> true
}

/** L'écran de recherche : une destination à part, pas un onglet. */
internal const val SEARCH_ROUTE = "recherche"

/** « Comment la moyenne est calculée ? » : une destination à part, ouverte depuis Notes et Plus. */
internal const val METHOD_ROUTE = "moyennes"

private fun AppTab.badge(badges: Badges): Int = when (this) {
    AppTab.Agenda -> badges.urgentHomework
    AppTab.Grades -> badges.newGrades
    AppTab.Messages -> badges.unreadMessages
    else -> 0
}

private fun NavController.navigateToTab(tab: AppTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun MainScaffold(
    connection: ConnectionStatus,
    connectionMessage: String?,
    onRetryConnection: () -> Unit,
    accounts: List<AccountSummary>,
    activeProfile: ActiveProfile?,
    accountActions: AccountActions,
) {
    // Un autre compte ou un autre élève repart d'un écran vierge : aucune donnée ne passe de l'un à l'autre.
    val subjectColors = rememberAppContainer().subjectColors
    val overrides by remember(activeProfile?.key) { subjectColors.overrides(activeProfile?.key.orEmpty()) }
        .collectAsStateWithLifecycle(emptyMap())
    // Pendant le fondu, l'ancien écran garde son propre profil : rien ne passe d'un compte à l'autre.
    Crossfade(targetState = activeProfile, animationSpec = tween(260), label = "account") { profile ->
        CompositionLocalProvider(LocalActiveProfile provides profile, LocalSubjectColorOverrides provides overrides) {
            MainContent(connection, connectionMessage, onRetryConnection, accounts, profile, accountActions)
        }
    }
}

@Composable
private fun MainContent(
    connection: ConnectionStatus,
    connectionMessage: String?,
    onRetryConnection: () -> Unit,
    accounts: List<AccountSummary>,
    activeProfile: ActiveProfile?,
    accountActions: AccountActions,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    var showAccounts by rememberSaveable { mutableStateOf(false) }
    var agendaPage by rememberSaveable { mutableIntStateOf(AGENDA_SCHEDULE) }
    val scrollEvents = remember { MutableSharedFlow<String>(extraBufferCapacity = 1) }
    val closedModules = activeProfile?.closedModules.orEmpty()
    val tabs = AppTab.entries.filter { it.isAvailable(closedModules) }

    // Raccourci, notification ou widget : ouvre l'onglet demandé, puis oublie la demande.
    val container = rememberAppContainer()
    val pendingTab by container.pendingTab.collectAsStateWithLifecycle()
    val pendingProfile by container.pendingWidgetProfile.collectAsStateWithLifecycle()
    LaunchedEffect(pendingTab, pendingProfile, activeProfile?.key) {
        val requested = pendingTab ?: return@LaunchedEffect
        val target = pendingProfile
        if (!target.isNullOrBlank() && target != activeProfile?.key?.lowercase()) {
            val account = accounts.firstOrNull { target.startsWith(it.identifier.lowercase() + "/") }
            if (account != null) {
                if (activeProfile?.identifier?.equals(account.identifier, ignoreCase = true) != true) {
                    accountActions.onSwitch(account.identifier)
                    return@LaunchedEffect
                }
                val child = target.removePrefix(account.identifier.lowercase() + "/")
                if (child.isNotBlank() && account.students.any { it.id == child } && child != activeProfile?.studentId) {
                    accountActions.onSelectStudent(child)
                    return@LaunchedEffect
                }
            }
            // Un profil supprimé n'ouvre jamais une matière homonyme d'un autre compte.
            if (target != activeProfile?.key?.lowercase()) container.pendingWidgetSubject.value = null
        }
        container.pendingWidgetProfile.value = null
        if (requested == SEARCH_ROUTE) {
            navController.navigate(SEARCH_ROUTE) { launchSingleTop = true }
        } else if (requested == "agenda_homework") {
            if (AppTab.Agenda in tabs) {
                agendaPage = AGENDA_HOMEWORK
                navController.navigateToTab(AppTab.Agenda)
            }
        } else {
            tabs.firstOrNull { it.route == requested }?.let(navController::navigateToTab)
        }
        container.pendingTab.value = null
    }

    val repository = rememberSchoolRepository()
    val badgesViewModel = appViewModel(key = "badges/${activeProfile?.key}") { BadgesViewModel(repository) }
    val badgesState by badgesViewModel.state.collectAsStateWithLifecycle()
    val badges = (badgesState as? UiState.Content)?.value ?: Badges()
    val freshness by repository.freshness.collectAsStateWithLifecycle()

    // Chaque retour au premier plan prévient les écrans ; chacun décide s'il a besoin de se relire.
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { resumeTick++ }
    LaunchedEffect(resumeTick) { badgesViewModel.refreshIfStale() }

    val adviceActions = remember(activeProfile?.key) {
        AdviceActions(reconnect = onRetryConnection, openDiagnostic = { navController.navigateToTab(AppTab.More) })
    }
    val privacyEnabled by container.settings.privacyMode.collectAsStateWithLifecycle()
    val privacyMessages by container.settings.privacyHideMessages.collectAsStateWithLifecycle()
    val privacy = remember(privacyEnabled, privacyMessages) { PrivacyMode(privacyEnabled, privacyMessages) }
    CompositionLocalProvider(
        LocalScrollEvents provides scrollEvents,
        LocalResumeTick provides resumeTick,
        LocalAdviceActions provides adviceActions,
        LocalPrivacy provides privacy,
    ) {
        Scaffold(
            containerColor = AppTheme.colors.background,
            bottomBar = {
                NavigationBar(containerColor = AppTheme.colors.surface) {
                    tabs.forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                        val count = tab.badge(badges)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                // Retoucher l'onglet ouvert remonte en haut de sa liste.
                                if (selected) scrollEvents.tryEmit(tab.route) else navController.navigateToTab(tab)
                            },
                            icon = {
                                BadgedBox(badge = { if (count > 0) Badge { Text("$count") } }) {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            },
                            label = { Text(tab.label, fontSize = 12.sp) },
                        )
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (connection == ConnectionStatus.Offline) {
                    ConnectionBanner(connectionMessage, onRetryConnection)
                }
                if (freshness.stale) {
                    FreshnessBanner(freshness) { action ->
                        when (action) {
                            AdviceAction.Reconnect -> onRetryConnection()
                            AdviceAction.OpenDiagnostic -> navController.navigateToTab(AppTab.More)
                            else -> badgesViewModel.retry()
                        }
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    Surface(modifier = Modifier.fillMaxSize(), color = AppTheme.colors.background) {
                        NavHost(
                            navController = navController,
                            startDestination = AppTab.Home.route,
                            enterTransition = { fadeIn(tween(240, delayMillis = 80)) + slideInVertically(tween(280, delayMillis = 80)) { it / 24 } },
                            exitTransition = { fadeOut(tween(80)) },
                            popEnterTransition = { fadeIn(tween(240, delayMillis = 80)) + slideInVertically(tween(280, delayMillis = 80)) { it / 24 } },
                            popExitTransition = { fadeOut(tween(80)) },
                        ) {
                            composable(AppTab.Home.route) {
                                HomeRoute(
                                    onOpenSchedule = {
                                        agendaPage = AGENDA_SCHEDULE
                                        navController.navigateToTab(AppTab.Agenda)
                                    },
                                    onOpenHomework = {
                                        agendaPage = AGENDA_HOMEWORK
                                        navController.navigateToTab(AppTab.Agenda)
                                    },
                                    onOpenGrades = { navController.navigateToTab(AppTab.Grades) },
                                    onOpenMessages = { navController.navigateToTab(AppTab.Messages) },
                                    onSelectChild = accountActions.onSelectStudent,
                                    onOpenSearch = { navController.navigate(SEARCH_ROUTE) { launchSingleTop = true } },
                                )
                            }
                            composable(AppTab.Agenda.route) { AgendaScreen(agendaPage) { agendaPage = it } }
                            composable(AppTab.Grades.route) { GradesRoute(onOpenMethod = { navController.navigate(METHOD_ROUTE) { launchSingleTop = true } }) }
                            composable(METHOD_ROUTE) { AverageMethodScreen(onClose = { navController.popBackStack() }) }
                            composable(AppTab.Messages.route) { MessagesRoute() }
                            composable(SEARCH_ROUTE) {
                                // Un résultat ouvre l'onglet concerné (même chemin qu'un raccourci ou une notification).
                                SearchRoute(onClose = { navController.popBackStack() }, onOpen = { container.pendingTab.value = it.tab })
                            }
                            composable(AppTab.More.route) {
                                MoreScreen(
                                    onOpenSearch = { navController.navigate(SEARCH_ROUTE) { launchSingleTop = true } },
                                    onOpenMethod = { navController.navigate(METHOD_ROUTE) { launchSingleTop = true } },
                                    profile = activeProfile,
                                    connection = connection,
                                    connectionMessage = connectionMessage,
                                    onOpenAccounts = { showAccounts = true },
                                    onRetryConnection = onRetryConnection,
                                )
                            }
                        }
                    }
                    Row(
                        Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PrivacyToggle()
                        AccountButton(profile = activeProfile, onClick = { showAccounts = true })
                    }
                }
            }
        }
    }
    if (showAccounts) {
        AccountSheet(accounts = accounts, actions = accountActions, onDismiss = { showAccounts = false })
    }
}
