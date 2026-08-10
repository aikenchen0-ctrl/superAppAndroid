package com.paifa.ubikitouch.accessibility.scrm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrmCredentialsStoreTest {
    @Test
    fun saveAndLoadRoundTripsCredentialsWithoutPersistingPlaintextKey() {
        val storage = InMemoryStorage()
        val store = ScrmCredentialsStore(storage, XorSecretCipher())
        val rawKey = "scrm_test_secret_1234"

        val saved = store.save("https://api.example.com/", rawKey)
        val loaded = store.load()

        assertEquals("https://api.example.com/openapi/v1", saved.baseUrl)
        assertEquals("https://api.example.com/openapi/v1", loaded?.baseUrl)
        assertEquals("****1234", loaded?.apiKey?.masked)
        assertEquals(rawKey, loaded?.apiKey?.headerValue())
        assertFalse(storage.values.values.any { it.contains(rawKey) })
        assertTrue(storage.values.containsKey("api_key_ciphertext"))
        assertTrue(storage.values.containsKey("api_key_iv"))
        assertEquals("1", storage.values["format_version"])
    }

    @Test
    fun selectedAccountContextIsEncryptedAndRoundTripsWithCredentials() {
        val storage = InMemoryStorage()
        val store = ScrmCredentialsStore(storage, XorSecretCipher())

        store.save(
            baseUrl = "https://api.example.com",
            apiKeyInput = "scrm_test_secret_1234",
            selectedDeviceUuid = "device-sensitive",
            selectedWeChatId = "wxid_sensitive"
        )
        val loaded = store.load()

        assertEquals("device-sensitive", loaded?.selectedDeviceUuid)
        assertEquals("wxid_sensitive", loaded?.selectedWeChatId)
        assertFalse(storage.values.values.any { it.contains("device-sensitive") })
        assertFalse(storage.values.values.any { it.contains("wxid_sensitive") })
        assertTrue(storage.values.containsKey("account_context_ciphertext"))
        assertTrue(storage.values.containsKey("account_context_iv"))
    }

    @Test
    fun loadReturnsNullOnlyWhenNoCredentialFieldsExist() {
        val store = ScrmCredentialsStore(InMemoryStorage(), XorSecretCipher())

        assertNull(store.load())
    }

    @Test
    fun partialOrUndecryptableCredentialsFailExplicitly() {
        val partialStorage = InMemoryStorage(
            mutableMapOf("base_url" to "https://api.example.com/openapi/v1")
        )
        val partialResult = runCatching {
            ScrmCredentialsStore(partialStorage, XorSecretCipher()).load()
        }

        assertTrue(partialResult.exceptionOrNull() is ScrmCredentialCorruptedException)

        val encryptedStorage = InMemoryStorage()
        ScrmCredentialsStore(encryptedStorage, XorSecretCipher()).save(
            "https://api.example.com",
            "scrm_test_secret_1234"
        )
        val decryptResult = runCatching {
            ScrmCredentialsStore(encryptedStorage, FailingDecryptCipher()).load()
        }

        assertTrue(decryptResult.exceptionOrNull() is ScrmCredentialCorruptedException)
        assertFalse(decryptResult.exceptionOrNull().toString().contains("scrm_test_secret_1234"))
    }

    @Test
    fun storageWriteFailureIsNotReportedAsSuccess() {
        val storage = InMemoryStorage(writeSucceeds = false)
        val store = ScrmCredentialsStore(storage, XorSecretCipher())

        val result = runCatching {
            store.save("https://api.example.com", "scrm_test_secret_1234")
        }

        assertTrue(result.exceptionOrNull() is ScrmCredentialStorageException)
    }

    @Test
    fun clearRemovesEveryCredentialField() {
        val storage = InMemoryStorage()
        val store = ScrmCredentialsStore(storage, XorSecretCipher())
        store.save("https://api.example.com", "scrm_test_secret_1234")

        store.clear()

        assertTrue(storage.values.isEmpty())
        assertNull(store.load())
    }

    private class InMemoryStorage(
        val values: MutableMap<String, String> = mutableMapOf(),
        private val writeSucceeds: Boolean = true
    ) : ScrmCredentialStorage {
        override fun read(key: String): String? = values[key]

        override fun replace(values: Map<String, String>): Boolean {
            if (!writeSucceeds) return false
            this.values.clear()
            this.values.putAll(values)
            return true
        }

        override fun clear(): Boolean {
            values.clear()
            return true
        }
    }

    private class XorSecretCipher : ScrmSecretCipher {
        override fun encrypt(plaintext: ByteArray): ScrmEncryptedSecret {
            return ScrmEncryptedSecret(
                ciphertext = plaintext.map { (it.toInt() xor 0x5A).toByte() }.toByteArray(),
                iv = byteArrayOf(1, 2, 3, 4)
            )
        }

        override fun decrypt(secret: ScrmEncryptedSecret): ByteArray {
            return secret.ciphertext.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        }
    }

    private class FailingDecryptCipher : ScrmSecretCipher {
        override fun encrypt(plaintext: ByteArray): ScrmEncryptedSecret {
            error("not used")
        }

        override fun decrypt(secret: ScrmEncryptedSecret): ByteArray {
            throw IllegalStateException("key invalidated")
        }
    }
}
