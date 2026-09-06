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

    /** Un élève dont le cache existe : de quoi proposer un widget par enfant. */
    data class Profile(val id: String, val name: String, val className: String)

    /**
     * @param accountId l'élève à afficher. `null` signifie « celui que l'application suit »,
     * ce qui est le bon choix par défaut ; un parent peut poser un widget par enfant et
     * chacun garde le sien, même après avoir changé de profil dans l'application.
     */
    fun load(context: Context, accountId: String? = null): Loaded {
        val settings = runCatching { PreferencesSettingsStore(context).read() }.getOrNull() ?: AppSettings()
        // Le cache est rangé par profil : un widget doit montrer l'élève qu'on lui a désigné,
        // à défaut celui que l'application suit, jamais le premier fichier venu.
        val profile = accountId ?: settings.selectedAccountId
        val snapshot = runCatching {
            FileSnapshotStore(context).read(profile)
        }.getOrNull() ?: SchoolSnapshot()
        return Loaded(snapshot = snapshot, settings = settings, now = LocalDateTime.now())
    }

    /** Les élèves dont un cache existe, tels que l'écran de configuration les propose. */
    fun profiles(context: Context): List<Profile> = runCatching {
        val store = FileSnapshotStore(context)
        store.profiles().mapNotNull { id ->
            val account = store.read(id)?.account ?: return@mapNotNull null
            Profile(
                id = id,
                name = account.displayName.ifBlank { "Élève" },
                className = account.className,
            )
        }
    }.getOrDefault(emptyList())
}
