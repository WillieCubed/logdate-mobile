package app.logdate.server.auth

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Instant

interface RefreshTokenRevocationRepository {
    suspend fun revoke(refreshToken: String): Boolean

    suspend fun isRevoked(refreshToken: String): Boolean
}

class InMemoryRefreshTokenRevocationRepository : RefreshTokenRevocationRepository {
    private val revokedTokenHashes = ConcurrentHashMap<String, Instant>()

    override suspend fun revoke(refreshToken: String): Boolean {
        if (refreshToken.isBlank()) return false
        revokedTokenHashes[refreshToken.sha256()] = Clock.System.now()
        return true
    }

    override suspend fun isRevoked(refreshToken: String): Boolean =
        refreshToken.isNotBlank() && revokedTokenHashes.containsKey(refreshToken.sha256())
}

internal fun String.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(toByteArray()).joinToString(separator = "") { "%02x".format(it) }
}
