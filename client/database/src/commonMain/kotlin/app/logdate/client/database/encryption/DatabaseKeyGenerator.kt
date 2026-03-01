package app.logdate.client.database.encryption

/**
 * Generates cryptographically strong random bytes for database encryption keys.
 */
expect fun generateDatabaseKey(length: Int): ByteArray
