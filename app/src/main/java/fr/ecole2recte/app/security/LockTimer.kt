package fr.ecole2recte.app.security

/** Délai d'absence après lequel l'application se reverrouille quand on y revient. */
enum class LockDelay(val label: String, val millis: Long) {
    Immediate("Immédiatement", 0L),
    OneMinute("Après 1 minute", 60_000L),
    FiveMinutes("Après 5 minutes", 5 * 60_000L),
    FifteenMinutes("Après 15 minutes", 15 * 60_000L),
    ;

    companion object {
        val Default = OneMinute
        fun parse(name: String?): LockDelay = entries.firstOrNull { it.name == name } ?: Default
    }
}

/**
 * Décide si le verrou doit revenir quand l'application repasse au premier plan.
 * L'horloge est injectée (en production : `SystemClock.elapsedRealtime`, qui ne dépend pas de l'heure
 * affichée) pour que le délai soit testable sans attendre.
 */
class LockTimer(private val clock: () -> Long) {
    private var leftAt: Long? = null
    private var authenticating = false

    /** L'application quitte le premier plan. Ignoré pendant une authentification (l'écran du code peut passer devant). */
    fun onBackground() {
        if (!authenticating) leftAt = clock()
    }

    /**
     * L'application revient au premier plan : vrai si le verrou doit être redemandé. L'absence est
     * effacée dans tous les cas : un seul retour, une seule décision.
     */
    fun onForeground(lockEnabled: Boolean, delay: LockDelay): Boolean {
        val left = leftAt
        leftAt = null
        if (!lockEnabled || authenticating || left == null) return false
        return clock() - left >= delay.millis
    }

    /** Une authentification est en cours : ne pas se reverrouiller à cause d'elle. */
    fun beginAuthentication() {
        authenticating = true
    }

    fun endAuthentication() {
        authenticating = false
        // Le retour de l'écran d'authentification ne compte pas comme une absence.
        leftAt = null
    }
}
