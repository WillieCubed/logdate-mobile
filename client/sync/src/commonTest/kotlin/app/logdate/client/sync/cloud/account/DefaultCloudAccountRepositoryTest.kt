package app.logdate.client.sync.cloud.account

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.cloud.CheckUsernameAvailabilityResponse
import app.logdate.client.sync.cloud.CloudApiClient
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
import app.logdate.client.sync.cloud.MediaUploadRequest
import app.logdate.client.sync.cloud.MediaUploadResponse
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.BeginAccountCreationRequest
import app.logdate.shared.model.BeginAccountCreationResponse
import app.logdate.shared.model.CompleteAccountCreationRequest
import app.logdate.shared.model.CompleteAccountCreationResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
        }

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
) : CloudApiClient {
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
        media: MediaUploadRequest,
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
private class InMemoryKeyValueStorage : KeyValueStorage {
    private val values = mutableMapOf<String, String>()

    override suspend fun getString(key: String): String? = values[key]

    override fun getStringSync(key: String): String? = values[key]

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        values[key] = value
    }

    override suspend fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = defaultValue

    override suspend fun putBoolean(
        key: String,
        value: Boolean,
    ) = Unit

    override suspend fun getInt(
        key: String,
        defaultValue: Int,
    ): Int = defaultValue

    override suspend fun putInt(
        key: String,
        value: Int,
    ) = Unit

    override suspend fun getLong(
        key: String,
        defaultValue: Long,
    ): Long = defaultValue

    override suspend fun putLong(
        key: String,
        value: Long,
    ) = Unit

    override suspend fun getFloat(
        key: String,
        defaultValue: Float,
    ): Float = defaultValue

    override suspend fun putFloat(
        key: String,
        value: Float,
    ) = Unit

    override suspend fun remove(key: String) {
        values.remove(key)
    }

    override suspend fun contains(key: String): Boolean = values.containsKey(key)

    override suspend fun clear() {
        values.clear()
    }

    override fun observeString(key: String): Flow<String?> = flowOf(values[key])

    override fun observeBoolean(
        key: String,
        defaultValue: Boolean,
    ): Flow<Boolean> = flowOf(defaultValue)

    override fun observeInt(
        key: String,
        defaultValue: Int,
    ): Flow<Int> = flowOf(defaultValue)

    override fun observeLong(
        key: String,
        defaultValue: Long,
    ): Flow<Long> = flowOf(defaultValue)

    override fun observeFloat(
        key: String,
        defaultValue: Float,
    ): Flow<Float> = flowOf(defaultValue)
}
