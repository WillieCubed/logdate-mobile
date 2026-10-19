package app.logdate.wear.di

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.client.sync.cloud.CloudRequestLocationProvider
import app.logdate.client.sync.cloud.RefreshedSession
import app.logdate.client.sync.cloud.SessionRefreshingAccountRepository
import app.logdate.client.sync.cloud.account.ServerAccountStore
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.CloudAccountRepository
import app.logdate.wear.sync.v2.MemoryKeyValueStorage
import app.logdate.wear.sync.v2.cloud.WatchCloudAccountRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WearCloudCredentialsTest {
    @Test
    fun `request location captures the selected server and invalidates after switching`() =
        runTest {
            val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://watch.test", initialApiVersion = "v2")
            val accounts: CloudRequestLocationProvider =
                WatchCloudAccountRepository(mockk(), config, mockk(), ServerAccountStore(MemoryKeyValueStorage()))
            val location = accounts.captureLocation()
            assertEquals("https://watch.test", location.origin)
            assertEquals("https://watch.test/api/v2", location.apiBaseUrl)
            assertTrue(accounts.isCurrentOrigin(location.origin))
            config.updateBackendUrl("https://other.test")
            assertFalse(accounts.isCurrentOrigin(location.origin))
        }

    @Test
    fun `watch refresh never copies session tokens into ordinary sync storage`() =
        runTest {
            val storage = MemoryKeyValueStorage()
            val api = mockk<CloudApiClient>()
            val refreshed = RefreshedSession("watch-access", "watch-refresh")
            coEvery { api.refreshSession("refresh") } returns Result.success(refreshed)
            val app =
                koinApplication {
                    modules(
                        wearCloudSyncModule,
                        module {
                            single<KeyValueStorage> { storage }
                            single<SessionStorage> { mockk(relaxed = true) }
                            single<LogDateConfigRepository> { DefaultLogDateConfigRepository(initialBackendUrl = "https://watch.test") }
                            single<CloudApiClient>(WatchCloud) { api }
                        },
                    )
                }
            try {
                val accounts = app.koin.get<CloudAccountRepository>(WatchCloud)
                val refresh = assertIs<SessionRefreshingAccountRepository>(accounts)
                assertEquals(refreshed, refresh.refreshSession("refresh").getOrThrow())
                assertTrue(storage.values.values.none { it == "watch-access" || it == "watch-refresh" })
            } finally {
                app.close()
            }
        }
}
