package fr.ecole2recte.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import fr.ecole2recte.app.ui.Destination
import fr.ecole2recte.app.ui.Ecole2recteApp
import fr.ecole2recte.app.ui.NavigationRequest

/**
 * `FragmentActivity` et non `ComponentActivity` : `BiometricPrompt` s'attache à un
 * gestionnaire de fragments, et c'est la seule raison de ce choix.
 */
class MainActivity : FragmentActivity() {

    companion object {
        /** Nom d'une [Destination], posé par les widgets d'écran d'accueil. */
        const val EXTRA_DESTINATION = "fr.ecole2recte.app.DESTINATION"
    }

    private val viewModel by viewModels<MainViewModel>()

    /**
     * Écran demandé de l'extérieur. Le compteur distingue deux demandes identiques : toucher
     * deux fois le même widget doit ramener sur la même page, même si on l'a quittée entre-temps.
     */
    private var request by mutableStateOf<NavigationRequest?>(null)
    private var requestCount = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readDestination(intent)
        setContent { Ecole2recteApp(viewModel, request) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readDestination(intent)
    }

    /**
     * Revenir dans l'application est le moment naturel pour rafraîchir : c'est là qu'on
     * regarde son prochain cours. La synchronisation est ignorée si les données sont encore
     * fraîches, pour ne pas marteler une API privée à chaque va-et-vient.
     */
    override fun onResume() {
        super.onResume()
        viewModel.refreshIfStale()
    }

    private fun readDestination(intent: Intent?) {
        val name = intent?.getStringExtra(EXTRA_DESTINATION) ?: return
        val destination = Destination.entries.firstOrNull { it.name == name } ?: return
        requestCount += 1
        request = NavigationRequest(destination, requestCount)
    }
}
