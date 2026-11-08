package fr.ecole2recte.app.widget

import android.content.Context
import fr.ecole2recte.app.data.PrefsTextStorage
import fr.ecole2recte.app.data.TextStorage
import org.json.JSONObject

/** Réglages locaux par appWidgetId ; une suppression ne touche jamais les autres instances. */
class WidgetConfigStore(private val storage: TextStorage) {
    private fun root() = runCatching { JSONObject(storage.read() ?: "{}") }.getOrElse { JSONObject() }

    fun read(id: Int): MeanWidgetConfig = runCatching {
        val json = root().optJSONObject(id.toString()) ?: return MeanWidgetConfig()
        MeanWidgetConfig(
            profile = json.optString("profile"),
            source = MeanSource.entries.firstOrNull { it.name == json.optString("source") } ?: MeanSource.General,
            subject = json.optString("subject"),
            period = MeanPeriod.entries.firstOrNull { it.name == json.optString("period") } ?: MeanPeriod.Current,
            title = json.optString("title"), target = json.optDouble("target", 14.0),
            greenFrom = json.optDouble("green", 14.0), orangeFrom = json.optDouble("orange", 10.0),
            showTrend = json.optBoolean("trend", true),
        ).takeIf { it.valid() } ?: MeanWidgetConfig()
    }.getOrDefault(MeanWidgetConfig())

    fun save(id: Int, config: MeanWidgetConfig): Boolean {
        if (id <= 0 || !config.valid()) return false
        val root = root()
        root.put(id.toString(), JSONObject().put("profile", config.profile).put("source", config.source.name)
            .put("subject", config.subject).put("period", config.period.name).put("title", config.title)
            .put("target", config.target).put("green", config.greenFrom).put("orange", config.orangeFrom).put("trend", config.showTrend))
        storage.write(root.toString())
        return true
    }

    fun remove(id: Int) { val root = root(); root.remove(id.toString()); storage.write(root.toString()) }

    fun clearAccount(identifier: String) {
        val root = root()
        root.keys().asSequence().toList().filter { root.optJSONObject(it)?.optString("profile")?.startsWith(identifier.lowercase() + "/") == true }.forEach {
            // Garde uniquement la liaison : l'instance affiche « – » au lieu de suivre un autre compte.
            val profile = root.getJSONObject(it).optString("profile")
            root.put(it, JSONObject().put("profile", profile))
        }
        storage.write(root.toString())
    }

    companion object {
        fun at(context: Context) = WidgetConfigStore(PrefsTextStorage(context, "ecole2recte_widget_config", "instances"))
    }
}
