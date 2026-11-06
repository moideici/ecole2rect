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
    val credentialStore: CredentialStore = SecureCredentialStore(context)
    val settings: AppSettings = AppSettings(context)
    val syncPreferences = SyncPreferences(context)

    /** Le dernier plantage, gardé sur le téléphone pour pouvoir le signaler. */
    val crashLog = fr.ecole2recte.app.diagnostic.CrashLog(File(context.filesDir, "last_crash.txt"))

    /** Onglet demandé par un raccourci, une notification ou un widget ; consommé par l'écran principal. */
    val pendingTab = MutableStateFlow<String?>(null)

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
