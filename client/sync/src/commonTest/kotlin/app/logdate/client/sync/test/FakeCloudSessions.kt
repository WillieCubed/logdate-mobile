package app.logdate.client.sync.test

import app.logdate.client.datastore.SessionAuthenticationSnapshot
import app.logdate.client.datastore.SessionInstallationGuard
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.shared.model.AuthenticationResult
import app.logdate.shared.model.BeginAccountCreationResult
import app.logdate.shared.model.CloudAccount
import app.logdate.shared.model.CloudAccountRepository
import app.logdate.shared.model.DeviceInfo
import app.logdate.shared.model.PasskeyCredential
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.uuid.Uuid

class FakeCloudAccountRepository : CloudAccountRepository {
    private var isAuthenticatedState: Boolean = true
    var accessToken: String? = "test-access-token"

    private val accountFlow = MutableStateFlow<CloudAccount?>(null)

    override suspend fun getCurrentAccount(): CloudAccount? =
        if (isAuthenticatedState) {
            CloudAccount(
                id = Uuid.random(),
                username = "testuser",
                displayName = "Test User",
                userId = Uuid.random(),
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now(),
                passkeyCredentialIds = emptyList(),
            )
        } else {
            null
        }

    override fun observeCurrentAccount(): Flow<CloudAccount?> = accountFlow.asStateFlow()

    override suspend fun isUsernameAvailable(username: String): Result<Boolean> =
        Result.failure(NotImplementedError("Use for sync testing only"))

    override suspend fun beginAccountCreation(
        username: String,
        displayName: String,
        deviceInfo: DeviceInfo?,
    ): Result<BeginAccountCreationResult> = Result.failure(NotImplementedError("Use for sync testing only"))

    override suspend fun completeAccountCreation(
        sessionToken: String,
        credentialId: String,
        clientDataJSON: String,
        attestationObject: String,
    ): Result<AuthenticationResult> = Result.failure(NotImplementedError("Use for sync testing only"))

    override suspend fun refreshAccessToken(refreshToken: String): Result<String> = Result.success("new-token")

    override suspend fun signOut(): Result<Boolean> = Result.success(true)

    override suspend fun getPasskeyCredentials(): Result<List<PasskeyCredential>> = Result.success(emptyList())

    override suspend fun associateUserIdentity(
        userId: Uuid,
        accountId: String,
    ): Result<Boolean> = Result.success(true)

    fun setAuthenticated(authenticated: Boolean) {
        isAuthenticatedState = authenticated
        accessToken = if (authenticated) "test-access-token" else null
        accountFlow.value =
            if (authenticated) {
                CloudAccount(
                    id = Uuid.random(),
                    username = "testuser",
                    displayName = "Test User",
                    userId = Uuid.random(),
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now(),
                    passkeyCredentialIds = emptyList(),
                )
            } else {
                null
            }
    }
}

/**
 * Fake SessionStorage for testing.
 */
class FakeSessionStorage(
    authenticated: Boolean = true,
) : SessionStorage {
    private var revision = 0L
    var currentOrigin: String = app.logdate.shared.config.DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL
    private var sessionState: UserSession? =
        UserSession(
            accessToken = "test-access-token",
            refreshToken = "test-refresh-token",
            accountId = "test-account-id",
        ).takeIf { authenticated }
    private val sessionStateFlow = MutableStateFlow(sessionState)

    override fun getSession(): UserSession? = sessionState

    override suspend fun captureAuthenticationSnapshot(): SessionAuthenticationSnapshot =
        SessionAuthenticationSnapshot(getOriginBoundSession(), SessionInstallationGuard(currentOrigin, revision))

    override suspend fun clearSessionIfUnchanged(
        expected: SessionInstallationGuard,
        onCleared: () -> Unit,
    ): Boolean {
        if (currentOrigin != expected.origin || revision != expected.revision) return false
        clearSession()
        onCleared()
        return true
    }

    override fun getOriginBoundSession(): app.logdate.client.datastore.OriginBoundSession? =
        sessionState?.let {
            app.logdate.client.datastore
                .OriginBoundSession(currentOrigin, it)
        }

    override suspend fun replaceSessionIfCurrent(
        expected: app.logdate.client.datastore.OriginBoundSession,
        updated: UserSession,
    ): Boolean {
        if (getOriginBoundSession() != expected) return false
        saveSession(updated)
        return true
    }

    override fun getSessionFlow(): Flow<UserSession?> = sessionStateFlow.asStateFlow()

    override suspend fun hasValidSession(): Boolean = sessionState != null

    override suspend fun saveSession(session: UserSession) {
        revision++
        sessionState = session
        sessionStateFlow.value = session
    }

    override suspend fun clearSession() {
        revision++
        sessionState = null
        sessionStateFlow.value = null
    }
}
