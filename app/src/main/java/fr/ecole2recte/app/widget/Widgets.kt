package fr.ecole2recte.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import fr.ecole2recte.app.MainActivity
import fr.ecole2recte.app.sync.AndroidNotifier
import fr.ecole2recte.app.sync.WidgetSink
import fr.ecole2recte.app.sync.WidgetSnapshot
import fr.ecole2recte.app.sync.WidgetSnapshotStore

private val Background = Color(0xFF4A50D6)
private val OnBackground = Color.White
private val Soft = Color(0xCCFFFFFF)

private fun title(text: String) = TextStyle(color = ColorProvider(Soft), fontSize = 12.sp, fontWeight = FontWeight.Bold)
private fun big(text: String) = TextStyle(color = ColorProvider(OnBackground), fontSize = 20.sp, fontWeight = FontWeight.Bold)
private fun normal() = TextStyle(color = ColorProvider(OnBackground), fontSize = 14.sp)
private fun soft() = TextStyle(color = ColorProvider(Soft), fontSize = 13.sp)

@Composable
private fun opens(tab: String): androidx.glance.action.Action =
    actionStartActivity(
        Intent(LocalContext.current, MainActivity::class.java)
            .putExtra(AndroidNotifier.TAB_EXTRA, tab)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )

/** Widget « Prochain cours ». */
class NextClassWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotStore(context).read()
        provideContent {
            Column(
                GlanceModifier.fillMaxSize().background(Background).padding(14.dp).clickable(opens("agenda")),
            ) {
                Text("PROCHAIN COURS", style = title("PROCHAIN COURS"))
                val next = snapshot?.nextClass
                when {
                    snapshot == null -> Text("Active les notifications et widgets dans Plus.", style = normal())
                    next == null -> Text("Plus de cours à venir", style = big("x"))
                    else -> {
                        Text(next.subject, style = big(next.subject))
                        Text(next.timeLabel, style = normal())
                        if (next.place.isNotBlank()) Text(next.place, style = soft())
                    }
                }
            }
        }
    }
}

class NextClassWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextClassWidget()
}

/** Widget « Devoirs à faire ». */
class HomeworkWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotStore(context).read()
        provideContent {
            Column(
                GlanceModifier.fillMaxSize().background(Background).padding(14.dp).clickable(opens("agenda_homework")),
            ) {
                Text("DEVOIRS À FAIRE", style = title("DEVOIRS"))
                when {
                    snapshot == null -> Text("Active les notifications et widgets dans Plus.", style = normal())
                    snapshot.tasks.isEmpty() -> Text("Rien à faire", style = big("x"))
                    else -> snapshot.tasks.take(4).forEach { task ->
                        Text("${task.subject} · ${task.due}", style = normal())
                    }
                }
            }
        }
    }
}

class HomeworkWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HomeworkWidget()
}

/** Enregistre l'instantané puis redessine les widgets déjà posés sur l'écran d'accueil. */
class WidgetUpdater(private val context: Context) : WidgetSink {
    override suspend fun publish(snapshot: WidgetSnapshot) {
        WidgetSnapshotStore(context).write(snapshot)
        runCatching {
            NextClassWidget().updateAll(context)
            HomeworkWidget().updateAll(context)
        }
    }
}
