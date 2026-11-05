package fr.ecole2recte.app.data.cache

import java.io.File

/** À qui appartient une donnée en cache : un compte, et pour une famille l'élève consulté. */
data class CacheOwner(val identifier: String, val studentId: String?)

data class StoredResponse(val at: Long, val json: String)

/**
 * La dernière réponse valide de chaque ressource, gardée telle que le serveur l'a rendue.
 *
 * Stocker la réponse brute plutôt que le résultat de la lecture est volontaire : une correction
 * d'un lecteur s'applique aussitôt aux données déjà enregistrées, et écrire deux fois la même
 * réponse donne le même fichier (aucun doublon possible).
 */
interface ResponseCache {
    fun get(owner: CacheOwner, resource: String): StoredResponse?
    fun put(owner: CacheOwner, resource: String, json: String, at: Long)

    /** Efface tout ce qui appartient à ce compte (retrait du compte). */
    fun clear(identifier: String)
}

class InMemoryResponseCache : ResponseCache {
    private val entries = HashMap<Triple<String, String?, String>, StoredResponse>()

    override fun get(owner: CacheOwner, resource: String) =
        entries[Triple(owner.identifier.lowercase(), owner.studentId, resource)]

    override fun put(owner: CacheOwner, resource: String, json: String, at: Long) {
        entries[Triple(owner.identifier.lowercase(), owner.studentId, resource)] = StoredResponse(at, json)
    }

    override fun clear(identifier: String) {
        entries.keys.removeAll { it.first == identifier.lowercase() }
    }
}

/** Un fichier par ressource, dans le dossier privé de l'application (jamais sauvegardé dans le cloud). */
class FileResponseCache(private val root: File) : ResponseCache {
    private fun folder(owner: CacheOwner) =
        File(File(root, encode(owner.identifier)), owner.studentId?.let(::encode) ?: "-")

    private fun file(owner: CacheOwner, resource: String) = File(folder(owner), "$resource.json")

    override fun get(owner: CacheOwner, resource: String): StoredResponse? = runCatching {
        val text = file(owner, resource).takeIf { it.isFile }?.readText() ?: return null
        val newline = text.indexOf('\n')
        if (newline <= 0) return null
        StoredResponse(at = text.substring(0, newline).toLong(), json = text.substring(newline + 1))
    }.getOrNull()

    override fun put(owner: CacheOwner, resource: String, json: String, at: Long) {
        runCatching {
            val target = file(owner, resource)
            target.parentFile?.mkdirs()
            // Écriture atomique : un arrêt en plein milieu ne laisse jamais un fichier tronqué.
            val temporary = File(target.parentFile, "$resource.tmp")
            temporary.writeText("$at\n$json")
            if (!temporary.renameTo(target)) {
                target.delete()
                temporary.renameTo(target)
            }
        }
    }

    override fun clear(identifier: String) {
        File(root, encode(identifier)).deleteRecursively()
    }

    /** Nom de dossier sûr, quel que soit l'identifiant. */
    private fun encode(value: String): String =
        value.lowercase().toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
}
