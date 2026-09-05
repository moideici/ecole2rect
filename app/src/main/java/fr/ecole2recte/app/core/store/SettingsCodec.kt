package fr.ecole2recte.app.core.store

import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.core.settings.HomeWidget
import fr.ecole2recte.app.core.settings.HomeWidgetSetting
import fr.ecole2recte.app.core.settings.ThemeChoice
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sérialisation des réglages.
 *
 * Tolérante par construction : une valeur inconnue (widget supprimé, thème renommé) est
 * ignorée et remplacée par la valeur par défaut, plutôt que de faire échouer la lecture et
 * de réinitialiser toute la personnalisation de l'utilisateur.
 */
object SettingsCodec {

    fun encode(settings: AppSettings): String = JSONObject().apply {
        put("theme", settings.theme.name)
        put(
            "subjectColors",
            JSONObject().apply {
                settings.subjectColors.forEach { (key, color) -> put(key, color) }
            },
        )
        put(
            "homeWidgets",
            JSONArray().apply {
                settings.orderedWidgets.forEach { setting ->
                    put(
                        JSONObject()
                            .put("widget", setting.widget.name)
                            .put("visible", setting.visible),
                    )
                }
            },
        )
        put("doneHomework", JSONArray(settings.doneHomework.toList()))
        put("undoneHomework", JSONArray(settings.undoneHomework.toList()))
        put("readMessages", JSONArray(settings.readMessages.toList()))
        settings.selectedAccountId?.let { put("selectedAccountId", it) }
        put("showDiagnostics", settings.showDiagnostics)
    }.toString()

    fun decode(raw: String?): AppSettings {
        if (raw.isNullOrBlank()) return AppSettings()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return AppSettings()
        val widgets = root.optJSONArray("homeWidgets")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val entry = array.optJSONObject(index) ?: continue
                    val widget = enumOrNull<HomeWidget>(entry.optString("widget")) ?: continue
                    add(HomeWidgetSetting(widget, entry.optBoolean("visible", true)))
                }
            }
        }.orEmpty()
        return AppSettings(
            theme = enumOrNull<ThemeChoice>(root.optString("theme")) ?: ThemeChoice.SYSTEM,
            subjectColors = root.optJSONObject("subjectColors")?.let { colors ->
                colors.keys().asSequence().associateWith { key -> colors.optLong(key) }
                    .filterValues { it != 0L }
            }.orEmpty(),
            homeWidgets = widgets.ifEmpty { HomeWidget.DEFAULT_ORDER.map { HomeWidgetSetting(it) } },
            doneHomework = root.optJSONArray("doneHomework").stringSet(),
            undoneHomework = root.optJSONArray("undoneHomework").stringSet(),
            readMessages = root.optJSONArray("readMessages").stringSet(),
            selectedAccountId = root.optString("selectedAccountId").takeIf { it.isNotBlank() },
            showDiagnostics = root.optBoolean("showDiagnostics", false),
        )
    }

    private inline fun <reified T : Enum<T>> enumOrNull(value: String?): T? =
        enumValues<T>().firstOrNull { it.name == value }

    private fun JSONArray?.stringSet(): Set<String> {
        val array = this ?: return emptySet()
        return buildSet {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
}
