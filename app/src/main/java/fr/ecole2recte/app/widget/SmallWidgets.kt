package fr.ecole2recte.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.*
import fr.ecole2recte.app.settings.PrivacySelection
import java.time.LocalDateTime

abstract class SmallWidget(private val kind: SmallWidgetKind) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(OneByOneSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = (id as? AppWidgetId)?.appWidgetId ?: 0
        val config = WidgetConfigStore.at(context).read(widgetId)
        val snapshot = instanceSnapshot(context, config)
        val data = SmallWidgetData.read(kind, snapshot, LocalDateTime.now(), System.currentTimeMillis(), PrivacySelection.mode(context))
        val colors = WidgetColors.load(context)
        provideContent {
            WidgetTheming(colors) {
                OneByOne(kind.icon, data.value, data.caption, widgetOpens(kind.tab, profile = config.profile), widgetVariant(), data.tone, data.detail, data.badge, widgetId)
            }
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        (glanceId as? AppWidgetId)?.let { WidgetConfigStore.at(context).remove(it.appWidgetId) }
        super.onDelete(context, glanceId)
    }
}

class SmallNextClassWidget : SmallWidget(SmallWidgetKind.NextClass)
class RemainingHomeworkWidget : SmallWidget(SmallWidgetKind.Homework)
class LastGradeWidget : SmallWidget(SmallWidgetKind.LastGrade)
class UnreadMessagesWidget : SmallWidget(SmallWidgetKind.Messages)
class ExamCountdownWidget : SmallWidget(SmallWidgetKind.Exam)

class SmallNextClassWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = SmallNextClassWidget() }
class RemainingHomeworkWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = RemainingHomeworkWidget() }
class LastGradeWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = LastGradeWidget() }
class UnreadMessagesWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = UnreadMessagesWidget() }
class ExamCountdownWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = ExamCountdownWidget() }
