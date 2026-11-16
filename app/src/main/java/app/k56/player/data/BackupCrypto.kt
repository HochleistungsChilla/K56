package app.k56.player.data

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-protects a settings backup with AES-256-GCM. The key is derived from
 * the password with PBKDF2 (random salt, embedded in the payload), so the same
 * password is required to import. Plain (unprotected) backups stay readable as
 * plain JSON and are detected separately.
 */
object BackupCrypto {

    private const val PREFIX = "k56enc1:"
    private const val ITERATIONS = 20_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private const val GCM_TAG_BITS = 128

    fun isEncrypted(payload: String): Boolean = payload.startsWith(PREFIX)

    fun encrypt(plain: String, password: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX +
            Base64.encodeToString(salt, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    /** Throws on a wrong password or a malformed payload. */
    fun decrypt(payload: String, password: String): String {
        val parts = payload.removePrefix(PREFIX).split(":")
        require(parts.size == 3) { "Invalid encrypted backup" }
        val salt = Base64.decode(parts[0], Base64.NO_WRAP)
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
