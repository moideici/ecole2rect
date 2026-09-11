package fr.ecole2recte.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.animation.Crossfade
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.AttachmentState
import fr.ecole2recte.app.MainViewModel
import fr.ecole2recte.app.core.model.DataSection
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.repo.AuthState
import fr.ecole2recte.app.data.AttachmentOpener
import fr.ecole2recte.app.ui.theme.Ecole2recteTheme
import java.time.LocalDate

/** Les cinq pages principales : chacune est complète, aucune n'est un aperçu. */
enum class Destination(val label: String, val icon: ImageVector) {
    HOME("Accueil", Icons.Outlined.Home),
    TIMETABLE("Planning", Icons.Outlined.CalendarMonth),
    HOMEWORK("Devoirs", Icons.AutoMirrored.Outlined.Assignment),
    GRADES("Notes", Icons.AutoMirrored.Outlined.ShowChart),
    MESSAGES("Messages", Icons.Outlined.ChatBubbleOutline),
}

/**
 * Une demande de navigation venue de l'extérieur — un widget d'écran d'accueil touché.
 * Le compteur rend deux demandes identiques distinctes, sinon revenir sur la même page
 * après l'avoir quittée ne déclencherait rien.
 */
data class NavigationRequest(val destination: Destination, val nonce: Long)

/** Les vues secondaires, ouvertes par-dessus une page et fermées par le retour arrière. */
sealed interface Detail {
    data class Homework(val id: String) : Detail
    data class Lesson(val id: String) : Detail
    data class Message(val id: String) : Detail
    data object Settings : Detail
    data object HomeEditor : Detail
    data object SubjectColors : Detail
    data object WidgetColors : Detail
    data object SchoolLife : Detail
    data object Teachers : Detail
    data object Stats : Detail
    data class Teacher(val name: String) : Detail
    data object Documents : Detail
    data object News : Detail
    data object Textbooks : Detail
    data object Cloud : Detail
    data object Search : Detail
    data object ErrorLog : Detail
}

/**
 * Les fiches ouvertes, empilées.
 *
 * Un emplacement unique suffisait tant qu'une fiche s'ouvrait depuis une page principale.
 * Il ne suffit plus : Réglages → Couleurs des matières → Retour ramenait à l'accueil au lieu
 * des Réglages, et Professeurs → fiche → devoir écrasait chaque niveau au passage. Une pile
 * rend au bouton retour ce qu'il promet : un cran, pas la sortie.
 *
 * Elle est sauvegardée, aussi : les fiches ne portent que des chaînes, donc une rotation ou
 * la mort du processus en arrière-plan ne referme plus le message qu'on était en train de
 * lire — ce qui arrivait à chaque fois.
 */
@Stable
class DetailStack(initial: List<Detail> = emptyList()) {

    private val stack = mutableStateListOf<Detail>().apply { addAll(initial) }

    val current: Detail? get() = stack.lastOrNull()

