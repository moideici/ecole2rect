package fr.ecole2recte.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.security.AppLock
import fr.ecole2recte.app.sync.AndroidNotifier
import fr.ecole2recte.app.ui.AppRoot
import fr.ecole2recte.app.ui.LockScreen
import fr.ecole2recte.app.ui.theme.Ecole2recteTheme

class MainActivity : FragmentActivity() {
    private var locked by mutableStateOf(false)
    private var lockMessage by mutableStateOf<String?>(null)
    private var leftAt = 0L

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        publishRequestedTab(intent)
    }

    /** Un raccourci, une notification ou un widget demande un onglet précis. */
    private fun publishRequestedTab(intent: Intent?) {
        intent?.getStringExtra(AndroidNotifier.TAB_EXTRA)?.let {
            (application as Ecole2recteApplication).container.pendingTab.value = it
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = (application as Ecole2recteApplication).container.settings
        publishRequestedTab(intent)
        // Au démarrage à froid, le verrou est toujours demandé.
        locked = settings.appLock.value
        setContent {
            val mode by settings.themeMode.collectAsStateWithLifecycle()
            val lockEnabled by settings.appLock.collectAsStateWithLifecycle()
            Ecole2recteTheme(mode) {
                if (locked && lockEnabled) {
                    LockScreen(message = lockMessage, onUnlock = ::unlock)
                } else {
                    AppRoot()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        leftAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        val settings = (application as Ecole2recteApplication).container.settings
        // Revenir après une courte absence ne redemande pas le verrou ; une longue absence, si.
        if (settings.appLock.value && leftAt != 0L && SystemClock.elapsedRealtime() - leftAt > RELOCK_AFTER_MS) {
            locked = true
        }
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

    private companion object {
        const val RELOCK_AFTER_MS = 30_000L
    }
}
