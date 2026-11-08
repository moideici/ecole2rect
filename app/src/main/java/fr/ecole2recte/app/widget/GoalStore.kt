package fr.ecole2recte.app.widget

import android.content.Context
import fr.ecole2recte.app.data.PrefsTextStorage
import fr.ecole2recte.app.data.TextStorage
import org.json.JSONObject

/** Petits objectifs locaux sur 20 ; pas de réseau, même stockage pour la fiche matière et le widget. */
class GoalStore(private val storage: TextStorage) {
    private fun root() = runCatching { JSONObject(storage.read() ?: "{}") }.getOrElse { JSONObject() }

    fun values(profile: String): Map<String, Double> {
        val values = root().optJSONObject(profile.lowercase()) ?: return emptyMap()
        return values.keys().asSequence().mapNotNull { key -> values.number(key)?.takeIf { it > 0 && it <= 20 }?.let { key to it } }.toMap()
    }

    fun set(profile: String, subject: String, target: Double?): Boolean {
        if (profile.isBlank() || subject.isBlank() || (target != null && (!target.isFinite() || target <= 0 || target > 20))) return false
        val root = root()
        val key = profile.lowercase()
        val values = root.optJSONObject(key) ?: JSONObject()
        if (target == null) values.remove(subject) else values.put(subject, target)
        if (values.length() == 0) root.remove(key) else root.put(key, values)
        storage.write(root.toString())
        return true
    }

    fun clearAccount(identifier: String) {
        val root = root()
        root.keys().asSequence().toList().filter { it.startsWith(identifier.lowercase() + "/") }.forEach(root::remove)
        storage.write(root.toString())
    }

    companion object {
        fun at(context: Context) = GoalStore(PrefsTextStorage(context, "ecole2recte_goals", "targets"))
    }
}
