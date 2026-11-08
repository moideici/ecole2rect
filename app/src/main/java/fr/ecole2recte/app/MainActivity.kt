package fr.ecole2recte.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import fr.ecole2recte.app.settings.shouldSecureWindow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.security.AppLock
import fr.ecole2recte.app.sync.AndroidNotifier
import fr.ecole2recte.app.ui.AppRoot
import fr.ecole2recte.app.ui.LockScreen
import fr.ecole2recte.app.ui.theme.Ecole2recteTheme

class MainActivity : FragmentActivity() {
    private var locked by mutableStateOf(false)
    private var lockMessage by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        publishRequestedTab(intent)
    }

    /** Un raccourci, une notification ou un widget demande un onglet précis. */
    private fun publishRequestedTab(intent: Intent?) {
        if (intent == null) return
        val container = (application as Ecole2recteApplication).container
        val target = if (intent.data?.scheme == "ecole2recte") {
            fr.ecole2recte.app.domain.DeepLinks.parse(intent.dataString)
        } else {
            fr.ecole2recte.app.domain.DeepLinks.feature(intent.getStringExtra("feature")) ?: intent.getStringExtra(AndroidNotifier.TAB_EXTRA)?.takeIf { it in setOf("accueil", "agenda", "agenda_homework", "notes", "messages", "plus", "recherche", "vie-scolaire") }
                ?.let { fr.ecole2recte.app.domain.NavigationTarget(it, if (it == "notes") fr.ecole2recte.app.domain.DeepLinks.subject(intent.getStringExtra("subject")) else null) }
        } ?: return
        container.pendingSubject.value = target.subject
        container.pendingTab.value = target.tab
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = (application as Ecole2recteApplication).container.settings
        publishRequestedTab(intent)
        // Aperçu masqué dans les applications récentes quand le verrou est actif et l'option cochée.
        lifecycleScope.launch {
            combine(settings.appLock, settings.hidePreview, ::shouldSecureWindow).distinctUntilChanged().collect { secure ->
                if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        // Au démarrage à froid, le verrou est toujours demandé.
        locked = settings.appLock.value
        setContent {
            val mode by settings.themeMode.collectAsStateWithLifecycle()
            val colorTheme by settings.colorTheme.collectAsStateWithLifecycle()
            val textScale by settings.textScale.collectAsStateWithLifecycle()
            val lockEnabled by settings.appLock.collectAsStateWithLifecycle()
            Ecole2recteTheme(mode, colorTheme, textScale) {
                if (locked && lockEnabled) {
                    LockScreen(message = lockMessage, onUnlock = ::unlock)
                } else {
                    AppRoot()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // L'application se ferme pour de bon : on efface les pièces jointes qu'elle a ouvertes.
        if (isFinishing) {
            fr.ecole2recte.app.ui.common.AttachmentFiles.at(this).clear()
            fr.ecole2recte.app.export.ExportFiles.at(this).clear()
        }
    }

    override fun onStop() {
        super.onStop()
        AppLock.timer.onBackground()
    }

    override fun onStart() {
        super.onStart()
        val settings = (application as Ecole2recteApplication).container.settings
        // Revenir après une courte absence ne redemande pas le verrou ; après le délai choisi (immédiat, 1, 5 ou 15 min), si.
        if (AppLock.timer.onForeground(settings.appLock.value, settings.lockDelay.value)) locked = true
    }

    private fun unlock() {
        lockMessage = null
        AppLock.authenticate(
            activity = this,
            title = "Déverrouiller ecole2recte",
            onSuccess = { locked = false },
            onFailure = { lockMessage = it },
        )
    }
}
