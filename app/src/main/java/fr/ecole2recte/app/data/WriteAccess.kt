package fr.ecole2recte.app.data

/**
 * Ce que l'utilisateur a autorisé à écrire chez ÉcoleDirecte. Par défaut : rien, l'application ne fait
 * que lire. Chaque capacité se règle à part dans Plus et demande une confirmation à la première activation.
 */
interface WriteAccess {
    fun homeworkSync(): Boolean
    fun messageSending(): Boolean
}

/** Aucune écriture : la valeur de tout ce qui n'a pas reçu d'autorisation explicite (tests, synchro de fond). */
object NoWrites : WriteAccess {
    override fun homeworkSync() = false
    override fun messageSending() = false
}
