package app.logdate.feature.core.restore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.datastore.LogDateConfigDataSource
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.config.LogDateConfigRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveRecoveryConfigurationTest {
    @Test
    fun `recovery cannot capture an owner or origin before saved configuration loads`() =
        runTest {
            val loaded = CompletableDeferred<Unit>()
            val savedOrigin = "https://journal.example.test"
            var preferences = preferencesOf(stringPreferencesKey("backend_url") to savedOrigin)
            val storage =
                object : DataStore<Preferences> {
                    override val data =
                        flow {
                            loaded.await()
                            emit(preferences)
                        }

                    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                        preferences = transform(preferences)
                        return preferences
                    }
                }
            val config = DefaultLogDateConfigRepository()
            val persisted = LogDateConfigDataSource(storage, config, backgroundScope)
            var ownerReads = 0
            val owner =
                object : CanonicalOwnerProvider {
                    override suspend fun getCanonicalOwnerId(): String {
                        ownerReads++
                        return "retained-owner"
                    }

                    override suspend fun hasBoundOwner() = true
                }
            val graph =
                koinApplication {
                    modules(
                        module {
                            single<CanonicalOwnerProvider> { owner }
                            single<LogDateConfigRepository> { config }
                            single { persisted }
                        },
                    )
                }
            try {
                val captured = async { currentArchiveRecoveryScope(graph.koin) }
                runCurrent()
                assertFalse(captured.isCompleted, "Recovery must not bind the temporary default server")
                assertEquals(0, ownerReads, "Recovery must wait before creating or reading installation identity")
                loaded.complete(Unit)
                assertEquals(ArchiveRecoveryScope("retained-owner", savedOrigin), captured.await())
            } finally {
                graph.close()
            }
        }
}
