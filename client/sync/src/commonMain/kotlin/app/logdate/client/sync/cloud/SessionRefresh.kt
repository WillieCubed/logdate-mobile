package app.logdate.client.sync.cloud

/**
 * A refreshed session: always a new access token, and a new refresh token when the server rotates it.
 * A linked device's server session slides forward on refresh and rotates the token in the last days of
 * its life, so a caller that stores only [accessToken] loses the device when the old token expires.
 */
data class RefreshedSession(
    val accessToken: String,
    val refreshToken: String?,
)

/** An account repository that returns a rotated refresh token along with the new access token. */
interface SessionRefreshingAccountRepository {
    suspend fun refreshSession(refreshToken: String): Result<RefreshedSession>
}

/** Told when the server says this device's session was revoked, so the device can stop and erase what it holds. */
fun interface DeviceSessionRevocationListener {
    suspend fun onDeviceSessionRevoked()

    companion object {
        val None = DeviceSessionRevocationListener { }
    }
}
