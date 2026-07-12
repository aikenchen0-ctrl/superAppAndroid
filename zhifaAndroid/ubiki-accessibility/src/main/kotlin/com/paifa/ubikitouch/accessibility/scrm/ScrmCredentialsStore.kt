package com.paifa.ubikitouch.accessibility.scrm

import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val CredentialsFormatVersion = "1"
private const val FormatVersionKey = "format_version"
private const val BaseUrlKey = "base_url"
private const val ApiKeyCiphertextKey = "api_key_ciphertext"
private const val ApiKeyIvKey = "api_key_iv"
private const val AccountContextCiphertextKey = "account_context_ciphertext"
private const val AccountContextIvKey = "account_context_iv"

private val CredentialsJson = Json {
    ignoreUnknownKeys = false
    isLenient = false
}

@Serializable
private data class ScrmAccountContextPayload(
    val deviceUuid: String,
    val weChatId: String
)

internal data class ScrmStoredCredentials(
    val baseUrl: String,
    val apiKey: ScrmApiKey,
    val selectedDeviceUuid: String? = null,
    val selectedWeChatId: String? = null
)

internal class ScrmEncryptedSecret(
    ciphertext: ByteArray,
    iv: ByteArray
) {
    val ciphertext: ByteArray = ciphertext.copyOf()
    val iv: ByteArray = iv.copyOf()
}

internal interface ScrmSecretCipher {
    fun encrypt(plaintext: ByteArray): ScrmEncryptedSecret
    fun decrypt(secret: ScrmEncryptedSecret): ByteArray
}

internal interface ScrmCredentialStorage {
    fun read(key: String): String?
    fun replace(values: Map<String, String>): Boolean
    fun clear(): Boolean
}

internal interface ScrmCredentialRepository {
    fun save(
        baseUrl: String,
        apiKeyInput: String,
        selectedDeviceUuid: String? = null,
        selectedWeChatId: String? = null
    ): ScrmStoredCredentials
    fun load(): ScrmStoredCredentials?
    fun clear()
}

internal class ScrmCredentialStorageException(message: String) : ScrmException(message)

internal class ScrmCredentialCorruptedException(message: String) : ScrmException(message)

