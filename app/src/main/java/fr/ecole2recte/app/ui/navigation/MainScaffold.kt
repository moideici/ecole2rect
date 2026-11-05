package fr.ecole2recte.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import fr.ecole2recte.app.session.AccountSummary
import fr.ecole2recte.app.session.ActiveProfile
import fr.ecole2recte.app.session.ConnectionStatus
import fr.ecole2recte.app.ui.accounts.AccountActions
import fr.ecole2recte.app.ui.accounts.AccountButton
import fr.ecole2recte.app.ui.accounts.AccountSheet
import fr.ecole2recte.app.ui.common.ConnectionBanner
import fr.ecole2recte.app.ui.common.LocalActiveProfile
import fr.ecole2recte.app.ui.grades.GradesRoute
import fr.ecole2recte.app.ui.home.HomeRoute
import fr.ecole2recte.app.ui.homework.HomeworkRoute
import fr.ecole2recte.app.ui.messages.MessagesRoute
import fr.ecole2recte.app.ui.schedule.ScheduleRoute
import fr.ecole2recte.app.ui.theme.AppTheme

/** Onglets de la barre du bas. Le lot 4 redistribuera leur contenu. */
internal enum class AppTab(val route: String, val label: String, val icon: ImageVector) {
    Home("accueil", "Accueil", Icons.Outlined.Home),
    Schedule("planning", "Planning", Icons.Outlined.CalendarMonth),
    Homework("devoirs", "Devoirs", Icons.AutoMirrored.Outlined.Assignment),
    Grades("notes", "Notes", Icons.AutoMirrored.Outlined.MenuBook),
    Messages("messages", "Messages", Icons.Outlined.ChatBubbleOutline),
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
    key(activeProfile?.key) {
        CompositionLocalProvider(LocalActiveProfile provides activeProfile) {
            MainContent(connection, connectionMessage, onRetryConnection, accounts, activeProfile, accountActions)
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
    Scaffold(
        containerColor = AppTheme.colors.background,
        bottomBar = {
            NavigationBar(containerColor = AppTheme.colors.surface) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = { navController.navigateToTab(tab) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
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
            Box(Modifier.fillMaxSize()) {
                Surface(modifier = Modifier.fillMaxSize(), color = AppTheme.colors.background) {
                    NavHost(navController = navController, startDestination = AppTab.Home.route) {
                        composable(AppTab.Home.route) {
                            HomeRoute(
                                onOpenSchedule = { navController.navigateToTab(AppTab.Schedule) },
                                onOpenHomework = { navController.navigateToTab(AppTab.Homework) },
                                onOpenGrades = { navController.navigateToTab(AppTab.Grades) },
                            )
                        }
                        composable(AppTab.Schedule.route) { ScheduleRoute() }
                        composable(AppTab.Homework.route) { HomeworkRoute() }
                        composable(AppTab.Grades.route) { GradesRoute() }
                        composable(AppTab.Messages.route) { MessagesRoute() }
                    }
                }
                AccountButton(
                    profile = activeProfile,
                    onClick = { showAccounts = true },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 18.dp),
                )
            }
        }
    }
    if (showAccounts) {
        AccountSheet(accounts = accounts, actions = accountActions, onDismiss = { showAccounts = false })
    }
}
