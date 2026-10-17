package fr.ecole2recte.app.ui

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import fr.ecole2recte.app.ui.components.PrimaryButton
import fr.ecole2recte.app.ui.components.ScreenPadding

/**
 * Verrouillage de l'application.
 *
 * Un carnet de notes est une donnée personnelle : sur un téléphone prêté ou posé sur une
 * table, l'ouvrir doit demander la même chose que le téléphone lui-même. On s'appuie donc sur
 * l'empreinte ou le code de l'appareil — jamais sur un code propre à l'application, qui
 * serait un secret de plus à retenir et à mal protéger.
 *
 * Le verrou n'est qu'un rideau devant l'interface : les données restent chiffrées par le
 * Keystore, ni plus ni moins qu'avant.
 */
@Composable
fun BiometricGate(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    fun prompt() {
        val host = activity ?: return
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onUnlocked()
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("ecole2recte")
            .setSubtitle("Déverrouille pour voir tes données scolaires")
            .apply {
                // La combinaison biométrie + code de l'appareil n'est acceptée qu'à partir
                // d'Android 11 : en deçà, il faut un bouton d'annulation explicite.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                    )
                } else {
                    setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    setNegativeButtonText("Annuler")
                }
            }
            .build()
        runCatching {
            BiometricPrompt(host, ContextCompat.getMainExecutor(context), callback).authenticate(info)
        }
    }

    // Si l'appareil n'a plus de verrou configuré, garder l'application fermée reviendrait à
    // enfermer l'utilisateur dehors : on laisse alors entrer.
    LaunchedEffect(Unit) {
        if (!canLock(context)) onUnlocked() else prompt()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(ScreenPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "ecole2recte",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Application verrouillée",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Box(Modifier.width(220.dp)) {
                PrimaryButton(text = "Déverrouiller", onClick = { prompt() })
            }
        }
    }
}

/** Vrai si l'appareil sait vérifier une empreinte ou un code : sans quoi le verrou n'a pas de sens. */
fun canLock(context: android.content.Context): Boolean {
    val manager = BiometricManager.from(context)
    val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else {
        BiometricManager.Authenticators.BIOMETRIC_WEAK
    }
    return manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
}
