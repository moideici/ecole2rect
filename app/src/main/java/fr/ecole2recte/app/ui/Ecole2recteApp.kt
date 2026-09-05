package fr.ecole2recte.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.MainViewModel
import fr.ecole2recte.app.core.model.DataSection
import fr.ecole2recte.app.core.repo.AuthState
import fr.ecole2recte.app.ui.theme.Ecole2recteTheme

/** Les cinq pages principales : chacune est complète, aucune n'est un aperçu. */
enum class Destination(val label: String, val icon: ImageVector) {
    HOME("Accueil", Icons.Outlined.Home),
    TIMETABLE("Planning", Icons.Outlined.CalendarMonth),
    HOMEWORK("Devoirs", Icons.Outlined.Assignment),
    GRADES("Notes", Icons.Outlined.ShowChart),
    MESSAGES("Messages", Icons.Outlined.ChatBubbleOutline),
}

/** Les vues secondaires, ouvertes par-dessus une page et fermées par le retour arrière. */
sealed interface Detail {
    data class Homework(val id: String) : Detail
    data class Message(val id: String) : Detail
    data object Settings : Detail
    data object HomeEditor : Detail
    data object SubjectColors : Detail
}

@Composable
fun Ecole2recteApp(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val auth by viewModel.auth.collectAsStateWithLifecycle()

    Ecole2recteTheme(settings.theme) {
        when (auth) {
            // Le tout premier instant, avant même d'avoir relu le disque : un écran vide
            // clignoterait, une marque calme vaut mieux.
            AuthState.Unknown -> SplashScreen()
            is AuthState.SignedIn -> SignedInScaffold(viewModel)
            else -> LoginRoute(viewModel)
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "ecole2recte",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun LoginRoute(viewModel: MainViewModel) {
    val auth by viewModel.auth.collectAsStateWithLifecycle()
    val form by viewModel.loginForm.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    LoginScreen(
        state = auth,
        form = form,
        suggestedAnswer = viewModel::suggestedAnswer,
        showDiagnostics = settings.showDiagnostics,
        onFormChange = viewModel::onLoginFormChange,
        onSubmit = viewModel::signIn,
        onAnswer = viewModel::answerSecurityQuestion,
        onToggleDiagnostics = viewModel::setDiagnosticsVisible,
    )
}

@Composable
private fun SignedInScaffold(viewModel: MainViewModel) {
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var detail by remember { mutableStateOf<Detail?>(null) }

    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val auth by viewModel.auth.collectAsStateWithLifecycle()

    BackHandler(enabled = detail != null) { detail = null }
    BackHandler(enabled = detail == null && destination != Destination.HOME) {
        destination = Destination.HOME
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Destination.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = destination == entry && detail == null,
                        onClick = {
                            destination = entry
                            detail = null
                        },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(entry.label, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val openDetail: (Detail) -> Unit = { detail = it }
            when (val current = detail) {
                null -> when (destination) {
                    Destination.HOME -> HomeScreen(
                        snapshot = snapshot,
                        settings = settings,
                        sync = sync,
                        now = now,
                        onNavigate = { destination = it },
                        onOpenDetail = openDetail,
                        onRefresh = { viewModel.refresh() },
                        onToggleHomework = viewModel::toggleHomework,
                    )

                    Destination.TIMETABLE -> TimetableScreen(
                        snapshot = snapshot,
                        settings = settings,
                        sync = sync,
                        now = now,
                        onRefresh = { viewModel.refresh(setOf(DataSection.TIMETABLE)) },
                    )

                    Destination.HOMEWORK -> HomeworkScreen(
                        snapshot = snapshot,
                        settings = settings,
                        sync = sync,
                        now = now,
                        onOpen = { item ->
                            viewModel.loadHomeworkDetails(item.dueDate)
                            openDetail(Detail.Homework(item.id))
                        },
                        onToggle = viewModel::toggleHomework,
                        onRefresh = { viewModel.refresh(setOf(DataSection.HOMEWORK)) },
                    )

                    Destination.GRADES -> GradesScreen(
                        snapshot = snapshot,
                        settings = settings,
                        sync = sync,
                        now = now,
                        onRefresh = { viewModel.refresh(setOf(DataSection.GRADES)) },
                    )

                    Destination.MESSAGES -> MessagesScreen(
                        snapshot = snapshot,
                        settings = settings,
                        sync = sync,
                        now = now,
                        onOpen = { message ->
                            viewModel.openMessage(message)
                            openDetail(Detail.Message(message.id))
                        },
                        onRefresh = { viewModel.refresh(setOf(DataSection.MESSAGES)) },
                    )
                }

                is Detail.Homework -> HomeworkDetailScreen(
                    item = snapshot.homework.firstOrNull { it.id == current.id },
                    settings = settings,
                    onToggle = viewModel::toggleHomework,
                    onClose = { detail = null },
                )

                is Detail.Message -> MessageDetailScreen(
                    message = snapshot.messages.firstOrNull { it.id == current.id },
                    onClose = { detail = null },
                )

                Detail.Settings -> SettingsScreen(
                    auth = auth,
                    snapshot = snapshot,
                    settings = settings,
                    sync = sync,
                    onClose = { detail = null },
                    onOpenDetail = openDetail,
                    onTheme = viewModel::setTheme,
                    onSelectAccount = viewModel::selectAccount,
                    onDiagnostics = viewModel::setDiagnosticsVisible,
                    onRefresh = { viewModel.refresh() },
                    onSignOut = viewModel::signOut,
                )

                Detail.HomeEditor -> HomeEditorScreen(
                    settings = settings,
                    onMove = viewModel::moveWidget,
                    onToggle = viewModel::toggleWidget,
                    onClose = { detail = null },
                )

                Detail.SubjectColors -> SubjectColorsScreen(
                    snapshot = snapshot,
                    settings = settings,
                    onPick = viewModel::setSubjectColor,
                    onClose = { detail = null },
                )
            }
        }
    }
}
