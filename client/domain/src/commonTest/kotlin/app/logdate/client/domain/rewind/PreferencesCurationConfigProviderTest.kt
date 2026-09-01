package app.logdate.client.domain.rewind

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.intelligence.curation.CurationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreferencesCurationConfigProviderTest {
    private val dataSource = LogdatePreferencesDataSource(TestPreferencesDataStore())
    private val provider = PreferencesCurationConfigProvider(dataSource)

    @Test
    fun `defaults to standard strictness excluding screenshots`() =
        runTest {
            val config = provider.get()
            assertEquals(CurationConfig.Strictness.STANDARD, config.strictness)
            assertTrue(config.excludeScreenshots, "STANDARD strictness should exclude screenshots by default")
        }

    @Test
    fun `strict strictness is resolved`() =
        runTest {
            dataSource.setRewindCurationStrictness("STRICT")
            val config = provider.get()
            assertEquals(CurationConfig.Strictness.STRICT, config.strictness)
            // STRICT preset caps the per-beat output more aggressively than STANDARD.
            assertEquals(2, config.maxItemsPerBeat)
        }

    @Test
    fun `lenient strictness is resolved`() =
        runTest {
            dataSource.setRewindCurationStrictness("LENIENT")
            val config = provider.get()
            assertEquals(CurationConfig.Strictness.LENIENT, config.strictness)
        }

    @Test
    fun `unknown strictness falls back to standard`() =
        runTest {
            // Older releases may have written an enum name we no longer ship.
            dataSource.setRewindCurationStrictness("RUTHLESS")
            val config = provider.get()
            assertEquals(CurationConfig.Strictness.STANDARD, config.strictness)
        }

    @Test
    fun `include screenshots overrides strictness default`() =
        runTest {
            dataSource.setRewindIncludeScreenshots(true)
            val config = provider.get()
            assertFalse(config.excludeScreenshots, "include-screenshots preference should flip excludeScreenshots off")
        }

    @Test
    fun `include screenshots layers on strict strictness`() =
        runTest {
            dataSource.setRewindCurationStrictness("STRICT")
            dataSource.setRewindIncludeScreenshots(true)
            val config = provider.get()
            assertEquals(CurationConfig.Strictness.STRICT, config.strictness)
            assertFalse(config.excludeScreenshots)
        }
}

private class TestPreferencesDataStore : DataStore<Preferences> {
    private val preferencesFlow = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = preferencesFlow

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updatedPreferences = transform(preferencesFlow.value)
        preferencesFlow.value = updatedPreferences
        return updatedPreferences
    }
}
