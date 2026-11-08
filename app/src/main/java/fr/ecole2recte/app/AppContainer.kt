package fr.ecole2recte.app

import android.app.Application
import android.content.Context
import java.io.File
import fr.ecole2recte.app.data.CredentialStore
import fr.ecole2recte.app.data.HomeworkStatusStore
import fr.ecole2recte.app.data.HomeworkFilterStore
import fr.ecole2recte.app.data.MessageReadMarks
import fr.ecole2recte.app.data.PrefsHomeworkStatusStore
import fr.ecole2recte.app.data.ProfileKeyValueStore
import fr.ecole2recte.app.data.SeenGrades
import fr.ecole2recte.app.data.KeystoreCipher
import fr.ecole2recte.app.data.PrefsTextStorage
import fr.ecole2recte.app.data.PrefsTokenLog
import fr.ecole2recte.app.data.TokenLifetimeLog
import fr.ecole2recte.app.data.ProfileRepositories
import fr.ecole2recte.app.data.SubjectColorStore
import fr.ecole2recte.app.data.cache.FileResponseCache
import fr.ecole2recte.app.data.cache.ResponseCache
import fr.ecole2recte.app.settings.AppSettings
import fr.ecole2recte.app.sync.SyncPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import fr.ecole2recte.app.data.SecureCredentialStore
import fr.ecole2recte.app.data.EcoleDirecteRepository
import fr.ecole2recte.app.data.demo.DemoSchoolRepository
import fr.ecole2recte.app.domain.SchoolRepository
import fr.ecole2recte.app.session.ActiveProfile
import fr.ecole2recte.app.session.ApiSession

/** Point unique où les dépendances sont assemblées (injection à la main, sans framework). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val credentialStore: CredentialStore = SecureCredentialStore(context)
    val settings: AppSettings = AppSettings(context)
    val syncPreferences = SyncPreferences(context)

    /** Les dernières synchronisations (heure, durée, résultat), sans donnée personnelle. */
    val syncJournal = fr.ecole2recte.app.sync.SyncJournal(fr.ecole2recte.app.sync.PrefsJournalStorage(context))

    /** Le dernier plantage, gardé sur le téléphone pour pouvoir le signaler. */
    val crashLog = fr.ecole2recte.app.diagnostic.CrashLog(File(context.filesDir, "last_crash.txt"))

    /** Vrai quand l'utilisateur rejoue la présentation depuis Plus > À propos. */
    val replayOnboarding = MutableStateFlow(false)

    /** Onglet demandé par un raccourci, une notification ou un widget ; consommé par l'écran principal. */
    val pendingTab = MutableStateFlow<String?>(null)
    val pendingWidgetSubject = MutableStateFlow<Pair<String, String>?>(null)
    val pendingWidgetProfile = MutableStateFlow<String?>(null)
    val pendingSubject = MutableStateFlow<String?>(null)

    fun clearLocalFeatures(identifier: String? = null) {
        identifier?.let { fr.ecole2recte.app.sync.AppShortcuts.visits(appContext).clear(it) }
        identifier?.let { fr.ecole2recte.app.widget.SchoolLifeSnapshotStore(appContext).forgetAccount(it) }
        val owner = fr.ecole2recte.app.sync.LocalFeaturePreferences(appContext).activeOwner
        if (identifier != null && owner != null && !owner.startsWith("$identifier/", ignoreCase = true)) return
        fr.ecole2recte.app.sync.LocalFeaturePreferences(appContext).activeOwner = null
        fr.ecole2recte.app.sync.AppShortcuts.clear(appContext)
        fr.ecole2recte.app.sync.WidgetSnapshotStore(appContext).clear()
        fr.ecole2recte.app.widget.SchoolLifeSnapshotStore(appContext).clear()
        fr.ecole2recte.app.sync.CurrentCourseScheduler.refresh(appContext)
        fr.ecole2recte.app.sync.AbsenceReminderScheduler.refresh(appContext)
        fr.ecole2recte.app.widget.WidgetRefresh.requestAll(appContext)
    }

    fun useLocalProfile(profile: ActiveProfile) {
        fr.ecole2recte.app.sync.LocalFeaturePreferences(appContext).activeOwner = profile.key
        val snapshotStore = fr.ecole2recte.app.sync.WidgetSnapshotStore(appContext)
        val lifeStore = fr.ecole2recte.app.widget.SchoolLifeSnapshotStore(appContext)
        if (snapshotStore.read()?.owner != profile.key) snapshotStore.clear()
        snapshotStore.read()?.let { snapshot ->
            snapshotStore.write(snapshot.copy(
                shortcutSubjects = if (fr.ecole2recte.app.data.SchoolModule.Grades in profile.closedModules) emptyList() else snapshot.shortcutSubjects,
                lessons = if (fr.ecole2recte.app.data.SchoolModule.Schedule in profile.closedModules) emptyList() else snapshot.lessons,
            ))
        }
        if (lifeStore.owner != profile.key || fr.ecole2recte.app.data.SchoolModule.SchoolLife in profile.closedModules) lifeStore.clear()
        fr.ecole2recte.app.sync.AppShortcuts.refresh(appContext)
        fr.ecole2recte.app.sync.CurrentCourseScheduler.refresh(appContext)
        fr.ecole2recte.app.sync.AbsenceReminderScheduler.refresh(appContext)
        fr.ecole2recte.app.widget.WidgetRefresh.requestAll(appContext)
    }

    /** Durées de vie du jeton mesurées sur ce téléphone. */
    val tokenLog: TokenLifetimeLog = PrefsTokenLog(context)

    /** Les données du profil actif. */
    val apiSession = ApiSession(tokenLog = tokenLog)
    val subjectColors = SubjectColorStore(PrefsTextStorage(context, "ecole2recte_colors", "overrides"))
    val homeworkStatus: HomeworkStatusStore = PrefsHomeworkStatusStore(context)

    /** « Lu » / « non lu » choisis dans la liste des messages : locaux, jamais écrits chez ÉcoleDirecte. */
    val messageMarks = MessageReadMarks(ProfileKeyValueStore(PrefsTextStorage(context, "ecole2recte_local", "message_marks")))

    /** Filtre choisi dans la liste des devoirs, par profil. */
    val homeworkFilter = HomeworkFilterStore(ProfileKeyValueStore(PrefsTextStorage(context, "ecole2recte_local", "homework_filter")))

    /** Notes déjà vues par l'application (pastille « Nouveau »), par profil. */
    val seenGrades = SeenGrades(ProfileKeyValueStore(PrefsTextStorage(context, "ecole2recte_local", "seen_grades")))
    val responseCache: ResponseCache = FileResponseCache(File(context.filesDir, "cache"), KeystoreCipher())

    /** Un seul dépôt par profil, partagé par tous les écrans. */
    private val repositories = ProfileRepositories { profile ->
        EcoleDirecteRepository(apiSession, profile, responseCache, homeworkStatus, writes = settings.writeAccess)
    }

    fun schoolRepositoryFor(profile: ActiveProfile?): SchoolRepository =
        if (profile == null) DemoSchoolRepository() else repositories.get(profile)

    fun forgetRepositories(identifier: String) = repositories.forget(identifier)
}

class Ecole2recteApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { container.crashLog.record(error) }
            previous?.uncaughtException(thread, error)
        }
        // Les pièces jointes ouvertes lors d'un lancement précédent ne restent pas sur le téléphone.
        fr.ecole2recte.app.ui.common.AttachmentFiles.at(this).clear()
        fr.ecole2recte.app.export.ExportFiles.at(this).clear()
    }
}
