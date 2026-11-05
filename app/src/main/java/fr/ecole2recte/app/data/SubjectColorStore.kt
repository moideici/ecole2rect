package fr.ecole2recte.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/** Un petit texte gardé sur l'appareil. Abstrait pour pouvoir être remplacé dans les tests. */
interface TextStorage {
    fun read(): String?
    fun write(value: String)
}

class PrefsTextStorage(context: Context, name: String, private val key: String) : TextStorage {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun read(): String? = preferences.getString(key, null)

    override fun write(value: String) {
        preferences.edit().putString(key, value).apply()
    }
}

/**
 * Les couleurs de matières choisies par l'utilisateur, par profil (compte ou élève) et par nom
 * de matière. Une couleur choisie suit la matière partout : accueil, agenda, devoirs, notes.
 */
class SubjectColorStore(private val storage: TextStorage) {
    private val state = MutableStateFlow(load())

    private fun load(): Map<String, Map<String, Int>> = runCatching {
        val root = JSONObject(storage.read() ?: return emptyMap())
        root.keys().asSequence().associate { owner ->
            val subjects = root.getJSONObject(owner)
            owner to subjects.keys().asSequence().associateWith { subjects.getInt(it) }
        }
    }.getOrDefault(emptyMap())

    private fun save(value: Map<String, Map<String, Int>>) {
        val root = JSONObject()
        value.forEach { (owner, subjects) -> root.put(owner, JSONObject(subjects)) }
        storage.write(root.toString())
    }

    /** Les couleurs choisies pour ce profil, mises à jour en direct. */
    fun overrides(ownerKey: String): Flow<Map<String, Int>> =
        state.map { it[ownerKey.lowercase()].orEmpty() }.distinctUntilChanged()

    /** Choisit la couleur d'une matière ; `null` rend la couleur d'origine. */
    fun set(ownerKey: String, subjectKey: String, paletteIndex: Int?) {
        val owner = ownerKey.lowercase()
        val current = state.value
        val subjects = current[owner].orEmpty().toMutableMap()
        if (paletteIndex == null) subjects.remove(subjectKey) else subjects[subjectKey] = paletteIndex
        val updated = if (subjects.isEmpty()) current - owner else current + (owner to subjects)
        state.value = updated
        save(updated)
    }

    /** Efface les couleurs d'un compte et de tous ses élèves. */
    fun clear(identifier: String) {
        val prefix = identifier.lowercase() + "/"
        val updated = state.value.filterKeys { !it.startsWith(prefix) }
        state.value = updated
        save(updated)
    }
}
