package fr.ecole2recte.app.widget

/** Seulement les extras des widgets ; aucune URI distante n'est interprétée. */
data class WidgetDestination(val tab: String, val profile: String, val subject: String) {
    companion object {
        fun parse(tab: String?, profile: String?, subject: String?): WidgetDestination? {
            val route = if (tab == "grades") "notes" else tab
            if (route !in setOf("accueil", "agenda", "agenda_homework", "notes", "messages", "plus", "recherche")) return null
            return WidgetDestination(route!!,
                profile?.takeIf { it.length <= 300 && !it.contains('\n') }?.lowercase().orEmpty(),
                if (route == "notes") subject?.takeIf { it.length <= 300 && !it.contains('\n') }.orEmpty() else "")
        }
    }
}
