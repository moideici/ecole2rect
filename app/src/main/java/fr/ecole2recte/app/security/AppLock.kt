package fr.ecole2recte.app.security

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Verrouillage de l'application par l'empreinte, le visage ou, depuis Android 11, le code de
 * l'appareil. Rien d'autre n'est stocké : c'est Android qui décide si l'utilisateur est bien là.
 */
object AppLock {
    /** Depuis Android 11 le code de l'appareil peut servir de secours ; avant, seule la biométrie est possible. */
    private val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    /** Vrai si l'appareil sait demander une authentification. Sinon, activer le verrou enfermerait l'utilisateur dehors. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        onSuccess: () -> Unit,
        onFailure: (message: String) -> Unit = {},
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailure(errString.toString())
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(authenticators)
            .apply {
                // Sans code de l'appareil en secours, Android exige un bouton pour annuler.
                if (authenticators and DEVICE_CREDENTIAL == 0) setNegativeButtonText("Annuler")
            }
            .build()
        prompt.authenticate(info)
    }
}

/** Retrouve l'activité qui contient une vue Compose. */
fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}
