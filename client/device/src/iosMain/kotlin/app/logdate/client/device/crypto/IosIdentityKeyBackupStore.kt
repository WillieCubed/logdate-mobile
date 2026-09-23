package app.logdate.client.device.crypto

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
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecAttrSynchronizable
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Backs up the identity recovery phrase in an iCloud Keychain-synchronized Keychain item --
 * Apple's equivalent of Android's device-transfer/cloud-backup file mirror
 * ([AndroidIdentityKeyBackupStore]).
 *
 * This is deliberately its own, narrowly-scoped Keychain item rather than a flag on
 * [app.logdate.client.device.storage.IosSecureStorage]: that class also stores things like the
 * session's auth tokens, which have no business syncing across the user's devices via iCloud
 * Keychain. Only the identity recovery phrase -- the one piece of local state that must survive a
 * reinstall or a new device to avoid orphaning already-synced content -- goes through this path.
 *
 * The identity key itself is never written here, only the phrase it's deterministically derived
 * from (see [IdentityKeyManager.setupNewIdentity]), so restoring this item is enough to rebuild
 * the key on a new device.
 *
 * iCloud Keychain sync ([kSecAttrSynchronizable]) requires no special app entitlement for an
 * app's own, non-shared Keychain items -- entitlements like Keychain Sharing
 * (`keychain-access-groups`) are only needed to share an item across multiple apps from the same
 * developer. It does require the user to have iCloud Keychain turned on for their device; when
 * it's off, items still write and read locally, they simply don't sync.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosIdentityKeyBackupStore(
    private val serviceName: String = "app.logdate.identity-recovery",
    private val account: String = "identity_recovery_phrase_backup_v1",
) : IdentityKeyBackupStore {
    override suspend fun readPhrase(): String? =
        memScoped {
            val query = baseQuery().toMutableMap()
            query[kSecReturnData] = kCFBooleanTrue
            query[kSecMatchLimit] = kSecMatchLimitOne

            val result = alloc<CFTypeRefVar>()
            val status = withKeychainDictionary(query) { cfQuery -> SecItemCopyMatching(cfQuery, result.ptr) }
            if (status != errSecSuccess) {
                return@memScoped null
            }
            val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
            NSString.create(data, NSUTF8StringEncoding)?.toString()
        }

    override suspend fun writePhrase(phrase: String) {
        val data = phrase.toNSData()
        val query = baseQuery().toMutableMap()
        query[kSecValueData] = data
        val status = withKeychainDictionary(query) { cfQuery -> SecItemAdd(cfQuery, null) }
        if (status == errSecDuplicateItem) {
            val updateQuery = baseQuery()
            val attributes = mapOf<Any?, Any?>(kSecValueData to data)
            val updateStatus =
                withKeychainDictionary(updateQuery) { cfUpdateQuery ->
                    withKeychainDictionary(attributes) { cfAttributes ->
                        SecItemUpdate(cfUpdateQuery, cfAttributes)
                    }
                }
            if (updateStatus != errSecSuccess) {
                Napier.w("Failed to update identity recovery phrase backup: status=$updateStatus")
            }
        } else if (status != errSecSuccess) {
            Napier.w("Failed to add identity recovery phrase backup: status=$status")
        }
    }

    override suspend fun clear() {
        val status = withKeychainDictionary(baseQuery()) { cfQuery -> SecItemDelete(cfQuery) }
        if (status != errSecSuccess && status != errSecItemNotFound) {
            Napier.w("Failed to clear identity recovery phrase backup: status=$status")
        }
    }

    private fun baseQuery(): Map<Any?, Any?> =
        mapOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceName,
            kSecAttrAccount to account,
            // Marks this item to sync via iCloud Keychain, unlike everything else that goes
            // through IosSecureStorage.
            kSecAttrSynchronizable to kCFBooleanTrue,
        )

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

    private fun String.toNSData(): NSData =
        encodeToByteArray().let { bytes ->
            bytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            }
        }
}
