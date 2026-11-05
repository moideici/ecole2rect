package fr.ecole2recte.app.data.api

import fr.ecole2recte.app.data.EcoleDirecteClient
import org.json.JSONObject
import java.time.LocalDate

/**
 * Les appels métier : quel chemin, quel verbe et quel identifiant pour chaque module, et rien
 * d'autre. Le protocole est dans [EcoleDirecteClient], les lecteurs dans [Parsers].
 */
class EcoleDirecteApi(private val client: EcoleDirecteClient) {
    fun timetable(studentId: String, from: LocalDate, to: LocalDate): List<ApiLesson> =
        Parsers.timetable(
            client.call(
                "/E/$studentId/emploidutemps.awp",
                "get",
                body = JSONObject()
                    .put("dateDebut", from.toString())
                    .put("dateFin", to.toString())
                    // Les trous sont calculés côté application, à partir des cours.
                    .put("avecTrous", false),
            ).data,
        )

    fun homework(studentId: String): List<ApiHomework> =
        Parsers.homeworkSummary(client.call("/Eleves/$studentId/cahierdetexte.awp", "get").data)

    fun grades(studentId: String): ApiGrades =
        Parsers.grades(
            client.call(
                "/eleves/$studentId/notes.awp",
                "get",
                body = JSONObject().put("anneeScolaire", ""),
            ).data,
        )

    /**
     * Une page de la boîte de réception. C'est la boîte du **titulaire de la connexion** :
     * celle de la famille pour un parent, pas celle de son enfant.
     */
    fun messages(segment: String, mailboxId: String, page: Int = 0, perPage: Int = 50): List<ApiMessage> =
        Parsers.messages(
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
            ).data,
        )
}