    /** Ouvrir deux fois la même fiche n'ajoute pas un cran à remonter. */
    fun push(detail: Detail) {
        if (stack.lastOrNull() != detail) stack += detail
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    fun clear() = stack.clear()

    companion object {
        // listSaver rend un Saver<Original, Any> : l'annoter autrement ne compile pas.
        val Saver: Saver<DetailStack, Any> = listSaver<DetailStack, String>(
            save = { state -> state.stack.map { detail -> encode(detail) } },
            restore = { saved -> DetailStack(saved.mapNotNull { value -> decode(value) }) },
        )

        private fun encode(detail: Detail): String = when (detail) {
            is Detail.Homework -> "devoir|${detail.id}"
            is Detail.Lesson -> "cours|${detail.id}"
            is Detail.Message -> "message|${detail.id}"
            is Detail.Teacher -> "professeur|${detail.name}"
            Detail.Settings -> "reglages"
            Detail.HomeEditor -> "accueil"
            Detail.SubjectColors -> "couleurs-matieres"
            Detail.WidgetColors -> "couleurs-widgets"
            Detail.SchoolLife -> "vie-scolaire"
            Detail.Teachers -> "professeurs"
            Detail.Stats -> "chiffres"
            Detail.Documents -> "documents"
            Detail.News -> "actualites"
            Detail.Textbooks -> "manuels"
            Detail.Cloud -> "stockage"
            Detail.Search -> "recherche"
            Detail.ErrorLog -> "diagnostic"
        }

        private fun decode(value: String): Detail? {
            val kind = value.substringBefore('|')
            val argument = value.substringAfter('|', "")
            return when (kind) {
                "devoir" -> Detail.Homework(argument)
                "cours" -> Detail.Lesson(argument)
                "message" -> Detail.Message(argument)
                "professeur" -> Detail.Teacher(argument)
                "reglages" -> Detail.Settings
                "accueil" -> Detail.HomeEditor
                "couleurs-matieres" -> Detail.SubjectColors
                "couleurs-widgets" -> Detail.WidgetColors
                "vie-scolaire" -> Detail.SchoolLife
                "professeurs" -> Detail.Teachers
                "chiffres" -> Detail.Stats
                "documents" -> Detail.Documents
                "actualites" -> Detail.News
                "manuels" -> Detail.Textbooks
                "stockage" -> Detail.Cloud
                "recherche" -> Detail.Search
                "diagnostic" -> Detail.ErrorLog
                // Une entrée illisible — version antérieure, état tronqué — est ignorée
                // plutôt que de faire échouer la restauration de toute la pile.
                else -> null
            }
        }
    }
}

@Composable
fun Ecole2recteApp(viewModel: MainViewModel, request: NavigationRequest? = null) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val auth by viewModel.auth.collectAsStateWithLifecycle()

    // Le déverrouillage vaut pour la session : on ne redemande pas l'empreinte à chaque
    // retour d'un autre écran, ce qui rendrait le verrou insupportable et donc désactivé.
    var unlocked by rememberSaveable { mutableStateOf(false) }

