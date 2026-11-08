package fr.ecole2recte.app.sync

import android.content.Context

/** Options locales, indépendantes de la fréquence de synchronisation. */
class LocalFeaturePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("local-school-features", Context.MODE_PRIVATE)
    var activeOwner: String?
        get() = prefs.getString("active-owner", null)
        set(value) { prefs.edit().putString("active-owner", value).apply() }
    var currentCourse: Boolean
        get() = prefs.getBoolean("current-course", false)
        set(value) { prefs.edit().putBoolean("current-course", value).apply() }
    var absenceReminderDays: Int?
        get() = prefs.getInt("absence-days", 0).takeIf { it in listOf(2, 5, 7) }
        set(value) { prefs.edit().putInt("absence-days", value?.takeIf { it in listOf(2, 5, 7) } ?: 0).apply() }
}
