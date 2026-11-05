package fr.ecole2recte.app

import android.app.Application
import android.content.Context
import java.io.File
import fr.ecole2recte.app.data.CredentialStore
import fr.ecole2recte.app.data.HomeworkStatusStore
import fr.ecole2recte.app.data.PrefsHomeworkStatusStore
import fr.ecole2recte.app.data.PrefsTextStorage
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

    /** Onglet demandé par un raccourci, une notification ou un widget ; consommé par l'écran principal. */
    val pendingTab = MutableStateFlow<String?>(null)

    /** Les données du profil actif. */
    val apiSession = ApiSession()
    val subjectColors = SubjectColorStore(PrefsTextStorage(context, "ecole2recte_colors", "overrides"))
    val homeworkStatus: HomeworkStatusStore = PrefsHomeworkStatusStore(context)
    val responseCache: ResponseCache = FileResponseCache(File(context.filesDir, "cache"))

    /** Un seul dépôt par profil, partagé par tous les écrans. */
    private val repositories = ProfileRepositories { profile ->
        EcoleDirecteRepository(apiSession, profile, responseCache, homeworkStatus)
    }

    fun schoolRepositoryFor(profile: ActiveProfile?): SchoolRepository =
        if (profile == null) DemoSchoolRepository() else repositories.get(profile)

    fun forgetRepositories(identifier: String) = repositories.forget(identifier)
}

class Ecole2recteApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
