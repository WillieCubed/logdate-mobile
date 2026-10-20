package app.logdate.client.data.journals

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.data.di.createLocalDraftRepository
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.LogDateConfigDataSource
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.identity.DefaultCanonicalOwnerProvider
import app.logdate.client.repository.journals.DraftRepository
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.config.LogDateConfigRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class LocalDraftRepositoryWiringTest {
    @Test
    fun `production provider hydrates persisted server and canonical ownership before signed-out recovery`() =
        runTest {
            val storage = CustodyStorage()
            val owner = DefaultCanonicalOwnerProvider(storage)
            val ownerId = Uuid.random().toString()
            assertTrue(owner.adoptRemoteOwnerIfUninitialized(ownerId))
            val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://initial.example")
            val preferences = CustodyPreferences()
            val origin = "https://retained.example"
            preferences.updateData { preferencesOf(stringPreferencesKey("backend_url") to origin) }
            val dataSource = LogDateConfigDataSource(preferences, config, backgroundScope)
            val metadata = CustodyMetadata()
            val draft = custodyDraft("Production DI restore while signed out")
            storage.putStringOrThrow("editor_drafts_anonymous", Json.encodeToString(listOf(draft)))
            metadata.pending += PendingUpload(draft.id.toString(), PendingOperation.UPDATE, scope = UploadScope(ownerId, origin))
            val app =
                koinApplication {
                    modules(
                        module {
                            single<KeyValueStorage> { storage }
                            single<Json> { Json }
                            single<SessionStorage> { SignedOutCustodySession }
                            single<CanonicalOwnerProvider> { owner }
                            single<LogDateConfigRepository> { config }
                            single { dataSource }
                            single<SyncMetadataService> { metadata }
                            single<app.logdate.client.sync.SyncTransactionManager> { DirectArchiveRecoveryTransactions }
                            single<DraftRepository> { createLocalDraftRepository() }
                        },
                    )
                }
            try {
                assertEquals(listOf(draft), app.koin.get<DraftRepository>().getAllDraftsForSync())
                assertEquals(origin, config.getCurrentBackendUrl())
                assertEquals(ownerId, owner.getCanonicalOwnerId())
                assertEquals(null, app.koin.get<SessionStorage>().getSession())
            } finally {
                app.close()
            }
        }
}

private object SignedOutCustodySession : SessionStorage {
    override fun getSession(): UserSession? = null

    override fun getSessionFlow() = flowOf<UserSession?>(null)

    override suspend fun hasValidSession() = false

    override suspend fun saveSession(session: UserSession) = error("Fixture is signed out")

    override suspend fun clearSession() = Unit
}
