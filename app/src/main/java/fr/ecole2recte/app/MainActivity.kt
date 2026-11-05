package fr.ecole2recte.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import fr.ecole2recte.app.ui.AppRoot
import fr.ecole2recte.app.ui.theme.Ecole2recteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Ecole2recteTheme { AppRoot() } }
    }
}
