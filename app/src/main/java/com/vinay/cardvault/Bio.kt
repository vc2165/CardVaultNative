package com.vinay.cardvault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Fingerprint unlock. A key inside the phone's secure hardware (Android Keystore) wraps the vault key.
 * That hardware key can only be used right after a successful fingerprint check, and Android destroys it
 * if a new fingerprint is added to the phone.
 */
object Bio {
    private const val ALIAS = "cardvault.bio.v1"
    private const val STORE = "AndroidKeyStore"

    private fun keyStore() = KeyStore.getInstance(STORE).apply { load(null) }

    private fun key(create: Boolean): SecretKey? {
        val ks = keyStore()
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        if (!create) return null
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build(),
        )
        return gen.generateKey()
    }

    /** Cipher to wrap the vault key (used after the fingerprint check). */
    fun encryptCipher(): Cipher {
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(true)) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            delete()
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(true)) }
        }
    }

    /** Cipher to unwrap the vault key, or null if the hardware key is gone (e.g. a new fingerprint was added). */
    fun decryptCipher(iv: ByteArray): Cipher? = try {
        val k = key(false) ?: return null
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, iv)) }
    } catch (e: KeyPermanentlyInvalidatedException) {
        delete(); null
    } catch (e: Exception) {
        null
    }

    fun delete() {
        try { keyStore().deleteEntry(ALIAS) } catch (_: Exception) {}
    }
}
