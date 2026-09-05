package fr.ecole2recte.app.data

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SavedLogin(
    val identifier: String,
    val password: String,
    val trustedAnswer: TrustedAnswer? = null,
)

class SecureCredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences("ecole2recte_secure", Context.MODE_PRIVATE)
    private val keyAlias = "ecole2recte_credentials"

    fun read(): SavedLogin? = preferences.getString("login", null)?.let { encrypted ->
        runCatching {
            val json = JSONObject(decrypt(encrypted))
            SavedLogin(
                identifier = json.getString("identifier"),
                password = json.getString("password"),
                trustedAnswer = json.optJSONObject("trustedAnswer")?.let {
                    TrustedAnswer(it.getString("cn"), it.getString("cv"))
                },
            )
        }.getOrNull()
    }

    fun save(login: SavedLogin) {
        val json = JSONObject()
            .put("identifier", login.identifier)
            .put("password", login.password)
        login.trustedAnswer?.let {
            json.put("trustedAnswer", JSONObject().put("cn", it.cn).put("cv", it.cv))
        }
        preferences.edit().putString("login", encrypt(json.toString())).apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(android.security.keystore.KeyGenParameterSpec.Builder(
                keyAlias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build())
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), StandardCharsets.UTF_8)
    }
}
