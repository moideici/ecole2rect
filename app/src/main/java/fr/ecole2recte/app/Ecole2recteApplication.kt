package fr.ecole2recte.app

import android.app.Application
import fr.ecole2recte.app.data.AppContainer

class Ecole2recteApplication : Application() {

    /** Créé à la demande : le premier écran n'attend pas la construction du graphe. */
    val container: AppContainer by lazy { AppContainer(this) }
}
