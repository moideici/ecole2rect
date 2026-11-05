package fr.ecole2recte.app.data

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Chiffre un texte pour le garder sur l'appareil. Abstrait pour pouvoir être remplacé dans les tests. */
interface ValueCipher {
    fun encrypt(value: String): String

    /** Lève une exception si le texte n'a pas été chiffré avec cette clé, ou a été altéré. */
    fun decrypt(value: String): String
}

/**
 * AES-GCM avec une clé du Keystore Android, qui ne quitte jamais l'appareil. La clé n'est **jamais**
 * liée à la biométrie : un changement d'empreinte la rendrait inutilisable et ferait perdre les comptes.
 */
class KeystoreCipher(private val keyAlias: String = "ecole2recte_credentials") : ValueCipher {
    @Volatile
    private var cachedKey: SecretKey? = null

    private fun secretKey(): SecretKey {
        cachedKey?.let { return it }
        synchronized(this) {
            cachedKey?.let { return it }
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = (keyStore.getKey(keyAlias, null) as? SecretKey) ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
                init(
                    android.security.keystore.KeyGenParameterSpec.Builder(
                        keyAlias,
                        android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                            android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setUserAuthenticationRequired(false)
                        .build(),
                )
            }.generateKey()
            cachedKey = key
            return key
        }
    }

    override fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    override fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), StandardCharsets.UTF_8)
    }
}
