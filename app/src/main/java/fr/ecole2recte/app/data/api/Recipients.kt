package fr.ecole2recte.app.data.api

import fr.ecole2recte.app.domain.Recipient
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** Lecture des contacts et mise en forme d'un message à envoyer. */
object Recipients {
    /**
     * Les contacts d'un groupe. Forme attendue (documentation de la communauté, non vérifiée sur
     * un vrai compte) : un tableau, ou un objet qui contient un tableau, d'objets `{id, nom, prenom, civilite, type}`.
     */
    fun parse(data: Any?, group: String, defaultType: String): List<Recipient> {
        val array = when (data) {
            is JSONArray -> data
            is JSONObject -> data.keys().asSequence().map { data.opt(it) }.filterIsInstance<JSONArray>().firstOrNull()
            else -> null
        } ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.mapNotNull { item ->
            val id = item.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: return@mapNotNull null
            val name = listOf(item.optString("civilite"), item.optString("prenom"), item.optString("nom"))
                .filter { it.isNotBlank() }.joinToString(" ").ifBlank { item.optString("libelle") }
            if (name.isBlank()) return@mapNotNull null
            Recipient(id, name, item.optString("type").ifBlank { defaultType }, group)
        }.distinctBy { it.group + it.id }.sortedBy { it.name.lowercase() }
    }

    /** Le texte devient du HTML sûr : rien de ce qui est tapé n'est interprété comme une balise. */
    fun toHtml(text: String): String =
        text.trim().replace("\r\n", "\n")
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\n", "<br>")

    fun encodedContent(text: String): String = Base64.getEncoder().encodeToString(toHtml(text).toByteArray(Charsets.UTF_8))
}
