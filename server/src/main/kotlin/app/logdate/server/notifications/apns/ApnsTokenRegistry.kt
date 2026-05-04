package app.logdate.server.notifications.apns

import java.util.concurrent.ConcurrentHashMap

/**
 * Stores APNs device tokens per signed-in user. Production deployments should swap the
 * in-memory implementation for a database-backed one once the auth/account schema is set
 * up — both contracts have to keep `(userId, deviceId)` as the natural key so the same
 * device updating its token replaces the previous entry instead of creating duplicates.
 */
interface ApnsTokenRegistry {
    suspend fun register(
        userId: String,
        deviceId: String,
        token: String,
    )

    suspend fun unregister(
        userId: String,
        deviceId: String,
    )

    suspend fun tokensFor(userId: String): List<String>
}

/**
 * Memory-backed registry for development and CI. Loses every token on restart, so use it
 * only when there's no real notification surface yet.
 */
class InMemoryApnsTokenRegistry : ApnsTokenRegistry {
    private val byUser = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()

    override suspend fun register(
        userId: String,
        deviceId: String,
        token: String,
    ) {
        byUser.computeIfAbsent(userId) { ConcurrentHashMap() }[deviceId] = token
    }

    override suspend fun unregister(
        userId: String,
        deviceId: String,
    ) {
        byUser[userId]?.remove(deviceId)
    }

    override suspend fun tokensFor(userId: String): List<String> = byUser[userId]?.values?.toList().orEmpty()
}
