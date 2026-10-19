package app.logdate.client.sync.cloud.account

import app.logdate.client.sync.InMemoryKeyValueStorage
import app.logdate.client.sync.cloud.CheckUsernameAvailabilityResponse
import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.ContentChangesResponse
import app.logdate.client.sync.cloud.ContentUpdateRequest
import app.logdate.client.sync.cloud.ContentUpdateResponse
import app.logdate.client.sync.cloud.ContentUploadRequest
import app.logdate.client.sync.cloud.ContentUploadResponse
import app.logdate.client.sync.cloud.DraftUploadRequest
import app.logdate.client.sync.cloud.JournalChangesResponse
import app.logdate.client.sync.cloud.JournalUpdateRequest
import app.logdate.client.sync.cloud.JournalUpdateResponse
import app.logdate.client.sync.cloud.JournalUploadRequest
import app.logdate.client.sync.cloud.JournalUploadResponse
import app.logdate.client.sync.cloud.MediaDownloadResponse
import app.logdate.client.sync.cloud.MediaUpload
import app.logdate.client.sync.cloud.MediaUploadResponse
import app.logdate.client.sync.cloud.RefreshedSession
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.AccountStorageMode
import app.logdate.shared.model.BeginAccountCreationRequest
import app.logdate.shared.model.BeginAccountCreationResponse
import app.logdate.shared.model.CompleteAccountCreationRequest
import app.logdate.shared.model.CompleteAccountCreationResponse
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests for [DefaultCloudAccountRepository].
 *
 * Verifies that the repository correctly manages cloud account information,
 * including completion of account creation, storage of decentralized identifiers (DIDs),
 * and handling of sign-out operations.
 */
class DefaultCloudAccountRepositoryTest {
    @Test
    fun `production account module follows the selected server configuration`() =
        runTest {
            val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://first.invalid")
            val app =
                org.koin.dsl.koinApplication {
                    modules(
                        org.koin.dsl.module {
                            single<app.logdate.client.datastore.KeyValueStorage> { InMemoryKeyValueStorage() }
                            single<app.logdate.shared.config.LogDateConfigRepository> { config }
                            single<CloudApiClient> { FakeCloudApiClient() }
                        },
                        app.logdate.client.sync.cloud.di.cloudAccountModule,
                    )
                }
            try {
                val account =
                    app.koin.get<app.logdate.shared.model.CloudAccountRepository>()
                        as app.logdate.client.sync.cloud.CloudRequestLocationProvider
                assertEquals("https://first.invalid", account.captureLocation().origin)
                config.updateBackendUrl("https://second.invalid")
                assertEquals("https://second.invalid", account.captureLocation().origin)
                assertEquals("https://second.invalid/api/v1", account.captureLocation().apiBaseUrl)
            } finally {
                app.close()
            }
        }

    @Test
    fun `complete account creation stores did and handle for later load`() =
        runTest {
            val storage = InMemoryKeyValueStorage()
            val configRepository = DefaultLogDateConfigRepository(initialBackendUrl = "https://test.logdate.app")
            val repository =
                DefaultCloudAccountRepository(
                    apiClient =
                        FakeCloudApiClient(
                            completeAccountCreationResult =
                                CompleteAccountCreationResponse(
                                    success = true,
                                    data =
                                        app.logdate.shared.model.CompleteAccountCreationData(
                                            account =
                                                app.logdate.shared.model.LogDateAccount(
                                                    id = Uuid.parse("550e8400-e29b-41d4-a716-446655440000"),
                                                    username = "tester",
                                                    displayName = "Tester",
                                                    did = "did:web:tester.logdate.app",
                                                    handle = "tester.logdate.app",
                                                    bio = "Polished bio",
                                                    originalBio = "My own words",
                                                    passkeyCredentialIds = listOf("cred-1"),
                                                    createdAt = Instant.parse("2026-03-05T00:00:00Z"),
                                                    updatedAt = Instant.parse("2026-03-05T00:00:00Z"),
                                                ),
                                            tokens =
                                                app.logdate.shared.model.AccountTokens(
                                                    accessToken = "access",
                                                    refreshToken = "refresh",
                                                ),
                                        ),
                                ),
                        ),
                    secureStorage = storage,
                    configRepository = configRepository,
                    coroutineScope = TestScope(StandardTestDispatcher(testScheduler)),
                )

            repository.completeAccountCreation(
                sessionToken = "session",
                credentialId = "cred-1",
                clientDataJSON = "client",
                attestationObject = "attestation",
            )

            assertEquals(
                "did:web:tester.logdate.app",
                storage.getString(scopedKey("cloud_account_did", configRepository.getCurrentBackendUrl())),
            )
            assertEquals(
                "tester.logdate.app",
                storage.getString(scopedKey("cloud_account_handle", configRepository.getCurrentBackendUrl())),
            )

            val reloaded =
                DefaultCloudAccountRepository(
                    apiClient = FakeCloudApiClient(),
                    secureStorage = storage,
                    configRepository = configRepository,
                    coroutineScope = TestScope(StandardTestDispatcher(testScheduler)),
                )
            testScheduler.advanceUntilIdle()

            val current = reloaded.getCurrentAccount()
            assertEquals("did:web:tester.logdate.app", current?.did)
            assertEquals("tester.logdate.app", current?.handle)
            assertEquals("Polished bio", current?.bio)
            assertEquals("My own words", current?.originalBio)
        }

