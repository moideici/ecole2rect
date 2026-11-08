package fr.ecole2recte.app.sync

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import fr.ecole2recte.app.MainActivity
import fr.ecole2recte.app.settings.PrivacySelection
import java.time.LocalDateTime

class NextClassTileService : TileService() {
    override fun onStartListening() {
        val snapshot = WidgetSnapshotStore(this).read()?.takeIf { it.owner.isNotBlank() && it.owner == LocalFeaturePreferences(this).activeOwner }
        val plan = CurrentCoursePlanner.plan(snapshot?.lessons.orEmpty(), LocalDateTime.now())
        qsTile?.apply {
            label = if (PrivacySelection.mode(this@NextClassTileService).enabled || isLocked) "Prochain cours" else plan.next?.subject ?: "Aucun cours à venir"
            state = if (plan.next == null) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            updateTile()
        }
    }

    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val open = Intent(this, MainActivity::class.java).putExtra("tab", "agenda").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        unlockAndRun {
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 4101, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            else startActivityAndCollapse(open)
        }
    }
}
