package fr.ecole2recte.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import fr.ecole2recte.app.core.protocol.TrustedAnswer
import fr.ecole2recte.app.core.store.CredentialStore
import fr.ecole2recte.app.core.store.SavedCredentials
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stockage chiffré des identifiants, clé matérielle du Keystore Android.
 *
 * L'API ne délivre pas de jeton de rafraîchissement : reconnecter en silence impose de
 * conserver le mot de passe. Il est donc chiffré en AES/GCM avec une clé qui ne quitte
 * jamais le Keystore, et n'est jamais recopié dans un journal, un diagnostic ou une
 * sauvegarde (`allowBackup=false` dans le manifeste).
 */
class SecureCredentialStore(context: Context) : CredentialStore {

    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun read(): SavedCredentials? {
        val encrypted = preferences.getString(KEY_LOGIN, null) ?: return null
        return runCatching {
            val json = JSONObject(decrypt(encrypted))
            SavedCredentials(
                identifier = json.getString("identifier"),
                password = json.getString("password"),
                trustedAnswer = json.optJSONObject("trustedAnswer")?.let {
                    TrustedAnswer(it.getString("cn"), it.getString("cv"))
                },
            )
        }.getOrElse {
            // Clé invalidée (restauration d'appareil, réinitialisation biométrique) : le
            // contenu est illisible pour toujours, autant repartir proprement.
            clear()
            null
        }
    }

    override fun save(credentials: SavedCredentials) {
        val json = JSONObject()
            .put("identifier", credentials.identifier)
            .put("password", credentials.password)
        credentials.trustedAnswer?.let {
            json.put("trustedAnswer", JSONObject().put("cn", it.cn).put("cv", it.cv))
        }
        runCatching { encrypt(json.toString()) }
            .onSuccess { preferences.edit().putString(KEY_LOGIN, it).apply() }
    }

    override fun clear() {
        preferences.edit().clear().apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(TAG_LENGTH_BITS, bytes.copyOfRange(0, IV_LENGTH)),
        )
        return String(cipher.doFinal(bytes.copyOfRange(IV_LENGTH, bytes.size)), StandardCharsets.UTF_8)
    }

    private companion object {
        const val FILE_NAME = "ecole2recte_secure"
        const val KEY_LOGIN = "login"
        const val KEY_ALIAS = "ecole2recte_credentials"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }
}
