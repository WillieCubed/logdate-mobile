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
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidSecureStorage(
    private val context: Context,
) : SecureStorage {
    private val keyAlias = "logdate_secure_storage_master_key"

    @Suppress("DEPRECATION")
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
     * Creates EncryptedSharedPreferences with recovery for post-restore scenarios.
     *
     * After a cloud backup restore, the preferences XML file may be encrypted with a
     * KeyStore master key from the old device. Since KeyStore keys are device-bound and
     * not included in cloud backups, the file is unreadable. This method catches the
     * resulting exception, deletes the stale file, and retries with a fresh instance.
     */
    @Suppress("DEPRECATION")
    private fun createEncryptedPrefs(): SharedPreferences =
        try {
            createEncryptedPrefsInternal()
        } catch (e: GeneralSecurityException) {
            Napier.w(
                "EncryptedSharedPreferences failed to initialize (likely post-restore). " +
                    "Deleting stale preferences and retrying.",
                e,
            )
            deleteStalePrefsFile()
            createEncryptedPrefsInternal()
        } catch (e: KeyStoreException) {
            Napier.w(
                "EncryptedSharedPreferences KeyStore error (likely post-restore). " +
                    "Deleting stale preferences and retrying.",
                e,
            )
            deleteStalePrefsFile()
            createEncryptedPrefsInternal()
        } catch (e: Exception) {
            // Catch protobuf and other unexpected errors from Tink internals.
            // InvalidProtocolBufferException is shaded inside Tink so we match by class name.
            if (e::class.java.simpleName == "InvalidProtocolBufferException" ||
                e.cause?.let { it::class.java.simpleName == "InvalidProtocolBufferException" } == true
            ) {
                Napier.w(
                    "EncryptedSharedPreferences protobuf error (likely post-restore). " +
                        "Deleting stale preferences and retrying.",
                    e,
                )
                deleteStalePrefsFile()
                createEncryptedPrefsInternal()
            } else {
                throw e
            }
        }

    @Suppress("DEPRECATION")
    private fun createEncryptedPrefsInternal(): SharedPreferences {
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

    private fun deleteStalePrefsFile() {
        val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/$PREFS_FILE_NAME.xml")
        if (prefsFile.exists()) {
            val deleted = prefsFile.delete()
            Napier.i("Deleted stale EncryptedSharedPreferences file: deleted=$deleted")
        }
    }

    companion object {
        private const val PREFS_FILE_NAME = "logdate_secure_storage"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
    }
}
