package fr.ecole2recte.app

import android.app.Application
import android.content.Context
import fr.ecole2recte.app.data.CredentialStore
import fr.ecole2recte.app.data.SecureCredentialStore
import fr.ecole2recte.app.data.demo.DemoSchoolRepository
import fr.ecole2recte.app.domain.SchoolRepository
import fr.ecole2recte.app.session.ActiveProfile

/** Point unique où les dépendances sont assemblées (injection à la main, sans framework). */
class AppContainer(context: Context) {
    val credentialStore: CredentialStore = SecureCredentialStore(context)

    /** Données du profil actif. Aujourd'hui de la démonstration personnalisée au prénom du compte. */
    fun schoolRepositoryFor(profile: ActiveProfile?): SchoolRepository = DemoSchoolRepository(profile?.firstName)
}

class Ecole2recteApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
