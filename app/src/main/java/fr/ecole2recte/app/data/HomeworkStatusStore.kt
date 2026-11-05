package fr.ecole2recte.app.data

import android.content.Context
import fr.ecole2recte.app.data.cache.CacheOwner
import org.json.JSONObject

/** Devoirs cochés ou décochés sur l'appareil. Une valeur ici l'emporte sur ce que dit le serveur. */
interface HomeworkStatusStore {
    fun overrides(owner: CacheOwner): Map<String, Boolean>
    fun set(owner: CacheOwner, id: String, done: Boolean)

    /** Oublie la case d'un devoir : le serveur redevient la référence pour lui. */
    fun remove(owner: CacheOwner, id: String)

    /** Efface tout ce qui appartient à ce compte. */
    fun clear(identifier: String)
}

class InMemoryHomeworkStatusStore : HomeworkStatusStore {
    private val values = HashMap<CacheOwner, MutableMap<String, Boolean>>()

    override fun overrides(owner: CacheOwner): Map<String, Boolean> = values[owner].orEmpty().toMap()

    override fun set(owner: CacheOwner, id: String, done: Boolean) {
        values.getOrPut(owner) { HashMap() }[id] = done
    }

    override fun remove(owner: CacheOwner, id: String) {
        values[owner]?.remove(id)
    }

    override fun clear(identifier: String) {
        values.keys.removeAll { it.identifier.equals(identifier, ignoreCase = true) }
    }
}

class PrefsHomeworkStatusStore(context: Context) : HomeworkStatusStore {
    private val preferences = context.getSharedPreferences("ecole2recte_homework", Context.MODE_PRIVATE)

    private fun key(owner: CacheOwner) = "${owner.identifier.lowercase()}|${owner.studentId.orEmpty()}"

    override fun overrides(owner: CacheOwner): Map<String, Boolean> = runCatching {
        val json = JSONObject(preferences.getString(key(owner), null) ?: return emptyMap())
        json.keys().asSequence().associateWith { json.getBoolean(it) }
    }.getOrDefault(emptyMap())

    override fun set(owner: CacheOwner, id: String, done: Boolean) {
        val json = JSONObject(overrides(owner).mapValues { it.value })
        json.put(id, done)
        preferences.edit().putString(key(owner), json.toString()).apply()
    }

    override fun remove(owner: CacheOwner, id: String) {
        val json = JSONObject(overrides(owner).mapValues { it.value })
        json.remove(id)
        preferences.edit().putString(key(owner), json.toString()).apply()
    }

    override fun clear(identifier: String) {
        val prefix = identifier.lowercase() + "|"
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        editor.apply()
    }
}
