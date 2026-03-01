@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.logdate.client.database.encryption

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

actual fun generateDatabaseKey(length: Int): ByteArray {
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        val status = SecRandomCopyBytes(kSecRandomDefault, length.toULong(), pinned.addressOf(0))
        if (status != errSecSuccess) {
            throw IllegalStateException("Failed to generate secure random bytes (status=$status)")
        }
    }
    return bytes
}
