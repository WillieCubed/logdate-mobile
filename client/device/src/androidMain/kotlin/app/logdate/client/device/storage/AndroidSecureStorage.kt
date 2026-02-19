@file:Suppress("DEPRECATION")

package app.logdate.client.device.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidSecureStorage(
    context: Context
) : SecureStorage {

    private val keyAlias = "logdate_secure_storage_master_key"
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    @Suppress("DEPRECATION")
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "logdate_secure_storage",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

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

    override suspend fun putString(key: String, value: String) {
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

    override fun observeString(key: String): Flow<String?> {
        return valueCacheFlow.map { cache -> cache[key] }
    }

    override fun observeAll(): Flow<Map<String, String>> {
        return valueCacheFlow
    }

    override suspend fun encrypt(data: ByteArray): ByteArray {
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(data)
            iv + encrypted
        }.getOrElse { error ->
            Napier.e("Failed to encrypt payload", error)
            data
        }
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

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
    }
}