internal class ScrmCredentialsStore(
    private val storage: ScrmCredentialStorage,
    private val cipher: ScrmSecretCipher
) : ScrmCredentialRepository {
    override fun save(
        baseUrl: String,
        apiKeyInput: String,
        selectedDeviceUuid: String?,
        selectedWeChatId: String?
    ): ScrmStoredCredentials {
        require((selectedDeviceUuid == null) == (selectedWeChatId == null)) {
            "设备和微信账号必须同时选择"
        }
        require(selectedDeviceUuid == null || selectedDeviceUuid.isNotBlank()) {
            "deviceUuid 不能为空"
        }
        require(selectedWeChatId == null || selectedWeChatId.isNotBlank()) {
            "weChatId 不能为空"
        }
        val apiKey = ScrmApiKey.from(apiKeyInput)
        val config = ScrmApiConfig(baseUrl, apiKey)
        val encryptedApiKey = encrypt(
            value = apiKey.headerValue(),
            failureMessage = "SCRM API Key 加密失败"
        )
        val values = mutableMapOf(
            FormatVersionKey to CredentialsFormatVersion,
            BaseUrlKey to config.baseUrl,
            ApiKeyCiphertextKey to Base64.getEncoder().encodeToString(encryptedApiKey.ciphertext),
            ApiKeyIvKey to Base64.getEncoder().encodeToString(encryptedApiKey.iv)
        )
        if (selectedDeviceUuid != null && selectedWeChatId != null) {
            val contextJson = CredentialsJson.encodeToString(
                ScrmAccountContextPayload(selectedDeviceUuid, selectedWeChatId)
            )
            val encryptedContext = encrypt(
                value = contextJson,
                failureMessage = "SCRM 账号上下文加密失败"
            )
            values[AccountContextCiphertextKey] =
                Base64.getEncoder().encodeToString(encryptedContext.ciphertext)
            values[AccountContextIvKey] = Base64.getEncoder().encodeToString(encryptedContext.iv)
        }
        if (!storage.replace(values)) {
            throw ScrmCredentialStorageException("SCRM 配置保存失败")
        }
        return ScrmStoredCredentials(
            baseUrl = config.baseUrl,
            apiKey = apiKey,
            selectedDeviceUuid = selectedDeviceUuid,
            selectedWeChatId = selectedWeChatId
        )
    }

    override fun load(): ScrmStoredCredentials? {
        val requiredValues = mapOf(
            FormatVersionKey to storage.read(FormatVersionKey),
            BaseUrlKey to storage.read(BaseUrlKey),
            ApiKeyCiphertextKey to storage.read(ApiKeyCiphertextKey),
            ApiKeyIvKey to storage.read(ApiKeyIvKey)
        )
        val contextCiphertext = storage.read(AccountContextCiphertextKey)
        val contextIv = storage.read(AccountContextIvKey)
        if (requiredValues.values.all { it == null } && contextCiphertext == null && contextIv == null) {
            return null
        }
        if (requiredValues.values.any { it == null }) {
            throw ScrmCredentialCorruptedException("SCRM 加密配置不完整，请重新配置")
        }
        if ((contextCiphertext == null) != (contextIv == null)) {
            throw ScrmCredentialCorruptedException("SCRM 账号上下文不完整，请重新选择账号")
        }
        if (requiredValues.getValue(FormatVersionKey) != CredentialsFormatVersion) {
            throw ScrmCredentialCorruptedException("SCRM 加密配置版本不受支持，请重新配置")
        }

        val decrypted = decrypt(
            ciphertext = requireNotNull(requiredValues.getValue(ApiKeyCiphertextKey)),
            iv = requireNotNull(requiredValues.getValue(ApiKeyIvKey)),
            failureMessage = "SCRM API Key 解密失败，请重新配置"
        )

        return try {
            val apiKey = ScrmApiKey.from(String(decrypted, StandardCharsets.UTF_8))
            val config = ScrmApiConfig(requireNotNull(requiredValues.getValue(BaseUrlKey)), apiKey)
            val selectedContext = if (contextCiphertext != null && contextIv != null) {
                decryptAccountContext(contextCiphertext, contextIv)
            } else {
                null
            }
            ScrmStoredCredentials(
                baseUrl = config.baseUrl,
                apiKey = apiKey,
                selectedDeviceUuid = selectedContext?.deviceUuid,
                selectedWeChatId = selectedContext?.weChatId
            )
        } catch (_: Exception) {
            throw ScrmCredentialCorruptedException("SCRM 加密配置无效，请重新配置")
        } finally {
            decrypted.fill(0)
        }
    }

    override fun clear() {
        if (!storage.clear()) {
            throw ScrmCredentialStorageException("SCRM 配置清除失败")
        }
    }

    private fun encrypt(value: String, failureMessage: String): ScrmEncryptedSecret {
        val plaintext = value.toByteArray(StandardCharsets.UTF_8)
        return try {
            cipher.encrypt(plaintext)
        } catch (_: Exception) {
            throw ScrmCredentialStorageException(failureMessage)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun decrypt(
        ciphertext: String,
        iv: String,
        failureMessage: String
    ): ByteArray {
        return try {
            cipher.decrypt(
                ScrmEncryptedSecret(
                    ciphertext = Base64.getDecoder().decode(ciphertext),
                    iv = Base64.getDecoder().decode(iv)
                )
            )
        } catch (_: Exception) {
            throw ScrmCredentialCorruptedException(failureMessage)
        }
    }

    private fun decryptAccountContext(ciphertext: String, iv: String): ScrmAccountContextPayload {
        val plaintext = decrypt(
            ciphertext = ciphertext,
            iv = iv,
            failureMessage = "SCRM 账号上下文解密失败，请重新选择账号"
        )
        return try {
            CredentialsJson.decodeFromString(String(plaintext, StandardCharsets.UTF_8))
        } finally {
            plaintext.fill(0)
        }
    }
}
