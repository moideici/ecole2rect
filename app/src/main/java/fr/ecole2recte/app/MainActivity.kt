package fr.ecole2recte.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import fr.ecole2recte.app.ui.Ecole2recteApp

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Ecole2recteApp(viewModel) }
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
}
