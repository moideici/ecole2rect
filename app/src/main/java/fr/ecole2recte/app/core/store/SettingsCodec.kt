package fr.ecole2recte.app.core.store

import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.core.settings.HomeWidget
import fr.ecole2recte.app.core.settings.HomeWidgetSetting
import fr.ecole2recte.app.core.settings.NotificationSettings
import fr.ecole2recte.app.core.settings.ThemeChoice
import fr.ecole2recte.app.core.settings.WidgetColorSettings
import fr.ecole2recte.app.core.settings.WidgetTheme
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
        put("binnedMessages", JSONArray(settings.binnedMessages.toList()))
        settings.averageTarget?.let { put("averageTarget", it) }
        settings.selectedAccountId?.let { put("selectedAccountId", it) }
        put("showDiagnostics", settings.showDiagnostics)
        put("biometricLock", settings.biometricLock)
        put("dynamicColor", settings.dynamicColor)
        put("parentAccount", settings.parentAccount)
        put("notified", JSONArray(settings.notified))
        put(
            "widgetColors",
            JSONObject()
                .put("theme", settings.widgetColors.theme.name)
                // `JSONObject.put` refuse null : une couleur absente est une clé absente,
                // ce que la relecture traduit par « celle du thème ».
                .apply {
                    settings.widgetColors.background?.let { put("background", it) }
                    settings.widgetColors.ink?.let { put("ink", it) }
                },
        )
        put(
            "notifications",
            JSONObject()
                .put("enabled", settings.notifications.enabled)
                .put("grades", settings.notifications.grades)
                .put("homework", settings.notifications.homework)
                .put("messages", settings.notifications.messages)
                .put("examReminder", settings.notifications.examReminder)
                .put("timetable", settings.notifications.timetable)
                .put("tomorrow", settings.notifications.tomorrow),
        )
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
            binnedMessages = root.optJSONArray("binnedMessages").stringSet(),
            averageTarget = if (root.has("averageTarget")) root.optDouble("averageTarget")
                .takeIf { !it.isNaN() } else null,
            selectedAccountId = root.optString("selectedAccountId").takeIf { it.isNotBlank() },
            showDiagnostics = root.optBoolean("showDiagnostics", false),
            biometricLock = root.optBoolean("biometricLock", false),
            dynamicColor = root.optBoolean("dynamicColor", false),
            parentAccount = root.optBoolean("parentAccount", false),
            notified = root.optJSONArray("notified").stringSet().toList(),
            widgetColors = root.optJSONObject("widgetColors")?.let { entry ->
                WidgetColorSettings(
                    theme = enumOrNull<WidgetTheme>(entry.optString("theme")) ?: WidgetTheme.SYSTEM,
                    background = entry.optLong("background").takeIf { it != 0L },
                    ink = entry.optLong("ink").takeIf { it != 0L },
                )
            } ?: WidgetColorSettings(),
            notifications = root.optJSONObject("notifications")?.let { entry ->
                NotificationSettings(
                    enabled = entry.optBoolean("enabled", true),
                    grades = entry.optBoolean("grades", true),
                    homework = entry.optBoolean("homework", true),
                    messages = entry.optBoolean("messages", true),
                    examReminder = entry.optBoolean("examReminder", true),
                    timetable = entry.optBoolean("timetable", true),
                    tomorrow = entry.optBoolean("tomorrow", true),
                )
            } ?: NotificationSettings(),
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
