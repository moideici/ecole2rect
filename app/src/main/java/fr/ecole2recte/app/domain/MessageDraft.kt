package fr.ecole2recte.app.domain

/** Quelqu'un à qui l'on peut écrire : un enseignant ou un membre de l'administration. */
data class Recipient(val id: String, val name: String, val type: String, val group: String)

/** Un message en cours de rédaction. Rien n'est envoyé tant qu'il n'a pas été relu et confirmé. */
data class MessageDraft(val recipients: List<Recipient>, val subject: String, val body: String) {
    /** Ce qui empêche d'envoyer, en français ; vide quand le message est prêt à être relu. */
    fun problems(): List<String> = buildList {
        if (recipients.isEmpty()) add("Choisis au moins un destinataire.")
        if (recipients.size > MAX_RECIPIENTS) add("Au plus $MAX_RECIPIENTS destinataires.")
        if (subject.isBlank()) add("Ajoute un objet.")
        if (subject.length > MAX_SUBJECT) add("L'objet est trop long ($MAX_SUBJECT caractères au plus).")
        if (body.isBlank()) add("Écris ton message.")
        if (body.length > MAX_BODY) add("Le message est trop long ($MAX_BODY caractères au plus).")
    }

    val isReady: Boolean get() = problems().isEmpty()

    /**
     * Ce que l'écran de confirmation montre mot pour mot avant l'envoi : tous les destinataires,
     * l'objet et le texte. Ce qui s'affiche ici est exactement ce qui part.
     */
    fun confirmation(): String = buildString {
        appendLine("À : " + recipients.joinToString(", ") { it.name })
        appendLine("Objet : " + subject.trim())
        appendLine()
        append(body.trim())
    }

    companion object {
        const val MAX_RECIPIENTS = 20
        const val MAX_SUBJECT = 120
        const val MAX_BODY = 10_000
    }
}
