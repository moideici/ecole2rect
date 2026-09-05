package fr.ecole2recte.app.data

import android.content.Context
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.core.store.SettingsCodec
import fr.ecole2recte.app.core.store.SettingsStore
import fr.ecole2recte.app.core.store.SnapshotCodec
import fr.ecole2recte.app.core.store.SnapshotStore
import java.io.File

/**
 * Cache disque des données scolaires.
 *
 * Un simple fichier JSON dans le stockage privé de l'application : le volume est petit
 * (une photo complète tient en quelques dizaines de kilo-octets), l'écriture est atomique
 * grâce au fichier temporaire, et le format est celui de l'application, donc relisible même
 * si l'API change du jour au lendemain.
 */
class FileSnapshotStore(context: Context) : SnapshotStore {

    private val file = File(context.filesDir, "snapshot.json")

    override fun read(): SchoolSnapshot? = runCatching {
        if (!file.exists()) null else SnapshotCodec.decode(file.readText())
    }.getOrNull()

    override fun write(snapshot: SchoolSnapshot) {
        runCatching {
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(SnapshotCodec.encode(snapshot))
            // Un remplacement en deux temps évite de laisser un cache tronqué si le
            // processus est tué en pleine écriture.
            if (!temporary.renameTo(file)) {
                file.writeText(temporary.readText())
                temporary.delete()
            }
        }
    }

    override fun clear() {
        runCatching { file.delete() }
    }
}

/** Réglages et personnalisation, dans les préférences partagées de l'application. */
class PreferencesSettingsStore(context: Context) : SettingsStore {

    private val preferences = context.getSharedPreferences("ecole2recte_settings", Context.MODE_PRIVATE)

    override fun read(): AppSettings = SettingsCodec.decode(preferences.getString(KEY, null))

    override fun write(settings: AppSettings) {
        preferences.edit().putString(KEY, SettingsCodec.encode(settings)).apply()
    }

    private companion object {
        const val KEY = "settings"
    }
}
