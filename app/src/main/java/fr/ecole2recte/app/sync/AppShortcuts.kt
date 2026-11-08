package fr.ecole2recte.app.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import fr.ecole2recte.app.MainActivity
import fr.ecole2recte.app.R
import fr.ecole2recte.app.data.PrefsTextStorage
import fr.ecole2recte.app.data.ProfileKeyValueStore
import fr.ecole2recte.app.domain.ShortcutLogic
import fr.ecole2recte.app.domain.SubjectVisits
import fr.ecole2recte.app.settings.PrivacySelection

/** Raccourcis construits uniquement à partir des consultations et du dernier instantané local. */
object AppShortcuts {
    fun visits(context: Context) = SubjectVisits(ProfileKeyValueStore(PrefsTextStorage(context, "subject-shortcuts", "visits")))
    private fun intent(context: Context, uri: String) = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(uri))
    private fun subjectId(owner: String, id: String) = "subject/" + Uri.encode(owner) + "/" + Uri.encode(id)
    fun refresh(context: Context) {
        runCatching {
            val snapshot = WidgetSnapshotStore(context).read()?.takeIf { it.owner.isNotBlank() && it.owner == LocalFeaturePreferences(context).activeOwner }
            val selected = ShortcutLogic.select(visits(context).read(snapshot?.owner.orEmpty()), snapshot?.shortcutSubjects.orEmpty().toSet(), PrivacySelection.mode(context).enabled)
            val items = selected.map {
                ShortcutInfoCompat.Builder(context, subjectId(snapshot!!.owner, it.id)).setShortLabel(it.title.take(40))
                    .setLongLabel("Notes · ${it.title}").setIcon(IconCompat.createWithResource(context, R.drawable.ic_stat_ecole2recte))
                    .setIntent(intent(context, "ecole2recte://notes?matiere=${Uri.encode(it.id)}")).build()
            }.take(ShortcutManagerCompat.getMaxShortcutCountPerActivity(context))
            // Les raccourcis épinglés restent chez le lanceur : on les désactive aussi si privés ou périmés.
            val active = items.map { it.id }.toSet()
            val pinned = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            val hidden = PrivacySelection.mode(context).enabled
            val disabled = pinned.filter { (it.id.startsWith("subject/") && it.id !in active) || (it.id == "next-class" && (hidden || snapshot?.owner.isNullOrBlank())) }.map { it.id }
            if (disabled.isNotEmpty()) {
                maskPinned(context, disabled)
                ShortcutManagerCompat.disableShortcuts(context, disabled, "Raccourci indisponible")
            }
            val enabled = items + if (!hidden && !snapshot?.owner.isNullOrBlank()) listOf(nextClassShortcut(context)) else emptyList()
            if (enabled.isNotEmpty()) ShortcutManagerCompat.enableShortcuts(context, enabled)
            ShortcutManagerCompat.setDynamicShortcuts(context, items)
        }
    }

    fun pinNextClass(context: Context): Boolean {
        if (PrivacySelection.mode(context).enabled || !ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        return ShortcutManagerCompat.requestPinShortcut(context, nextClassShortcut(context), null)
    }

    private fun nextClassShortcut(context: Context): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, "next-class")
            .setShortLabel("Prochain cours").setIcon(IconCompat.createWithResource(context, R.drawable.ic_stat_ecole2recte))
            .setIntent(intent(context, "ecole2recte://agenda")).build()

    fun clear(context: Context) {
        runCatching {
            ShortcutManagerCompat.removeAllDynamicShortcuts(context)
            val pinned = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED).map { it.id }
            if (pinned.isNotEmpty()) {
                maskPinned(context, pinned)
                ShortcutManagerCompat.disableShortcuts(context, pinned, "Compte déconnecté")
            }
        }
    }

    private fun maskPinned(context: Context, ids: List<String>) {
        ShortcutManagerCompat.updateShortcuts(context, ids.map { id ->
            ShortcutInfoCompat.Builder(context, id).setShortLabel(if (id == "next-class") "Prochain cours" else "Notes")
                .setLongLabel(if (id == "next-class") "Prochain cours" else "Notes")
                .setIntent(intent(context, if (id == "next-class") "ecole2recte://agenda" else "ecole2recte://notes")).build()
        })
    }
}
