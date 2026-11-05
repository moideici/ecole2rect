package fr.ecole2recte.app

import android.app.Application
import android.content.Context
import java.io.File
import fr.ecole2recte.app.data.CredentialStore
import fr.ecole2recte.app.data.HomeworkStatusStore
import fr.ecole2recte.app.data.PrefsHomeworkStatusStore
import fr.ecole2recte.app.data.PrefsTextStorage
import fr.ecole2recte.app.data.SubjectColorStore
import fr.ecole2recte.app.data.cache.FileResponseCache
import fr.ecole2recte.app.data.cache.ResponseCache
import fr.ecole2recte.app.settings.AppSettings
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

    /** Les données du profil actif. */
    val apiSession = ApiSession()
    val subjectColors = SubjectColorStore(PrefsTextStorage(context, "ecole2recte_colors", "overrides"))
    val homeworkStatus: HomeworkStatusStore = PrefsHomeworkStatusStore(context)
    val responseCache: ResponseCache = FileResponseCache(File(context.filesDir, "cache"))

    fun schoolRepositoryFor(profile: ActiveProfile?): SchoolRepository =
        if (profile == null) DemoSchoolRepository() else EcoleDirecteRepository(apiSession, profile, responseCache, homeworkStatus)
}

class Ecole2recteApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