    @Test
    fun `the account's storage mode is stored for its server and loaded again`() =
        runTest {
            val storage = InMemoryKeyValueStorage()
            val configRepository = DefaultLogDateConfigRepository(initialBackendUrl = "https://test.logdate.app")
            val repository = repository(storage, configRepository, createdAccount(AccountStorageMode.PERSONAL_SPACE_V1))

            repository.completeAccountCreation("session", "cred-1", "client", "attestation")

            assertEquals(AccountStorageMode.PERSONAL_SPACE_V1, repository.getCurrentAccount()?.storageMode)
            assertEquals("personalSpaceV1", storage.getString(scopedKey("cloud_account_storage_mode", "https://test.logdate.app")))
            assertNull(storage.getString(scopedKey("cloud_account_storage_mode", "https://other.logdate.app")))
            val reloaded = repository(storage, configRepository)
            testScheduler.advanceUntilIdle()
            assertEquals(AccountStorageMode.PERSONAL_SPACE_V1, reloaded.getCurrentAccount()?.storageMode)

            reloaded.signOut()
            assertNull(storage.getString(scopedKey("cloud_account_storage_mode", "https://test.logdate.app")))
        }

    @Test
    fun `an account stored before storage modes has an unknown mode and is never legacy`() =
        runTest {
            val storage = InMemoryKeyValueStorage()
            val configRepository = DefaultLogDateConfigRepository(initialBackendUrl = "https://test.logdate.app")
            repository(storage, configRepository, createdAccount(AccountStorageMode.PERSONAL_SPACE_V1))
                .completeAccountCreation("session", "cred-1", "client", "attestation")
            storage.remove(scopedKey("cloud_account_storage_mode", "https://test.logdate.app"))

            val reloaded = repository(storage, configRepository)
            testScheduler.advanceUntilIdle()

            assertEquals("tester", reloaded.getCurrentAccount()?.username)
            assertEquals(AccountStorageMode.UNRECOGNIZED, reloaded.getCurrentAccount()?.storageMode)
        }

    private fun kotlinx.coroutines.test.TestScope.repository(
        storage: InMemoryKeyValueStorage,
        configRepository: DefaultLogDateConfigRepository,
        created: CompleteAccountCreationResponse? = null,
    ) = DefaultCloudAccountRepository(
        apiClient = FakeCloudApiClient(completeAccountCreationResult = created),
        secureStorage = storage,
        configRepository = configRepository,
        coroutineScope = TestScope(StandardTestDispatcher(testScheduler)),
    )

    private fun createdAccount(storageMode: AccountStorageMode) =
        CompleteAccountCreationResponse(
            success = true,
            data =
                app.logdate.shared.model.CompleteAccountCreationData(
                    account =
                        app.logdate.shared.model.LogDateAccount(
                            id = Uuid.parse("550e8400-e29b-41d4-a716-446655440000"),
                            username = "tester",
                            displayName = "Tester",
                            did = "did:plc:tester",
                            createdAt = Instant.parse("2026-03-05T00:00:00Z"),
                            updatedAt = Instant.parse("2026-03-05T00:00:00Z"),
                            storageMode = storageMode,
                        ),
                    tokens =
                        app.logdate.shared.model
                            .AccountTokens(accessToken = "access", refreshToken = "refresh"),
                ),
        )

    @Test
    fun `sign out clears persisted did and handle`() =
        runTest {
            val storage = InMemoryKeyValueStorage()
            val configRepository = DefaultLogDateConfigRepository(initialBackendUrl = "https://test.logdate.app")
            storage.putString(
                scopedKey("cloud_account_did", configRepository.getCurrentBackendUrl()),
                "did:web:tester.logdate.app",
            )
            storage.putString(
                scopedKey("cloud_account_handle", configRepository.getCurrentBackendUrl()),
                "tester.logdate.app",
            )

            val repository =
                DefaultCloudAccountRepository(
                    apiClient = FakeCloudApiClient(),
                    secureStorage = storage,
                    configRepository = configRepository,
                    coroutineScope = TestScope(StandardTestDispatcher(testScheduler)),
                )

            repository.signOut()

            assertNull(storage.getString(scopedKey("cloud_account_did", configRepository.getCurrentBackendUrl())))
            assertNull(storage.getString(scopedKey("cloud_account_handle", configRepository.getCurrentBackendUrl())))
        }

