package fr.ecole2recte.app.widget

import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import fr.ecole2recte.app.Ecole2recteApplication
import fr.ecole2recte.app.MainActivity
import fr.ecole2recte.app.domain.MASKED
import fr.ecole2recte.app.domain.PrivacyMode
import fr.ecole2recte.app.settings.AverageDecimals
import fr.ecole2recte.app.settings.PrivacySelection
import fr.ecole2recte.app.sync.WidgetSnapshot
import fr.ecole2recte.app.sync.WidgetSnapshotStore

object MeanTileData {
    fun label(snapshot: WidgetSnapshot?, digits: Int, privacy: PrivacyMode, locked: Boolean): String {
        if (locked) return "Verrouillé"
        val value = MeanWidgetData.read(snapshot, MeanWidgetConfig(), digits, privacy).value
        return if (value == "–" || value == MASKED) value else "$value /20"
    }
}

/** La tuile relit uniquement l'instantané ; Android et MainActivity conservent le verrouillage. */
class MeanTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val appLock = (application as Ecole2recteApplication).container.settings.appLock.value
        val keyguardLocked = getSystemService(KeyguardManager::class.java)?.isDeviceLocked ?: true
        val locked = isLocked || keyguardLocked || appLock
        val text = MeanTileData.label(WidgetSnapshotStore(this).read(), AverageDecimals.read(this), PrivacySelection.mode(this), locked)
        tile.label = "Moyenne · $text"
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = text
        tile.state = if (locked) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val open = {
            val intent = Intent(this, MainActivity::class.java).putExtra("tab", "grades")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
        if (isLocked) unlockAndRun(open) else open()
    }
}
