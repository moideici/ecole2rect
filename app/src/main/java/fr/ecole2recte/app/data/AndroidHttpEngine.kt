package fr.ecole2recte.app.data

import fr.ecole2recte.app.core.protocol.HttpBinaryResponse
import fr.ecole2recte.app.core.protocol.HttpEngine
import fr.ecole2recte.app.core.protocol.HttpRequest
import fr.ecole2recte.app.core.protocol.HttpResponse
import fr.ecole2recte.app.core.protocol.NetworkFailure
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Implémentation Android du transport, sur `HttpURLConnection`.
 *
 * Aucune bibliothèque tierce : le protocole est simple, et la seule contrainte réelle est de
 * contrôler finement les en-têtes et les cookies, ce que la classe de la plateforme fait très
 * bien. Le corps est déjà encodé par le client ; ici, on ne fait que le transmettre.
 *
 * Une précaution qui ne se voit pas : **ne pas** appeler `disconnect()` quand la réponse a été
 * lue jusqu'au bout. Sur Android, `disconnect()` évince la socket du pool de connexions ;
 * l'appeler dans un `finally` rouvrait donc une poignée de main TLS à chaque requête, soit
 * neuf par synchronisation au lieu d'une — des secondes de radio allumée, et de la batterie,
 * pour rien. Fermer le flux suffit : la socket retourne au pool d'elle-même. On ne coupe que
 * sur panne, là où rien n'a été lu et où la connexion n'est plus réutilisable.
 */
class AndroidHttpEngine(
    private val connectTimeoutMillis: Int = 20_000,
    private val readTimeoutMillis: Int = 30_000,
) : HttpEngine {

    override fun download(request: HttpRequest): HttpBinaryResponse {
        val connection = open(request)
        return try {
            write(connection, request)
            val status = connection.responseCode
            val stream = if (status in 200..399) connection.inputStream else connection.errorStream
            HttpBinaryResponse(
                status = status,
                headers = headers(connection),
                bytes = stream?.use { it.readBytes() } ?: ByteArray(0),
            )
        } catch (error: IOException) {
            // Une réponse jamais lue ne peut pas revenir au pool : là, il faut fermer.
            connection.disconnect()
            throw NetworkFailure("Téléchargement interrompu.", error)
        }
    }

    override fun execute(request: HttpRequest): HttpResponse {
        val connection = open(request)
        return try {
            write(connection, request)
            val status = connection.responseCode
            // Un code d'erreur laisse quand même un corps JSON exploitable : c'est là que
            // l'API explique ce qu'elle refuse.
            val stream = if (status in 200..399) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            HttpResponse(status = status, headers = headers(connection), body = body)
        } catch (error: IOException) {
            connection.disconnect()
            throw NetworkFailure("Connexion réseau impossible.", error)
        }
    }

    private fun open(request: HttpRequest): HttpURLConnection = try {
        (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = request.method
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            instanceFollowRedirects = true
            request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
            doInput = true
        }
    } catch (error: IOException) {
        throw NetworkFailure("Adresse injoignable.", error)
    }

    private fun write(connection: HttpURLConnection, request: HttpRequest) {
        val body = request.body ?: return
        connection.doOutput = true
        connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
    }

    private fun headers(connection: HttpURLConnection): Map<String, List<String>> =
        connection.headerFields
            .mapNotNull { (name, values) -> name?.let { it to values.orEmpty() } }
            .toMap()
}
