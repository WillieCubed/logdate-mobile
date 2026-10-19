package app.logdate.wear.sync.v2.cloud

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.client.sync.cloud.CloudRequestLocation
import app.logdate.client.sync.cloud.CloudRequestLocationProvider
import app.logdate.client.sync.cloud.RefreshedSession
import app.logdate.client.sync.cloud.SessionRefreshingAccountRepository
import app.logdate.client.sync.cloud.account.ServerAccountStore
import app.logdate.client.sync.cloud.account.ServerReportedAccount
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.AuthenticationResult
import app.logdate.shared.model.BeginAccountCreationResult
import app.logdate.shared.model.CloudAccount
import app.logdate.shared.model.CloudAccountRepository
import app.logdate.shared.model.DeviceInfo
import app.logdate.shared.model.PasskeyCredential
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.uuid.Uuid

/**
 * Refreshes a linked session; SyncTokenRefresher alone saves rotated tokens in the secure session vault.
 * The current account is the one the server last described at `/auth/me`, which the watch reads with
 * its own device token as soon as it is linked and each time sync starts, while the watch is signed in
 * to that account on that server.
 */
class WatchCloudAccountRepository(
    private val api: CloudApiClient,
    private val config: LogDateConfigRepository,
    private val sessions: SessionStorage,
    private val accounts: ServerAccountStore,
) : CloudAccountRepository,
    SessionRefreshingAccountRepository,
    CloudRequestLocationProvider {
    override fun captureLocation(): CloudRequestLocation {
        val origin = config.getCurrentBackendUrl()
        val base = config.getCurrentApiBaseUrl()
        check(config.getCurrentBackendUrl() == origin) { "Cloud request origin changed" }
        return CloudRequestLocation(origin, base)
    }

    override fun isCurrentOrigin(origin: String): Boolean = config.getCurrentBackendUrl() == origin

    override suspend fun refreshSession(refreshToken: String): Result<RefreshedSession> = api.refreshSession(refreshToken)

    override suspend fun refreshAccessToken(refreshToken: String): Result<String> = refreshSession(refreshToken).map { it.accessToken }

    override suspend fun getCurrentAccount(): CloudAccount? = current(accounts.read(), config.getCurrentBackendUrl())

    override fun observeCurrentAccount(): Flow<CloudAccount?> =
        combine(accounts.observe(), sessions.getSessionFlow(), config.backendUrl) { reported, _, origin -> current(reported, origin) }
            .distinctUntilChanged()

    private fun current(
        reported: ServerReportedAccount?,
        origin: String,
    ): CloudAccount? {
        val bound = sessions.getOriginBoundSession()?.takeIf { it.origin.trimEnd('/') == origin.trimEnd('/') } ?: return null
        if (reported == null || !reported.describes(bound.origin, bound.session.accountId)) return null
        return reported.toCloudAccount() ?: run {
            Napier.e("The account the server described has an ID that isn't a UUID; this watch has no current account")
            null
        }
    }

    override suspend fun isUsernameAvailable(username: String): Result<Boolean> = managedOnPhone()

    override suspend fun beginAccountCreation(
        username: String,
        displayName: String,
        deviceInfo: DeviceInfo?,
    ): Result<BeginAccountCreationResult> = managedOnPhone()

    override suspend fun completeAccountCreation(
        sessionToken: String,
        credentialId: String,
        clientDataJSON: String,
        attestationObject: String,
    ): Result<AuthenticationResult> = managedOnPhone()

    override suspend fun signOut(): Result<Boolean> = managedOnPhone()

    override suspend fun getPasskeyCredentials(): Result<List<PasskeyCredential>> = managedOnPhone()

    override suspend fun associateUserIdentity(
        userId: Uuid,
        accountId: String,
    ): Result<Boolean> = managedOnPhone()

    private fun <T> managedOnPhone(): Result<T> =
        Result.failure(UnsupportedOperationException("This watch's account is managed on its phone"))
}
