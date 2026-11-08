package fr.ecole2recte.app.settings

import android.content.Context
import fr.ecole2recte.app.domain.AveragePresentation

object AverageDecimals {
    const val KEY = "average_decimals"
    fun read(context: Context): Int = AveragePresentation.digits(context.getSharedPreferences(ThemeSelection.PREFS, Context.MODE_PRIVATE).getInt(KEY, 2))
}
