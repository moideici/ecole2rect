package fr.ecole2recte.app.data.api

import fr.ecole2recte.app.data.ApiReply
import fr.ecole2recte.app.data.EcoleDirecteClient
import org.json.JSONObject
import java.time.LocalDate

/**
 * Les appels métier : quel chemin, quel verbe et quel identifiant pour chaque module, et rien
 * d'autre. Le protocole est dans [EcoleDirecteClient], les lecteurs dans [Parsers].
 */
class EcoleDirecteApi(private val client: EcoleDirecteClient) {
    private fun ApiReply.raw(): String = data?.toString() ?: "null"

    fun timetable(studentId: String, from: LocalDate, to: LocalDate): String =
        client.call(
            "/E/$studentId/emploidutemps.awp",
            "get",
            body = JSONObject()
                .put("dateDebut", from.toString())
                .put("dateFin", to.toString())
                // Les trous sont calculés côté application, à partir des cours.
                .put("avecTrous", false),
        ).raw()

    fun homework(studentId: String): String =
        client.call("/Eleves/$studentId/cahierdetexte.awp", "get").raw()

    /** Le détail d'un jour : les énoncés et le contenu de séance, absents du résumé. */
    fun dayContent(studentId: String, date: String): String =
        client.call("/Eleves/$studentId/cahierdetexte/$date.awp", "get").raw()

    fun grades(studentId: String): String =
        client.call(
            "/eleves/$studentId/notes.awp",
            "get",
            body = JSONObject().put("anneeScolaire", ""),
        ).raw()

    fun schoolLife(studentId: String): String =
        client.call("/eleves/$studentId/viescolaire.awp", "get").raw()

    /**
     * Une page de la boîte de réception. C'est la boîte du **titulaire de la connexion** :
     * celle de la famille pour un parent, pas celle de son enfant.
     */
    fun messages(segment: String, mailboxId: String, page: Int = 0, perPage: Int = 50): String =
        client.call(
                "/$segment/$mailboxId/messages.awp",
                "get",
                query = mapOf(
                    "force" to "false",
                    "typeRecuperation" to "received",
                    "orderBy" to "date",
                    "order" to "desc",
                    "page" to page.toString(),
                    "itemsPerPage" to perPage.toString(),
                    "onlyRead" to "false",
                    "query" to "",
                ),
                body = JSONObject().put("anneeMessages", ""),
        ).raw()

    /**
     * Le corps d'un message. Cet appel est celui du site : il **marque le message lu** côté
     * ÉcoleDirecte. Le `mode` dit de quel côté on se place — destinataire pour les messages reçus.
     */
    /**
     * Une pièce jointe, en lecture seule. ÉcoleDirecte distingue le type de fichier (`FICHIER_CDT`
     * pour le cahier de textes, `PIECE_JOINTE` pour la messagerie) ; le type annoncé par la liste est
     * utilisé tel quel, sinon celui de l'endroit d'où vient la pièce.
     */
    fun attachment(id: String, announcedType: String, source: fr.ecole2recte.app.domain.AttachmentSource): EcoleDirecteClient.Download {
        val fallback = if (source == fr.ecole2recte.app.domain.AttachmentSource.Homework) "FICHIER_CDT" else "PIECE_JOINTE"
        val type = announcedType.takeIf { it.isNotBlank() && !it.equals("FICHIER", ignoreCase = true) } ?: fallback
        val query = linkedMapOf("fichierId" to id, "leTypeDeFichier" to type)
        if (source == fr.ecole2recte.app.domain.AttachmentSource.Message) query["anneeMessages"] = ""
        return client.download("/telechargement.awp", query)
    }

    fun messageDetail(segment: String, mailboxId: String, id: String): String =
        client.call("/$segment/$mailboxId/messages/$id.awp", "get", query = mapOf("mode" to "destinataire")).raw()
}
