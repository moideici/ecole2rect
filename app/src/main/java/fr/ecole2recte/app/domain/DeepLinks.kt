package fr.ecole2recte.app.domain

import java.net.URI
import java.net.URLDecoder

data class NavigationTarget(val tab: String, val subject: String? = null)

/** Analyse avant navigation : aucune URI extérieure ne devient une route arbitraire. */
object DeepLinks {
    fun feature(value: String?): NavigationTarget? = when (value?.trim()?.lowercase(java.util.Locale.FRANCE)) {
        "agenda", "emploi du temps" -> NavigationTarget("agenda")
        "devoirs", "travail à faire" -> NavigationTarget("agenda_homework")
        "notes", "moyennes" -> NavigationTarget("notes")
        "messages", "messagerie" -> NavigationTarget("messages")
        else -> null
    }
    fun subject(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() && it.length <= 256 && '\uFFFD' !in it && it.none(Char::isISOControl) }
    fun parse(raw: String?): NavigationTarget? = runCatching {
        val uri = URI(raw ?: return null)
        if (uri.scheme != "ecole2recte" || uri.rawUserInfo != null || uri.port != -1 || uri.rawFragment != null || uri.rawPath !in listOf(null, "", "/")) return null
        val tab = when (uri.host) {
            "notes" -> "notes"
            "agenda" -> "agenda"
            "devoirs" -> "agenda_homework"
            "messages" -> "messages"
            else -> return null
        }
        val query = uri.rawQuery?.split('&')?.map {
            val pair = it.split('=', limit = 2)
            if (pair.size != 2) return null
            URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
        }.orEmpty()
        if (query.any { it.first != "matiere" } || query.size > 1 || (query.isNotEmpty() && tab != "notes")) return null
        val subject = query.firstOrNull()?.second?.let { subject(it) ?: return null }
        NavigationTarget(tab, subject)
    }.getOrNull()
}
