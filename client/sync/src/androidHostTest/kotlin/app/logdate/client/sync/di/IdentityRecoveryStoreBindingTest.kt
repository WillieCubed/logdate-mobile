package app.logdate.client.sync.di

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.sync.InMemoryKeyValueStorage
import app.logdate.client.sync.RecoverIdentityUseCase
import app.logdate.client.sync.RecoveryPhraseAccountProof
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider
import app.logdate.client.sync.metadata.IdentityRecoveryNeededStore
import app.logdate.client.sync.metadata.KeyValueIdentityRecoveryNeededStore
import app.logdate.client.sync.metadata.KeyValueUnreadableCloudRecordStore
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UnreadableCloudRecordStore
import app.logdate.client.sync.recovery.DownloadInbox
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IdentityRecoveryStoreBindingTest {
    @Test
    fun `sync module shares the durable recovery state store`() =
        runTest {
            val backing = InMemoryKeyValueStorage()
            val storage =
                object : KeyValueStorage by backing {
                    private var recoveryNeeded = false

                    override suspend fun getBoolean(
                        key: String,
                        defaultValue: Boolean,
                    ): Boolean = if (key == "identity_recovery_needed") recoveryNeeded else defaultValue

                    override suspend fun putBoolean(
                        key: String,
                        value: Boolean,
                    ) {
                        if (key == "identity_recovery_needed") recoveryNeeded = value
                    }
                }
            val application =
                koinApplication {
                    modules(
                        syncModule,
                        module {
                            single<KeyValueStorage> { storage }
                            single { mockk<IdentityKeyManager>() }
                            single<SyncMetadataService> { mockk() }
                            single<MediaPayloadKeyProvider> { mockk() }
                            single { mockk<DownloadInbox>() }
                            single<RecoveryPhraseAccountProof> { mockk() }
                            single<SessionStorage> { mockk() }
                        },
                    )
                }
            try {
                val store = application.koin.get<IdentityRecoveryNeededStore>()
                assertIs<KeyValueIdentityRecoveryNeededStore>(store)
                assertSame(store, application.koin.get<IdentityRecoveryNeededStore>())
                store.setNeeded(true)
                assertTrue(application.koin.get<IdentityRecoveryNeededStore>().isNeeded())
                val unreadable = application.koin.get<UnreadableCloudRecordStore>()
                assertIs<KeyValueUnreadableCloudRecordStore>(unreadable)
                assertSame(unreadable, application.koin.get<UnreadableCloudRecordStore>())
                assertNotNull(application.koin.get<RecoverIdentityUseCase>())
            } finally {
                application.close()
            }
        }
}
