package app.logdate.client.device.storage

import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import java.util.prefs.Preferences
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class DesktopSecureStorage(
    baseDirectory: Path = Path.of(System.getProperty("user.home"), ".logdate"),
) : SecureStorage,
    DesktopFileCipher {
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
        val encrypted = encryptString(value)
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

    override suspend fun encrypt(data: ByteArray): ByteArray = encryptBytes(data)

    override suspend fun decrypt(data: ByteArray): ByteArray? =
        runCatching { decryptBytes(data) }.getOrElse { error ->
            Napier.e("Failed to decrypt payload", error)
            null
        }

    /**
     * Loads the device key, creating it only when none exists. An existing key that cannot be read
     * stops startup: replacing it would make everything already encrypted unreadable.
     */
    private fun loadOrCreateKey(): SecretKey {
        if (Files.exists(keyPath)) {
            val keyBytes = Files.readAllBytes(keyPath)
            check(keyBytes.size == KEY_LENGTH_BYTES) { "The device key at $keyPath is damaged (${keyBytes.size} bytes)" }
            return SecretKeySpec(keyBytes, "AES")
        }
        Files.createDirectories(keyPath.parent)
        val keyBytes = ByteArray(KEY_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        Files.write(keyPath, keyBytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.SYNC)
        runCatching {
            Files.setPosixFilePermissions(keyPath, PosixFilePermissions.fromString("rw-------"))
        }.onFailure { Napier.w("The device key permissions could not be restricted on this filesystem", it) }
        return SecretKeySpec(keyBytes, "AES")
    }

    override fun encryptFile(
        source: Path,
        target: Path,
    ) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        FileOutputStream(target.toFile()).use { file ->
            file.write(cipher.iv)
            CipherOutputStream(NonClosingOutputStream(file), cipher).use { encrypted ->
                Files.newInputStream(source).use { plain -> plain.copyTo(encrypted, FILE_BUFFER_BYTES) }
            }
            file.fd.sync()
        }
    }

    override fun decryptFile(
        source: Path,
        target: Path,
    ) {
        Files.newInputStream(source).buffered(FILE_BUFFER_BYTES).use { sealed ->
            val iv = sealed.readNBytes(GCM_IV_LENGTH_BYTES)
            check(iv.size == GCM_IV_LENGTH_BYTES) { "The encrypted file at $source is too short" }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            FileOutputStream(target.toFile()).use { file ->
                // The final block verifies the authentication tag, so a wrong key or damaged file
                // throws here instead of producing a database that only looks complete.
                CipherInputStream(sealed, cipher).use { plain -> plain.copyTo(file, FILE_BUFFER_BYTES) }
                file.fd.sync()
            }
        }
    }

    /** Lets the cipher stream finish its final block without closing the file before it is synced. */
    private class NonClosingOutputStream(
        delegate: OutputStream,
    ) : FilterOutputStream(delegate) {
        override fun write(
            b: ByteArray,
            off: Int,
            len: Int,
        ) = out.write(b, off, len)

        override fun close() = flush()
    }

    private fun encryptString(value: String): String {
        val encrypted = encryptBytes(value.encodeToByteArray())
        return Base64.getEncoder().encodeToString(encrypted)
    }

    private fun decryptString(value: String): String? {
        val decoded = runCatching { Base64.getDecoder().decode(value) }.getOrNull() ?: return null
        val decrypted = decryptBytes(decoded) ?: return null
        return decrypted.decodeToString()
    }

    private fun encryptBytes(data: ByteArray): ByteArray {
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
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    companion object {
        private const val KEY_LENGTH_BYTES = 32
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val FILE_BUFFER_BYTES = 64 * 1024
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