    @Test
    fun `a rotated refresh token is stored for its server`() =
        runTest {
            val storage = InMemoryKeyValueStorage()
            val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://test.logdate.app")
            val repository =
                DefaultCloudAccountRepository(
                    apiClient = FakeCloudApiClient(refreshSessionResult = Result.success(RefreshedSession("access-2", "refresh-2"))),
                    secureStorage = storage,
                    configRepository = config,
                    coroutineScope = TestScope(StandardTestDispatcher(testScheduler)),
                )

            assertEquals(RefreshedSession("access-2", "refresh-2"), repository.refreshSession("refresh-1").getOrThrow())
            assertEquals("access-2", storage.getString(scopedKey("cloud_access_token", config.getCurrentBackendUrl())))
            assertEquals("refresh-2", storage.getString(scopedKey("cloud_refresh_token", config.getCurrentBackendUrl())))
        }

    @Test
    fun `a server without rotation still refreshes the access token`() =
        runTest {
            val storage = InMemoryKeyValueStorage()
            val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://test.logdate.app")
            val repository =
                DefaultCloudAccountRepository(
                    apiClient = FakeCloudApiClient(),
                    secureStorage = storage,
                    configRepository = config,
                    coroutineScope = TestScope(StandardTestDispatcher(testScheduler)),
                )

            assertEquals(RefreshedSession("access", null), repository.refreshSession("refresh-1").getOrThrow())
            assertEquals("access", storage.getString(scopedKey("cloud_access_token", config.getCurrentBackendUrl())))
            assertNull(storage.getString(scopedKey("cloud_refresh_token", config.getCurrentBackendUrl())))
        }

    @Test
    fun `a refused session refresh stores nothing and says why`() =
        runTest {
            val storage = InMemoryKeyValueStorage()
            val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://test.logdate.app")
            val revoked = CloudApiException("DEVICE_SESSION_REVOKED", "revoked", statusCode = 401)
            val repository =
                DefaultCloudAccountRepository(
                    apiClient = FakeCloudApiClient(refreshSessionResult = Result.failure(revoked)),
                    secureStorage = storage,
                    configRepository = config,
                    coroutineScope = TestScope(StandardTestDispatcher(testScheduler)),
                )

            assertEquals(revoked, repository.refreshSession("refresh-1").exceptionOrNull())
            assertNull(storage.getString(scopedKey("cloud_access_token", config.getCurrentBackendUrl())))
            assertNull(storage.getString(scopedKey("cloud_refresh_token", config.getCurrentBackendUrl())))
        }

    @Test
    fun `changing backend clears active identity instead of reviving another server account`() =
        runTest {
            val storage = InMemoryKeyValueStorage()
            val configRepository = DefaultLogDateConfigRepository(initialBackendUrl = "https://first.logdate.app")
            storage.putString(
                scopedKey("cloud_access_token", configRepository.getCurrentBackendUrl()),
                "first-access",
            )
            storage.putString(
                scopedKey("cloud_account_id", configRepository.getCurrentBackendUrl()),
                "550e8400-e29b-41d4-a716-446655440000",
            )
            storage.putString(
                scopedKey("cloud_username", configRepository.getCurrentBackendUrl()),
                "first-user",
            )
            storage.putString(
                scopedKey("cloud_display_name", configRepository.getCurrentBackendUrl()),
                "First User",
            )
            storage.putString(
                scopedKey("cloud_user_id", configRepository.getCurrentBackendUrl()),
                "550e8400-e29b-41d4-a716-446655440000",
            )
            storage.putString(
                scopedKey("cloud_created_at", configRepository.getCurrentBackendUrl()),
                "2026-03-05T00:00:00Z",
            )
            storage.putString(
                scopedKey("cloud_updated_at", configRepository.getCurrentBackendUrl()),
                "2026-03-05T00:00:00Z",
            )

            // Recreate the repository so its initial load sees the persisted first identity.
            val reloadedRepository =
                DefaultCloudAccountRepository(
                    apiClient = FakeCloudApiClient(),
                    secureStorage = storage,
                    configRepository = configRepository,
                    coroutineScope = TestScope(StandardTestDispatcher(testScheduler)),
                )
            testScheduler.advanceUntilIdle()
            assertEquals("first-user", reloadedRepository.getCurrentAccount()?.username)

            // Even if another origin has credentials left by an older build, selecting it must
            // not implicitly switch identities. Explicit authentication is required instead.
            storage.putString(
                scopedKey("cloud_access_token", "https://second.logdate.app"),
                "second-access",
            )
            configRepository.updateBackendUrl("https://second.logdate.app")
            testScheduler.advanceUntilIdle()

            assertNull(reloadedRepository.getCurrentAccount())
        }
}

