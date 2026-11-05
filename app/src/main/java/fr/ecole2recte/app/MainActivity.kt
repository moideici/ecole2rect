package fr.ecole2recte.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.ui.AppRoot
import fr.ecole2recte.app.ui.theme.Ecole2recteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = (application as Ecole2recteApplication).container.settings
        setContent {
            val mode by settings.themeMode.collectAsStateWithLifecycle()
            Ecole2recteTheme(mode) { AppRoot() }
        }
    }
}
