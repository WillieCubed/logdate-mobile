package app.logdate.client.datastore

/**
 * The saved sign-in for each server, addressed by server rather than by whichever server the app
 * is currently connected to.
 *
 * Moving an account signs in to the new server while the app is still connected to the old one,
 * and cleans up the old server after it has switched. Both need a session for a server other than
 * the current one. Every call finishes its write before returning, so nothing is still in flight
 * when the app switches servers.
 */
interface OriginSessionVault {
    suspend fun read(origin: String): UserSession?

    suspend fun write(
        origin: String,
        session: UserSession,
    )

    suspend fun clear(origin: String)
}
