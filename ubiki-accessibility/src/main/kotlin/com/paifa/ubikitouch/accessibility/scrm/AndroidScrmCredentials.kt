package com.paifa.ubikitouch.accessibility.scrm

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ScrmCredentialsPreferences = "scrm_credentials"
private const val ScrmKeystoreProvider = "AndroidKeyStore"
private const val ScrmKeyAlias = "scrm_openapi_credentials_v1"
private const val ScrmCipherTransformation = "AES/GCM/NoPadding"
private const val GcmTagLengthBits = 128

internal fun createAndroidScrmCredentialsStore(context: Context): ScrmCredentialsStore {
    val preferences = context.applicationContext.getSharedPreferences(
        ScrmCredentialsPreferences,
        Context.MODE_PRIVATE
    )
    return ScrmCredentialsStore(
        storage = SharedPreferencesScrmCredentialStorage(preferences),
        cipher = AndroidKeystoreScrmSecretCipher()
    )
}

private class SharedPreferencesScrmCredentialStorage(
    private val preferences: SharedPreferences
) : ScrmCredentialStorage {
    override fun read(key: String): String? = preferences.getString(key, null)

    override fun replace(values: Map<String, String>): Boolean {
        val editor = preferences.edit().clear()
        values.forEach { (key, value) -> editor.putString(key, value) }
        return editor.commit()
    }

    override fun clear(): Boolean = preferences.edit().clear().commit()
}

private class AndroidKeystoreScrmSecretCipher : ScrmSecretCipher {
    @Synchronized
    override fun encrypt(plaintext: ByteArray): ScrmEncryptedSecret {
        val cipher = Cipher.getInstance(ScrmCipherTransformation)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return ScrmEncryptedSecret(
            ciphertext = cipher.doFinal(plaintext),
            iv = cipher.iv
        )
    }

    @Synchronized
    override fun decrypt(secret: ScrmEncryptedSecret): ByteArray {
        val keyStore = loadKeyStore()
        val key = keyStore.getKey(ScrmKeyAlias, null) as? SecretKey
            ?: throw IllegalStateException("SCRM Keystore key is missing")
        val cipher = Cipher.getInstance(ScrmCipherTransformation)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(GcmTagLengthBits, secret.iv)
        )
        return cipher.doFinal(secret.ciphertext)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = loadKeyStore()
        val existing = keyStore.getKey(ScrmKeyAlias, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ScrmKeystoreProvider
        )
        val specification = KeyGenParameterSpec.Builder(
            ScrmKeyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(specification)
        return keyGenerator.generateKey()
    }

    private fun loadKeyStore(): KeyStore {
        return KeyStore.getInstance(ScrmKeystoreProvider).apply { load(null) }
    }
}
