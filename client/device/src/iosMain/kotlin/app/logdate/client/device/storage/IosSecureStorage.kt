package app.logdate.client.device.storage

import io.github.aakira.napier.Napier
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCBlockSizeAES128
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCKeySizeAES256
import platform.CoreCrypto.kCCOptionPKCS7Padding
import platform.CoreCrypto.kCCSuccess
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSCopyingProtocol
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecRandomDefault
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy
import platform.posix.size_tVar

/**
 * iOS implementation of SecureStorage using the Keychain.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosSecureStorage(
    private val serviceName: String = "app.logdate",
) : SecureStorage {
    private val valueCache = mutableMapOf<String, String>()
    private val valueCacheFlow = MutableStateFlow(valueCache.toMap())

    override suspend fun getString(key: String): String? {
        val value = readKeychain(key)
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
        writeKeychain(key, value)
        valueCache[key] = value
        valueCacheFlow.value = valueCache.toMap()
    }

    override suspend fun remove(key: String) {
        deleteKeychain(key)
        valueCache.remove(key)
        valueCacheFlow.value = valueCache.toMap()
    }

    override suspend fun clear() {
        val query = baseQuery(null)
        val status = withKeychainDictionary(query) { cfQuery -> SecItemDelete(cfQuery) }
        if (status != errSecSuccess) {
            Napier.w("Failed to clear keychain: status=$status")
        }
        valueCache.clear()
        valueCacheFlow.value = emptyMap()
    }

    override fun observeString(key: String): Flow<String?> = valueCacheFlow.map { cache -> cache[key] }

    override fun observeAll(): Flow<Map<String, String>> = valueCacheFlow

    override suspend fun encrypt(data: ByteArray): ByteArray {
        val key = getOrCreateEncryptionKey()
        val iv = randomBytes(IV_LENGTH_BYTES)
        val encrypted = crypt(kCCEncrypt, data, key, iv)
        return encrypted?.let { iv + it } ?: data
    }

    override suspend fun decrypt(data: ByteArray): ByteArray? {
        if (data.size <= IV_LENGTH_BYTES) {
            return null
        }
        val key = getOrCreateEncryptionKey()
        val iv = data.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = data.copyOfRange(IV_LENGTH_BYTES, data.size)
        return crypt(kCCDecrypt, ciphertext, key, iv)
    }

    private fun readKeychain(key: String): String? =
        memScoped {
            val query = baseQuery(key).toMutableMap()
            query[kSecReturnData] = kCFBooleanTrue
            query[kSecMatchLimit] = kSecMatchLimitOne

            val result = alloc<CFTypeRefVar>()
            val status = withKeychainDictionary(query) { cfQuery -> SecItemCopyMatching(cfQuery, result.ptr) }
            if (status != errSecSuccess) {
                return@memScoped null
            }
            val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
            val string = NSString.create(data, NSUTF8StringEncoding)?.toString()
            return@memScoped string
        }

    private fun writeKeychain(
        key: String,
        value: String,
    ) {
        val data = value.toNSData()
        val query = baseQuery(key).toMutableMap()
        query[kSecValueData] = data
        val status = withKeychainDictionary(query) { cfQuery -> SecItemAdd(cfQuery, null) }
        if (status == errSecDuplicateItem) {
            val updateQuery = baseQuery(key)
            val attributes = mapOf<Any?, Any?>(kSecValueData to data)
            val updateStatus =
                withKeychainDictionary(updateQuery) { cfUpdateQuery ->
                    withKeychainDictionary(attributes) { cfAttributes ->
                        SecItemUpdate(cfUpdateQuery, cfAttributes)
                    }
                }
            if (updateStatus != errSecSuccess) {
                Napier.w("Failed to update keychain item: status=$updateStatus")
            }
        } else if (status != errSecSuccess) {
            Napier.w("Failed to add keychain item: status=$status")
        }
    }

    private fun deleteKeychain(key: String) {
        val status = withKeychainDictionary(baseQuery(key)) { cfQuery -> SecItemDelete(cfQuery) }
        if (status != errSecSuccess && status != errSecItemNotFound) {
            Napier.w("Failed to delete keychain item: status=$status")
        }
    }

    private fun baseQuery(key: String?): Map<Any?, Any?> {
        val query = mutableMapOf<Any?, Any?>()
        query[kSecClass] = kSecClassGenericPassword
        query[kSecAttrService] = serviceName
        if (key != null) {
            query[kSecAttrAccount] = key
        }
        return query
    }

    private inline fun <T> withKeychainDictionary(
        query: Map<Any?, Any?>,
        block: (CFDictionaryRef) -> T,
    ): T {
        // Security framework functions take CoreFoundation dictionaries. In Kotlin/Native these are C pointers,
        // so we create an Objective-C NSDictionary and bridge it to a CFDictionaryRef.
        val nsQuery = NSMutableDictionary()
        query.forEach { (key, value) ->
            if (key != null && value != null) {
                val copyKey =
                    key as? NSCopyingProtocol
                        ?: error("Keychain query key does not conform to NSCopyingProtocol.")
                nsQuery.setObject(value, forKey = copyKey)
            }
        }
        val retained =
            CFBridgingRetain(nsQuery)
                ?: error("CFBridgingRetain returned null for keychain query dictionary")
        try {
            val cfQuery: CFDictionaryRef = retained.reinterpret()
            return block(cfQuery)
        } finally {
            CFRelease(retained)
        }
    }

    private fun getOrCreateEncryptionKey(): ByteArray {
        val existing = readKeychain(ENCRYPTION_KEY)
        val decoded = existing?.fromBase64()
        if (decoded != null && decoded.size == KEY_SIZE_BYTES) {
            return decoded
        }
        val key = randomBytes(KEY_SIZE_BYTES)
        writeKeychain(ENCRYPTION_KEY, key.toBase64())
        return key
    }

    private fun crypt(
        operation: UInt,
        data: ByteArray,
        key: ByteArray,
        iv: ByteArray,
    ): ByteArray? =
        memScoped {
            val outputSize = data.size + BLOCK_SIZE_BYTES
            val output = ByteArray(outputSize)
            val outLength = alloc<size_tVar>()
            val status =
                data.usePinned { dataPinned ->
                    key.usePinned { keyPinned ->
                        iv.usePinned { ivPinned ->
                            output.usePinned { outputPinned ->
                                CCCrypt(
                                    operation,
                                    kCCAlgorithmAES,
                                    kCCOptionPKCS7Padding,
                                    keyPinned.addressOf(0),
                                    key.size.toULong(),
                                    ivPinned.addressOf(0),
                                    dataPinned.addressOf(0),
                                    data.size.toULong(),
                                    outputPinned.addressOf(0),
                                    outputSize.toULong(),
                                    outLength.ptr,
                                )
                            }
                        }
                    }
                }
            if (status != kCCSuccess) {
                Napier.w("CommonCrypto operation failed: status=$status")
                return@memScoped null
            }
            return@memScoped output.copyOf(outLength.value.toInt())
        }

    private fun randomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        val status =
            bytes.usePinned { pinned ->
                SecRandomCopyBytes(kSecRandomDefault, size.toULong(), pinned.addressOf(0))
            }
        if (status != errSecSuccess) {
            Napier.e("Failed to generate secure random bytes: status=$status")
        }
        return bytes
    }

    private fun String.toNSData(): NSData = encodeToByteArray().toNSData()

    private fun ByteArray.toNSData(): NSData =
        usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
        }

    private fun ByteArray.toBase64(): String = toNSData().base64EncodedStringWithOptions(0u)

    private fun String.fromBase64(): ByteArray? {
        val data = NSData.create(base64EncodedString = this, options = 0u) ?: return null
        return data.toByteArray()
    }

    private fun NSData.toByteArray(): ByteArray {
        val length = this.length.toInt()
        val buffer = ByteArray(length)
        val bytesPointer = bytes ?: return buffer
        buffer.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytesPointer, length.toULong())
        }
        return buffer
    }

    companion object {
        private const val ENCRYPTION_KEY = "logdate_secure_storage_key"
        private const val IV_LENGTH_BYTES = 16
        private val KEY_SIZE_BYTES = kCCKeySizeAES256.toInt()
        private val BLOCK_SIZE_BYTES = kCCBlockSizeAES128.toInt()
    }
}
