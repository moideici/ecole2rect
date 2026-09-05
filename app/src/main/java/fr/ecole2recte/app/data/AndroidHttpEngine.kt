package fr.ecole2recte.app.data

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
 */
class AndroidHttpEngine(
    private val connectTimeoutMillis: Int = 20_000,
    private val readTimeoutMillis: Int = 30_000,
) : HttpEngine {

    override fun execute(request: HttpRequest): HttpResponse {
        val connection = try {
            (URL(request.url).openConnection() as HttpURLConnection)
        } catch (error: IOException) {
            throw NetworkFailure("Adresse injoignable.", error)
        }
        return try {
            connection.requestMethod = request.method
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.instanceFollowRedirects = true
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            connection.doInput = true
            request.body?.let { body ->
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = connection.responseCode
            // Un code d'erreur laisse quand même un corps JSON exploitable : c'est là que
            // l'API explique ce qu'elle refuse.
            val stream = if (status in 200..399) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            HttpResponse(
                status = status,
                headers = connection.headerFields
                    .mapNotNull { (name, values) -> name?.let { it to values.orEmpty() } }
                    .toMap(),
                body = body,
            )
        } catch (error: IOException) {
            throw NetworkFailure("Connexion réseau impossible.", error)
        } finally {
            connection.disconnect()
        }
    }
}
