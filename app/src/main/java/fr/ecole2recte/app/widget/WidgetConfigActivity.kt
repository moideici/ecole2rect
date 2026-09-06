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
import fr.ecole2recte.app.core.widget.WidgetKind
import fr.ecole2recte.app.core.widget.WidgetOptions
import fr.ecole2recte.app.ui.components.AppCard
import fr.ecole2recte.app.ui.components.ScreenPadding
import fr.ecole2recte.app.ui.theme.Ecole2recteTheme

/**
 * Réglages d'un widget, au moment où on le pose.
 *
 * Le système ouvre cet écran avant que le widget n'apparaisse : c'est le seul moment où on
 * peut lui demander « lequel de vos enfants ? » ou « la moyenne générale, ou celle d'une
 * matière ? ». Deux principes :
 *
 *  - **on ne demande que ce qui a une réponse.** Un élève seul n'a pas à choisir un profil,
 *    et la plupart des widgets n'ont pas de matière à désigner ; dans ce cas l'écran ne
 *    s'affiche pas du tout et le widget est posé immédiatement ;
 *  - **abandonner n'installe rien**, d'où le résultat annulé posé dès l'entrée.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var chosenProfile: String? = null

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

        val kind = kindOfWidget()
        val profiles = WidgetData.profiles(this)
        val subjectAware = kind != null && kind in WidgetOptions.SUBJECT_AWARE

        // Rien à demander : on pose le widget sans faire perdre un toucher à personne.
        if (profiles.size < 2 && !subjectAware) {
            confirm(WidgetOptions())
            return
        }

        val data = WidgetData.load(this)
        val subjects = if (subjectAware) Insights.knownSubjects(data.snapshot) else emptyList()

        setContent {
            Ecole2recteTheme(data.settings.theme, data.settings.dynamicColor) {
                ConfigScreen(
                    profiles = if (profiles.size > 1) profiles else emptyList(),
                    subjects = subjects,
                    subjectAware = subjectAware,
                    onProfile = { profile ->
                        chosenProfile = profile
                        // Sans matière à choisir, le profil suffit : on n'enchaîne pas un
                        // deuxième écran pour rien.
                        if (!subjectAware) confirm(WidgetOptions(accountId = profile))
                    },
                    onChoose = { subject -> confirm(WidgetOptions(subject, chosenProfile)) },
                )
            }
        }
    }

    private fun kindOfWidget(): WidgetKind? = runCatching {
        WidgetUpdater.kindOf(
            AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider?.className,
        )
    }.getOrNull()

    private fun confirm(options: WidgetOptions) {
        WidgetOptionsStore(this).save(widgetId, options)
        WidgetUpdater.updateAll(this)
        setResult(Activity.RESULT_OK, resultIntent())
        finish()
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}

@Composable
private fun ConfigScreen(
    profiles: List<WidgetData.Profile>,
    subjects: List<String>,
    subjectAware: Boolean,
    onProfile: (String?) -> Unit,
    onChoose: (String?) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(contentPadding = PaddingValues(vertical = 24.dp)) {
            if (profiles.isNotEmpty()) {
                item("titre-eleve") { SectionTitle("Quel élève ce widget suit-il ?") }
                item("eleve-suivi") {
                    Choice(
                        title = "L'élève affiché dans l'application",
                        description = "Le widget change en même temps que le profil ouvert.",
                        onClick = { onProfile(null) },
                    )
                }
                items(profiles, key = { it.id }) { profile ->
                    Choice(
                        title = profile.name,
                        description = profile.className.ifBlank { "Profil enregistré" },
                        onClick = { onProfile(profile.id) },
                    )
                }
            }

            if (subjectAware) {
                item("titre-matiere") { SectionTitle("Que doit afficher ce widget ?") }
                item("generale") {
                    Choice(
                        title = "Moyenne générale",
                        description = "La moyenne de la période en cours, toutes matières confondues.",
                        onClick = { onChoose(null) },
                    )
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
                    Choice(title = subject, description = "", onClick = { onChoose(subject) })
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = ScreenPadding + 4.dp, vertical = 12.dp),
        style = MaterialTheme.typography.headlineSmall,
    )
}

@Composable
private fun Choice(title: String, description: String, onClick: () -> Unit) {
    Box(Modifier.padding(horizontal = ScreenPadding, vertical = 5.dp)) {
        AppCard(onClick = onClick) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (description.isNotBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Réglages par widget posé.
 *
 * Chaque instance a son identifiant système : deux widgets « moyenne » peuvent afficher deux
 * matières, et deux widgets « journée » deux enfants. Les réglages sont effacés quand le
 * widget est retiré, sinon les préférences accumuleraient des identifiants morts jusqu'à la
 * désinstallation.
 */
class WidgetOptionsStore(context: Context) {

    private val preferences = context.getSharedPreferences("ecole2recte_widgets", Context.MODE_PRIVATE)

    fun read(widgetId: Int): WidgetOptions = WidgetOptions(
        subject = preferences.getString(subjectKey(widgetId), null),
        accountId = preferences.getString(accountKey(widgetId), null),
    )

    fun save(widgetId: Int, options: WidgetOptions) {
        preferences.edit().apply {
            if (options.subject == null) {
                remove(subjectKey(widgetId))
            } else {
                putString(subjectKey(widgetId), options.subject)
            }
            if (options.accountId == null) {
                remove(accountKey(widgetId))
            } else {
                putString(accountKey(widgetId), options.accountId)
            }
        }.apply()
    }

    fun forget(widgetIds: IntArray) {
        preferences.edit().apply {
            widgetIds.forEach { id ->
                remove(subjectKey(id))
                remove(accountKey(id))
            }
        }.apply()
    }

    private fun subjectKey(widgetId: Int) = "widget-$widgetId-matiere"

    private fun accountKey(widgetId: Int) = "widget-$widgetId-eleve"
}