    Ecole2recteTheme(settings.theme, settings.dynamicColor) {
        if (settings.biometricLock && !unlocked) {
            BiometricGate(onUnlocked = { unlocked = true })
            return@Ecole2recteTheme
        }
        when (auth) {
            // Le tout premier instant, avant même d'avoir relu le disque : un écran vide
            // clignoterait, une marque calme vaut mieux.
            AuthState.Unknown -> SplashScreen()
            // Reconnexion silencieuse au lancement : inutile de faire apparaître un
            // formulaire de connexion que l'utilisateur n'aura pas le temps de lire.
            is AuthState.Connecting -> if ((auth as AuthState.Connecting).silent) {
                SplashScreen()
            } else {
                LoginRoute(viewModel)
            }

            is AuthState.SignedIn -> SignedInScaffold(viewModel, request)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignedInScaffold(viewModel: MainViewModel, request: NavigationRequest?) {
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    val details = rememberSaveable(saver = DetailStack.Saver) { DetailStack() }
    val detail = details.current

    // L'emploi du temps garde son jour ici, au-dessus de la fiche de détail : ouvrir un
    // devoir depuis mardi puis revenir doit ramener sur mardi, pas sur aujourd'hui.
    val timetable = rememberTimetableState(LocalDate.now())

    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val auth by viewModel.auth.collectAsStateWithLifecycle()
    val attachment by viewModel.attachment.collectAsStateWithLifecycle()
    val messagesComplete by viewModel.messagesComplete.collectAsStateWithLifecycle()
    val loadingWeeks by viewModel.loadingWeeks.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMoreMessages.collectAsStateWithLifecycle()
    val errors by viewModel.errorLog.collectAsStateWithLifecycle()
    val autoAnswer by viewModel.autoAnswer.collectAsStateWithLifecycle()
    val messageFolder by viewModel.messageFolder.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    // Cocher un devoir est le geste le plus répété de l'application : une brève vibration
    // confirme qu'il a été pris en compte sans qu'on ait à vérifier des yeux.
    val toggleHomework: (HomeworkItem) -> Unit = { item ->
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.toggleHomework(item)
    }

    // Le nombre de messages non lus, tel qu'il apparaît sur l'onglet.
    val unreadMessages = snapshot.inbox.count { !settings.isMessageRead(it.id, it.read) }

    // Une pièce jointe prête part vers l'application capable de l'ouvrir, puis l'état est
    // remis à zéro : sans quoi revenir sur la fiche la rouvrirait toute seule.
    LaunchedEffect(attachment) {
        val ready = attachment as? AttachmentState.Ready ?: return@LaunchedEffect
        val intent = AttachmentOpener(context).viewIntent(ready.uri, ready.name)
        runCatching { context.startActivity(intent) }
        viewModel.clearAttachmentState()
    }

    // Un widget d'écran d'accueil ouvre directement sa page.
    LaunchedEffect(request) {
        request?.let {
            destination = it.destination
            details.clear()
            // Un widget parle d'aujourd'hui : le toucher ramène sur aujourd'hui, même si
            // l'écran était resté sur la semaine prochaine.
            if (it.destination == Destination.TIMETABLE) timetable.showDay(LocalDate.now())
        }
    }

    // Un module facultatif n'est demandé qu'à l'ouverture de son écran, et une seule fois :
    // s'il a déjà répondu, le cache suffit jusqu'à la prochaine synchronisation complète.
    LaunchedEffect(detail) {
        when (detail) {
            Detail.News -> viewModel.loadModule(DataSection.NEWS)
            Detail.Textbooks -> viewModel.loadModule(DataSection.TEXTBOOKS)
            Detail.Cloud -> viewModel.loadModule(DataSection.CLOUD)
            else -> Unit
        }
    }

    BackHandler(enabled = detail != null) { details.pop() }
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
                            details.clear()
                        },
                        icon = {
                            // Les messages non lus sont la seule information qui mérite de
                            // remonter jusqu'à la barre : les devoirs ont déjà leur écran.
                            if (entry == Destination.MESSAGES && unreadMessages > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge {
                                            Text(if (unreadMessages > 99) "99+" else "$unreadMessages")
                                        }
                                    },
                                ) {
                                    Icon(entry.icon, contentDescription = null)
                                }
                            } else {
                                Icon(entry.icon, contentDescription = null)
                            }
                        },
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
            val openDetail: (Detail) -> Unit = { details.push(it) }
            // Ouvrir un devoir, c'est aussi charger son contenu : la liste ne le porte pas.
            val openHomework: (HomeworkItem) -> Unit = { item ->
                viewModel.loadHomeworkDetails(item.dueDate)
                openDetail(Detail.Homework(item.id))
            }
            when (val current = detail) {
                // Tirer vers le bas synchronise : le geste attendu partout ailleurs.
                null -> PullToRefreshBox(
                    isRefreshing = sync.running,
                    onRefresh = { viewModel.refreshEverything() },
                ) {
                    // Un fondu court entre les pages : le changement d'onglet cesse d'être un
                    // clignotement, sans jamais retarder l'affichage de ce qu'on est venu voir.
                    Crossfade(targetState = destination, label = "page") { page ->
                    when (page) {
                    Destination.HOME -> HomeScreen(
                        snapshot = snapshot,
                        settings = settings,
                        sync = sync,
                        now = now,
                        accounts = (auth as? AuthState.SignedIn)?.accounts.orEmpty(),
                        onNavigate = { destination = it },
                        onOpenDetail = openDetail,
                        onRefresh = { viewModel.refreshEverything() },
                        onToggleHomework = toggleHomework,
                        onSelectAccount = viewModel::selectAccount,
                    )

                    Destination.TIMETABLE -> TimetableScreen(
                        state = timetable,
                        snapshot = snapshot,
                        settings = settings,
                        sync = sync,
                        now = now,
                        loadingWeeks = loadingWeeks,
                        onLoadWeek = viewModel::loadWeek,
                        onOpenLesson = { lesson ->
                            // Le contenu de séance n'est pas dans la liste : il arrive avec
                            // le détail du jour, qu'on demande à l'ouverture de la fiche.
                            viewModel.loadHomeworkDetails(lesson.date)
                            openDetail(Detail.Lesson(lesson.id))
                        },
                        onOpenHomework = openHomework,
                        onRefreshWeek = viewModel::refreshWeek,
                        onSearch = { openDetail(Detail.Search) },
                    )

                    Destination.HOMEWORK -> HomeworkScreen(
                        snapshot = snapshot,
                        settings = settings,
                        sync = sync,
                        now = now,
                        onOpen = openHomework,
                        onToggle = toggleHomework,
                        onRefresh = { viewModel.refresh(setOf(DataSection.HOMEWORK)) },
                        onSearch = { openDetail(Detail.Search) },
                    )

                    Destination.GRADES -> GradesScreen(
                        snapshot = snapshot,
                        settings = settings,
                        sync = sync,
                        now = now,
                        onAverageTarget = viewModel::setAverageTarget,
                        onRefresh = { viewModel.refresh(setOf(DataSection.GRADES)) },
                        onSearch = { openDetail(Detail.Search) },
                    )

                    Destination.MESSAGES -> MessagesScreen(
                        snapshot = snapshot,
                        settings = settings,
                        sync = sync,
                        now = now,
                        folder = messageFolder,
                        onSelectFolder = viewModel::selectMessageFolder,
                        onOpen = { message ->
                            viewModel.openMessage(message)
                            openDetail(Detail.Message(message.id))
                        },
                        onBin = viewModel::binMessage,
                        onLoadMore = viewModel::loadMoreMessages,
                        canLoadMore = !messagesComplete &&
                            snapshot.messages.any { it.folder == messageFolder },
                        loadingMore = loadingMore,
                        onRefresh = { viewModel.refresh(setOf(DataSection.MESSAGES)) },
                        onSearch = { openDetail(Detail.Search) },
                    )
                    }
                    }
                }

                is Detail.Homework -> HomeworkDetailScreen(
                    item = snapshot.homework.firstOrNull { it.id == current.id },
                    settings = settings,
                    parentView = snapshot.account?.viewedByFamily == true,
                    attachmentState = attachment,
                    onToggle = toggleHomework,
                    onOpenAttachment = viewModel::openAttachment,
                    onClose = { details.pop() },
                )

                is Detail.Lesson -> LessonDetailScreen(
                    lesson = snapshot.lessons.firstOrNull { it.id == current.id },
                    settings = settings,
                    homework = snapshot.lessons.firstOrNull { it.id == current.id }
                        ?.let { lesson ->
                            fr.ecole2recte.app.core.repo.Insights.homeworkForLesson(snapshot.homework, lesson)
                        }
                        .orEmpty(),
                    sessions = snapshot.lessons.firstOrNull { it.id == current.id }
                        ?.let { lesson ->
                            snapshot.sessionContents.filter {
                                it.date == lesson.date &&
                                    fr.ecole2recte.app.core.format.Text.normalizeKey(it.subject) ==
                                    fr.ecole2recte.app.core.format.Text.normalizeKey(lesson.subject)
                            }
                        }
                        .orEmpty(),
                    onOpenHomework = openHomework,
                    onOpenTeacher = { openDetail(Detail.Teacher(it)) },
                    onClose = { details.pop() },
                )

                is Detail.Message -> MessageDetailScreen(
                    message = snapshot.messages.firstOrNull { it.id == current.id },
                    attachmentState = attachment,
                    onOpenAttachment = viewModel::openAttachment,
                    onClose = { details.pop() },
                )

                Detail.Stats -> StatsScreen(
                    snapshot = snapshot,
                    settings = settings,
                    today = now.toLocalDate(),
                    onClose = { details.pop() },
                )

                Detail.Teachers -> TeachersScreen(
                    snapshot = snapshot,
                    settings = settings,
                    today = now.toLocalDate(),
                    onOpenTeacher = { openDetail(Detail.Teacher(it)) },
                    onClose = { details.pop() },
                )

                is Detail.Teacher -> TeacherDetailScreen(
                    name = current.name,
                    snapshot = snapshot,
                    settings = settings,
                    today = now.toLocalDate(),
                    onOpenHomework = openHomework,
                    onClose = { details.pop() },
                )

                Detail.Settings -> SettingsScreen(
                    auth = auth,
                    snapshot = snapshot,
                    settings = settings,
                    sync = sync,
                    autoAnswer = autoAnswer,
                    onClose = { details.pop() },
                    onOpenDetail = openDetail,
                    onTheme = viewModel::setTheme,
                    onSelectAccount = viewModel::selectAccount,
                    onBirthDate = viewModel::setBirthDate,
                    onForgetAnswer = viewModel::forgetAnswer,
                    onDiagnostics = viewModel::setDiagnosticsVisible,
                    onNotifications = viewModel::setNotifications,
                    onBiometricLock = viewModel::setBiometricLock,
                    onDynamicColor = viewModel::setDynamicColor,
                    onAverageTarget = viewModel::setAverageTarget,
                    onRefresh = { viewModel.refresh() },
                    onSignOut = viewModel::signOut,
                )

                Detail.HomeEditor -> HomeEditorScreen(
                    settings = settings,
                    onMove = viewModel::moveWidget,
                    onToggle = viewModel::toggleWidget,
                    onClose = { details.pop() },
                )

                Detail.Search -> SearchScreen(
                    snapshot = snapshot,
                    settings = settings,
                    today = now.toLocalDate(),
                    onOpen = { hit ->
                        when (hit.kind) {
                            fr.ecole2recte.app.core.repo.Search.HitKind.LESSON ->
                                details.push(Detail.Lesson(hit.id))

                            fr.ecole2recte.app.core.repo.Search.HitKind.HOMEWORK -> {
                                snapshot.homework.firstOrNull { it.id == hit.id }?.let(openHomework)
                            }

                            fr.ecole2recte.app.core.repo.Search.HitKind.MESSAGE -> {
                                snapshot.messages.firstOrNull { it.id == hit.id }?.let { message ->
                                    viewModel.openMessage(message)
                                    details.push(Detail.Message(message.id))
                                }
                            }

                            fr.ecole2recte.app.core.repo.Search.HitKind.GRADE -> {
                                details.clear()
                                destination = Destination.GRADES
                            }

                            fr.ecole2recte.app.core.repo.Search.HitKind.SCHOOL_LIFE ->
                                details.push(Detail.SchoolLife)
                        }
                    },
                    onClose = { details.pop() },
                )

                Detail.ErrorLog -> ErrorLogScreen(
                    entries = errors,
                    onClear = viewModel::clearErrorLog,
                    onClose = { details.pop() },
                )

                Detail.SchoolLife -> SchoolLifeScreen(
                    snapshot = snapshot,
                    onClose = { details.pop() },
                )

                Detail.Documents -> DocumentsScreen(
                    snapshot = snapshot,
                    attachmentState = attachment,
                    onOpen = { document -> viewModel.openAttachment(document.asAttachment()) },
                    onClose = { details.pop() },
                )

                Detail.News -> NewsScreen(
                    snapshot = snapshot,
                    attachmentState = attachment,
                    onOpenAttachment = viewModel::openAttachment,
                    onRefresh = { viewModel.refresh(setOf(DataSection.NEWS)) },
                    onClose = { details.pop() },
                )

                Detail.Textbooks -> TextbooksScreen(
                    snapshot = snapshot,
                    settings = settings,
                    // Un manuel numérique s'ouvre chez son éditeur : l'application n'a pas à
                    // s'interposer entre l'élève et un site conçu pour être lu au navigateur.
                    onOpen = { book ->
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(book.url),
                                ),
                            )
                        }
                    },
                    onRefresh = { viewModel.refresh(setOf(DataSection.TEXTBOOKS)) },
                    onClose = { details.pop() },
                )

                Detail.Cloud -> CloudScreen(
                    snapshot = snapshot,
                    attachmentState = attachment,
                    onOpen = { file -> viewModel.openAttachment(file.asAttachment()) },
                    onRefresh = { viewModel.refresh(setOf(DataSection.CLOUD)) },
                    onClose = { details.pop() },
                )

                Detail.WidgetColors -> WidgetColorsScreen(
                    settings = settings,
                    onChange = viewModel::setWidgetColors,
                    onClose = { details.pop() },
                )

                Detail.SubjectColors -> SubjectColorsScreen(
                    snapshot = snapshot,
                    settings = settings,
                    onPick = viewModel::setSubjectColor,
                    onClose = { details.pop() },
                )
            }
        }
    }
}