private fun scopedKey(
    baseKey: String,
    backendUrl: String,
): String = "${baseKey}_${backendUrl.trim().removePrefix("https://").removePrefix("http://").replace(Regex("[^A-Za-z0-9]"), "_")}"

/**
 * A fake [CloudApiClient] for testing account repository operations.
 */
private class FakeCloudApiClient(
    private val completeAccountCreationResult: CompleteAccountCreationResponse? = null,
    private val refreshSessionResult: Result<RefreshedSession>? = null,
) : CloudApiClient {
    override suspend fun refreshSession(refreshToken: String): Result<RefreshedSession> =
        refreshSessionResult ?: Result.failure(UnsupportedOperationException("No session refresh configured"))

    override suspend fun checkUsernameAvailability(username: String): Result<CheckUsernameAvailabilityResponse> =
        Result.success(CheckUsernameAvailabilityResponse(available = true, username = username))

    override suspend fun beginAccountCreation(request: BeginAccountCreationRequest): Result<BeginAccountCreationResponse> =
        Result.failure(NotImplementedError())

    override suspend fun completeAccountCreation(request: CompleteAccountCreationRequest): Result<CompleteAccountCreationResponse> =
        Result.success(requireNotNull(completeAccountCreationResult))

    override suspend fun refreshAccessToken(refreshToken: String): Result<String> = Result.success("access")

    override suspend fun getAccountInfo(accessToken: String): Result<app.logdate.shared.model.LogDateAccount> =
        Result.failure(NotImplementedError())

    override suspend fun uploadContent(
        accessToken: String,
        content: ContentUploadRequest,
    ): Result<ContentUploadResponse> = Result.failure(NotImplementedError())

    override suspend fun getContentChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ): Result<ContentChangesResponse> = Result.failure(NotImplementedError())

    override suspend fun updateContent(
        accessToken: String,
        contentId: String,
        content: ContentUpdateRequest,
    ): Result<ContentUpdateResponse> = Result.failure(NotImplementedError())

    override suspend fun deleteContent(
        accessToken: String,
        contentId: String,
    ): Result<Unit> = Result.failure(NotImplementedError())

    override suspend fun uploadJournal(
        accessToken: String,
        journal: JournalUploadRequest,
    ): Result<JournalUploadResponse> = Result.failure(NotImplementedError())

    override suspend fun getJournalChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ): Result<JournalChangesResponse> = Result.failure(NotImplementedError())

    override suspend fun updateJournal(
        accessToken: String,
        journalId: String,
        journal: JournalUpdateRequest,
    ): Result<JournalUpdateResponse> = Result.failure(NotImplementedError())

    override suspend fun deleteJournal(
        accessToken: String,
        journalId: String,
    ): Result<Unit> = Result.failure(NotImplementedError())

    override suspend fun uploadAssociations(
        accessToken: String,
        associations: app.logdate.client.sync.cloud.AssociationUploadRequest,
    ): Result<app.logdate.client.sync.cloud.AssociationUploadResponse> = Result.failure(NotImplementedError())

    override suspend fun getAssociationChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ): Result<app.logdate.client.sync.cloud.AssociationChangesResponse> = Result.failure(NotImplementedError())

    override suspend fun deleteAssociations(
        accessToken: String,
        associations: app.logdate.client.sync.cloud.AssociationDeleteRequest,
    ): Result<Unit> = Result.failure(NotImplementedError())

    override suspend fun uploadMedia(
        accessToken: String,
        media: MediaUpload,
    ): Result<MediaUploadResponse> = Result.failure(NotImplementedError())

    override suspend fun uploadDraft(
        accessToken: String,
        draft: DraftUploadRequest,
    ) = TODO()

    override suspend fun getDraftChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ) = TODO()

    override suspend fun deleteDraft(
        accessToken: String,
        draftId: String,
    ) = TODO()

    override suspend fun downloadMedia(
        accessToken: String,
        mediaId: String,
    ): Result<MediaDownloadResponse> = Result.failure(NotImplementedError())
}

/**
 * An in-memory implementation of [KeyValueStorage] for unit testing.
 */
