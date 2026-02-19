package app.logdate.client.database.encryption

import java.security.SecureRandom

actual fun generateDatabaseKey(length: Int): ByteArray {
    val bytes = ByteArray(length)
    SecureRandom().nextBytes(bytes)
    return bytes
}
