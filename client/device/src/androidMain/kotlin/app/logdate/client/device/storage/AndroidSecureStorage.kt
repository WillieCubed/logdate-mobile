@file:Suppress("DEPRECATION")

package app.logdate.client.device.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val PREFS_FILE_NAME = "logdate_secure_storage"
private const val KEYSTORE = "AndroidKeyStore"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_IV_LENGTH_BYTES = 12

/**
 * Signals that the encrypted preferences file backing [AndroidSecureStorage] could not be
 * opened, without deleting or replacing it.
 *
 * That file holds the user's identity encryption key and recovery phrase. There is no safe
 * automatic recovery from this error: a transient KeyStore hiccup and genuine corruption look
 * identical from inside this call, and guessing wrong by deleting the file would destroy the
 * user's only copy of their key, permanently orphaning every entry already synced to the cloud.
 */
class SecureStorageInitializationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class AndroidSecureStorage
    internal constructor(
        private val encryptedPrefsFactory: () -> SharedPreferences,
    ) : SecureStorage {
        constructor(context: Context) : this({ createRealEncryptedPrefs(context) })

        private val keyAlias = "logdate_secure_storage_master_key"

        private val prefs: SharedPreferences = createEncryptedPrefs()

        private val valueCache = mutableMapOf<String, String>()
        private val valueCacheFlow = MutableStateFlow<Map<String, String>>(emptyMap())

        init {
            prefs.all.forEach { (key, value) ->
                if (value is String) {
                    valueCache[key] = value
                }
            }
            valueCacheFlow.value = valueCache.toMap()
        }

        override suspend fun getString(key: String): String? {
            val value = prefs.getString(key, null)
            if (value != null) {
                valueCache[key] = value
                valueCacheFlow.value = valueCache.toMap()
            }
            return value
        }

        override suspend fun putString(
            key: String,
            value: String,
        ) {
            prefs.edit().putString(key, value).apply()
            valueCache[key] = value
            valueCacheFlow.value = valueCache.toMap()
        }

        override suspend fun remove(key: String) {
            prefs.edit().remove(key).apply()
            valueCache.remove(key)
            valueCacheFlow.value = valueCache.toMap()
        }

        override suspend fun clear() {
            prefs.edit().clear().apply()
            valueCache.clear()
            valueCacheFlow.value = emptyMap()
        }

        override fun observeString(key: String): Flow<String?> = valueCacheFlow.map { cache -> cache[key] }

        override fun observeAll(): Flow<Map<String, String>> = valueCacheFlow

        override suspend fun encrypt(data: ByteArray): ByteArray =
            runCatching {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey())
                val iv = cipher.iv
                val encrypted = cipher.doFinal(data)
                iv + encrypted
            }.getOrElse { error ->
                Napier.e("Failed to encrypt payload", error)
                data
            }

        override suspend fun decrypt(data: ByteArray): ByteArray? {
            return runCatching {
                if (data.size <= GCM_IV_LENGTH_BYTES) {
                    return@runCatching null
                }
                val iv = data.copyOfRange(0, GCM_IV_LENGTH_BYTES)
                val ciphertext = data.copyOfRange(GCM_IV_LENGTH_BYTES, data.size)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
                cipher.doFinal(ciphertext)
            }.getOrElse { error ->
                Napier.e("Failed to decrypt payload", error)
                null
            }
        }

        private fun secretKey(): SecretKey {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            return keyStore.getKey(keyAlias, null) as SecretKey
        }

        /**
         * Opens the encrypted preferences that back this store.
         *
         * The file this reads holds the user's only copy of their identity encryption key. If it
         * cannot be opened, the failure is logged and rethrown rather than treated as recoverable —
         * there is no way to tell "temporarily unreadable" apart from "genuinely stale" from inside
         * this call, and deleting the file to recover destroys the key permanently. Letting
         * construction (and Koin startup with it) fail loudly is far better than silently
         * generating a replacement key and orphaning the user's synced data.
         */
        private fun createEncryptedPrefs(): SharedPreferences =
            try {
                encryptedPrefsFactory()
            } catch (e: GeneralSecurityException) {
                Napier.e(
                    "EncryptedSharedPreferences failed to initialize. Leaving the identity key " +
                        "file untouched instead of deleting and recreating it.",
                    e,
                )
                throw SecureStorageInitializationException("Failed to open encrypted secure storage", e)
            } catch (e: KeyStoreException) {
                Napier.e(
                    "EncryptedSharedPreferences KeyStore error. Leaving the identity key file " +
                        "untouched instead of deleting and recreating it.",
                    e,
                )
                throw SecureStorageInitializationException("Failed to open encrypted secure storage", e)
            } catch (e: Exception) {
                // Tink shades InvalidProtocolBufferException internally, so it can only be matched
                // by class name.
                val isProtobufError =
                    e::class.java.simpleName == "InvalidProtocolBufferException" ||
                        e.cause?.let { it::class.java.simpleName == "InvalidProtocolBufferException" } == true
                if (isProtobufError) {
                    Napier.e(
                        "EncryptedSharedPreferences failed to initialize with a protobuf error. " +
                            "Leaving the identity key file untouched instead of deleting and " +
                            "recreating it.",
                        e,
                    )
                    throw SecureStorageInitializationException("Failed to open encrypted secure storage", e)
                } else {
                    throw e
                }
            }
    }

@Suppress("DEPRECATION")
private fun createRealEncryptedPrefs(context: Context): SharedPreferences {
    val masterKey =
        MasterKey
            .Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    return EncryptedSharedPreferences.create(
        context,
        PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
}
