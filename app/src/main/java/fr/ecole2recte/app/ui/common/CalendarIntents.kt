package fr.ecole2recte.app.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.domain.CalendarEventFields
import fr.ecole2recte.app.ui.theme.AppTheme

/**
 * Ouvre l'écran « nouvel événement » de l'application Calendrier, rempli. Aucune permission : rien
 * n'est écrit tant que l'utilisateur n'a pas enregistré l'événement lui-même.
 */
fun calendarInsertIntent(fields: CalendarEventFields): Intent =
    Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, fields.title)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, fields.beginMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, fields.endMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, fields.allDay)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, fields.location)
        .putExtra(CalendarContract.Events.DESCRIPTION, fields.description)

/** Lance l'intent ; faux quand aucune application Calendrier ne sait le traiter. */
fun openInCalendar(context: Context, fields: CalendarEventFields): Boolean = try {
    context.startActivity(calendarInsertIntent(fields).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (missing: ActivityNotFoundException) {
    false
}

/** « Ajouter au calendrier » : une ligne cliquable pour une fiche de cours ou de contrôle. */
@Composable
fun AddToCalendarRow(fields: CalendarEventFields?, modifier: Modifier = Modifier) {
    if (fields == null) return
    val context = LocalContext.current
    var problem by remember(fields) { mutableStateOf<String?>(null) }
    Column(modifier) {
        Text(
            "Ajouter au calendrier du téléphone",
            modifier = Modifier.fillMaxWidth().clickable {
                problem = if (openInCalendar(context, fields)) null else "Aucune application Calendrier n'a été trouvée sur ce téléphone."
            }.padding(vertical = 12.dp),
            color = AppTheme.colors.accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        problem?.let { Text(it, color = AppTheme.colors.error, fontSize = 13.sp) }
    }
}
