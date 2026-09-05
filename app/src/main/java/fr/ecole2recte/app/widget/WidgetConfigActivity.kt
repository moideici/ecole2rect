package fr.ecole2recte.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.core.repo.Insights
import fr.ecole2recte.app.core.widget.WidgetOptions
import fr.ecole2recte.app.ui.components.AppCard
import fr.ecole2recte.app.ui.components.ScreenPadding
import fr.ecole2recte.app.ui.theme.Ecole2recteTheme

/**
 * Réglages d'un widget, au moment où on le pose.
 *
 * Le système ouvre cet écran avant que le widget n'apparaisse : c'est le seul moment où on
 * peut lui demander « la moyenne générale, ou celle d'une matière ? ». Si l'utilisateur
 * abandonne, le widget n'est pas créé — d'où le résultat annulé posé dès l'entrée.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(Activity.RESULT_CANCELED, resultIntent())

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val data = WidgetData.load(this)
        val subjects = Insights.knownSubjects(data.snapshot)

        setContent {
            Ecole2recteTheme(data.settings.theme, data.settings.dynamicColor) {
                ConfigScreen(
                    subjects = subjects,
                    onChoose = { subject -> confirm(subject) },
                )
            }
        }
    }

    private fun confirm(subject: String?) {
        WidgetOptionsStore(this).save(widgetId, WidgetOptions(subject))
        WidgetUpdater.updateAll(this)
        setResult(Activity.RESULT_OK, resultIntent())
        finish()
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}

@Composable
private fun ConfigScreen(subjects: List<String>, onChoose: (String?) -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(contentPadding = PaddingValues(vertical = 24.dp)) {
            item("titre") {
                Text(
                    "Que doit afficher ce widget ?",
                    modifier = Modifier.padding(horizontal = ScreenPadding + 4.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            item("generale") {
                Box(Modifier.padding(horizontal = ScreenPadding, vertical = 5.dp)) {
                    AppCard(onClick = { onChoose(null) }) {
                        Text("Moyenne générale", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "La moyenne de la période en cours, toutes matières confondues.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (subjects.isEmpty()) {
                item("vide") {
                    Text(
                        "Les matières apparaîtront ici après la première synchronisation.",
                        modifier = Modifier.padding(horizontal = ScreenPadding + 4.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(subjects, key = { it }) { subject ->
                Box(Modifier.padding(horizontal = ScreenPadding, vertical = 5.dp)) {
                    AppCard(onClick = { onChoose(subject) }) {
                        Text(subject, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

/**
 * Réglages par widget posé.
 *
 * Chaque instance a son identifiant système : deux widgets « moyenne » peuvent donc afficher
 * deux matières différentes. Les réglages sont effacés quand le widget est retiré, sinon les
 * préférences accumuleraient des identifiants morts jusqu'à la désinstallation.
 */
class WidgetOptionsStore(context: Context) {

    private val preferences = context.getSharedPreferences("ecole2recte_widgets", Context.MODE_PRIVATE)

    fun read(widgetId: Int): WidgetOptions =
        WidgetOptions(subject = preferences.getString(key(widgetId), null))

    fun save(widgetId: Int, options: WidgetOptions) {
        preferences.edit().apply {
            if (options.subject == null) remove(key(widgetId)) else putString(key(widgetId), options.subject)
        }.apply()
    }

    fun forget(widgetIds: IntArray) {
        preferences.edit().apply { widgetIds.forEach { remove(key(it)) } }.apply()
    }

    private fun key(widgetId: Int) = "widget-$widgetId-matiere"
}
