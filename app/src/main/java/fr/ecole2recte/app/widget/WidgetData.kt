package fr.ecole2recte.app.widget

import android.content.Context
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.data.FileSnapshotStore
import fr.ecole2recte.app.data.PreferencesSettingsStore
import java.time.LocalDateTime

/**
 * Ce qu'un widget lit pour se dessiner.
 *
 * Un widget est réveillé par le système, souvent sans que l'application tourne : il ne peut
 * donc pas compter sur le dépôt en mémoire. Il relit le cache sur disque — le même fichier
 * que l'application — ce qui a deux conséquences heureuses : aucun appel réseau depuis un
 * `BroadcastReceiver`, et un widget qui affiche exactement ce que l'application affiche.
 */
object WidgetData {

    data class Loaded(
        val snapshot: SchoolSnapshot,
        val settings: AppSettings,
        val now: LocalDateTime,
    )

    fun load(context: Context): Loaded = Loaded(
        snapshot = runCatching { FileSnapshotStore(context).read() }.getOrNull() ?: SchoolSnapshot(),
        settings = runCatching { PreferencesSettingsStore(context).read() }.getOrNull() ?: AppSettings(),
        now = LocalDateTime.now(),
    )
}
