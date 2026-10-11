package app.logdate.client.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.DeploymentKind
import app.logdate.shared.model.ServerDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LogDateConfigDataSourceTest {
    @Test
    fun `explicit persistence saves the new server before returning`() =
        runTest {
            val storage = ConfigTestDataStore()
            val config = DefaultLogDateConfigRepository()
            val source = LogDateConfigDataSource(storage, config, backgroundScope)
            source.awaitConfigurationLoaded()
            runCurrent()
            config.updateBackendUrl(LINKED)
            config.updateApiVersion("v2")
            val descriptor = ServerDescriptor(LINKED, "$LINKED/api/v2", "v2", DeploymentKind.SELF_HOSTED, "Linked")
            config.updateServerDescriptor(descriptor)
            source.persistCurrentConfiguration()
            assertEquals(LINKED, source.getBackendUrl().first())
            assertEquals("v2", source.getApiVersion().first())
            assertEquals(descriptor, source.getServerDescriptor().first())
        }

    @Test
    fun `an earlier queued collector cannot overwrite explicitly saved configuration`() =
        runTest {
            val storage = ConfigTestDataStore()
            val wait = CompletableDeferred<Unit>()
            var intercepted = false
            storage.beforeUpdate = {
                if (!intercepted) {
                    intercepted = true
                    wait.await()
                }
            }
            val config = DefaultLogDateConfigRepository()
            val source = LogDateConfigDataSource(storage, config, backgroundScope)
            source.awaitConfigurationLoaded()
            runCurrent()
            config.updateBackendUrl(LINKED)
            source.persistCurrentConfiguration()
            val start = storage.history.size
            wait.complete(Unit)
            runCurrent()
            assertTrue(
                storage.history.drop(start).all { preferences ->
                    preferences.asMap().entries.none { it.key.name == "backend_url" && it.value != LINKED }
                },
            )
            assertEquals(LINKED, source.getBackendUrl().first())
        }

    private companion object {
        const val LINKED = "https://linked.test"
    }
}

private class ConfigTestDataStore : DataStore<Preferences> {
    private val preferences = MutableStateFlow<Preferences>(emptyPreferences())
    val history = mutableListOf<Preferences>()
    var beforeUpdate: suspend () -> Unit = {}
    override val data: Flow<Preferences> = preferences

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        beforeUpdate()
        val updated = transform(preferences.value)
        preferences.value = updated
        history += updated
        return updated
    }
}
