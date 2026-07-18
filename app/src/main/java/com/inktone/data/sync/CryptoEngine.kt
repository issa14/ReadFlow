package com.inktone.data.sync

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Moteur de chiffrement/déchiffrement AES-256-GCM avec dérivation PBKDF2.
 *
 * Format du payload chiffré : [salt:16][iv:12][ciphertext]
 * - salt  : 16 octets (PBKDF2)
 * - iv    : 12 octets (GCM, recommandé)
 * - data  : chiffré + tag GCM (16 octets)
 */
object CryptoEngine {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "AES"
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 10_000
    private const val KEY_SIZE = 256
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_SIZE = 128 // bits

    private val secureRandom = SecureRandom()

    /**
     * Chiffre [plainText] avec le mot de passe [password].
     * @return payload binaire [salt + iv + ciphertext]
     */
    fun encrypt(plainText: String, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_SIZE).also { secureRandom.nextBytes(it) }
        val secretKey = deriveKey(password, salt)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv // GCM génère son IV automatiquement
        val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return salt + iv + ciphertext
    }

    /**
     * Déchiffre [encryptedData] (format [salt + iv + ciphertext]).
     * @return le texte clair original.
     */
    fun decrypt(encryptedData: ByteArray, password: CharArray): String {
        require(encryptedData.size > SALT_SIZE + IV_SIZE) {
            "Données chiffrées invalides ou corrompues"
        }
        val salt = encryptedData.copyOfRange(0, SALT_SIZE)
        val iv = encryptedData.copyOfRange(SALT_SIZE, SALT_SIZE + IV_SIZE)
        val ciphertext = encryptedData.copyOfRange(SALT_SIZE + IV_SIZE, encryptedData.size)
        val secretKey = deriveKey(password, salt)
        val spec = GCMParameterSpec(TAG_SIZE, iv)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    /**
     * Encode le payload chiffré en Base64 pour le transport HTTP.
     */
    fun encryptToBase64(plainText: String, password: CharArray): String {
        return Base64.encodeToString(encrypt(plainText, password), Base64.NO_WRAP)
    }

    /**
     * Décode le Base64 puis déchiffre.
     */
    fun decryptFromBase64(base64Data: String, password: CharArray): String {
        val raw = Base64.decode(base64Data, Base64.NO_WRAP)
        return decrypt(raw, password)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_SIZE)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, KEY_ALGORITHM)
    }
}
