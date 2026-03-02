package app.logdate.client.device.storage

import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import java.util.prefs.Preferences
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class DesktopSecureStorage(
    baseDirectory: Path = Path.of(System.getProperty("user.home"), ".logdate"),
) : SecureStorage {
    private val keyPath = baseDirectory.resolve("secure-storage.key")
    private val preferences = Preferences.userRoot().node("app.logdate.secure")
    private val secretKey: SecretKey = loadOrCreateKey()

    private val valueCache = mutableMapOf<String, String>()
    private val valueCacheFlow = MutableStateFlow<Map<String, String>>(emptyMap())

    init {
        runCatching {
            preferences.keys().forEach { key ->
                val encrypted = preferences.get(key, null) ?: return@forEach
                val decrypted = decryptString(encrypted)
                if (decrypted != null) {
                    valueCache[key] = decrypted
                }
            }
        }.onFailure { Napier.w("Failed to load secure storage cache", it) }
        valueCacheFlow.value = valueCache.toMap()
    }

    override suspend fun getString(key: String): String? {
        val cached = valueCache[key]
        if (cached != null) {
            return cached
        }
        val encrypted = preferences.get(key, null) ?: return null
        val decrypted = decryptString(encrypted)
        if (decrypted != null) {
            valueCache[key] = decrypted
            valueCacheFlow.value = valueCache.toMap()
        }
        return decrypted
    }

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        val encrypted = encryptString(value) ?: return
        preferences.put(key, encrypted)
        valueCache[key] = value
        valueCacheFlow.value = valueCache.toMap()
    }

    override suspend fun remove(key: String) {
        preferences.remove(key)
        valueCache.remove(key)
        valueCacheFlow.value = valueCache.toMap()
    }

    override suspend fun clear() {
        preferences.clear()
        valueCache.clear()
        valueCacheFlow.value = emptyMap()
    }

    override fun observeString(key: String): Flow<String?> = valueCacheFlow.map { cache -> cache[key] }

    override fun observeAll(): Flow<Map<String, String>> = valueCacheFlow

    override suspend fun encrypt(data: ByteArray): ByteArray =
        runCatching { encryptBytes(data) }.getOrElse { error ->
            Napier.e("Failed to encrypt payload", error)
            null
        } ?: data

    override suspend fun decrypt(data: ByteArray): ByteArray? =
        runCatching { decryptBytes(data) }.getOrElse { error ->
            Napier.e("Failed to decrypt payload", error)
            null
        }

    private fun loadOrCreateKey(): SecretKey {
        return runCatching {
            if (Files.exists(keyPath)) {
                val keyBytes = Files.readAllBytes(keyPath)
                return@runCatching SecretKeySpec(keyBytes, "AES")
            }
            Files.createDirectories(keyPath.parent)
            val keyBytes = ByteArray(KEY_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
            Files.write(keyPath, keyBytes)
            runCatching {
                Files.setPosixFilePermissions(keyPath, PosixFilePermissions.fromString("rw-------"))
            }
            SecretKeySpec(keyBytes, "AES")
        }.getOrElse { error ->
            Napier.e("Failed to initialize secure storage key", error)
            val fallback = ByteArray(KEY_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
            SecretKeySpec(fallback, "AES")
        }
    }

    private fun encryptString(value: String): String? {
        val encrypted = encryptBytes(value.encodeToByteArray()) ?: return null
        return Base64.getEncoder().encodeToString(encrypted)
    }

    private fun decryptString(value: String): String? {
        val decoded = runCatching { Base64.getDecoder().decode(value) }.getOrNull() ?: return null
        val decrypted = decryptBytes(decoded) ?: return null
        return decrypted.decodeToString()
    }

    private fun encryptBytes(data: ByteArray): ByteArray? {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        return iv + encrypted
    }

    private fun decryptBytes(data: ByteArray): ByteArray? {
        if (data.size <= GCM_IV_LENGTH_BYTES) {
            return null
        }
        val iv = data.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = data.copyOfRange(GCM_IV_LENGTH_BYTES, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    companion object {
        private const val KEY_LENGTH_BYTES = 32
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
