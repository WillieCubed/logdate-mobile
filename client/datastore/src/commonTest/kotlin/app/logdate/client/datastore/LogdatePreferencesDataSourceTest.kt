package app.logdate.client.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [LogdatePreferencesDataSource], which provides a high-level API for
 * accessing and modifying application-wide user preferences.
 *
 * This suite verifies that default preference values are correctly set and that
 * many changes to settings (such as search visibility or feature toggles) are correctly
 * persisted to the underlying [DataStore].
 */
class LogdatePreferencesDataSourceTest {
    private val dataSource = LogdatePreferencesDataSource(TestPreferencesDataStore())

    @Test
    fun systemSearchVisibility_defaultsToEnabled() =
        runTest {
            assertTrue(dataSource.getSystemSearchVisibilityEnabled())
        }

    @Test
    fun peopleFeature_defaultsToEnabled() =
        runTest {
            assertEquals(true, dataSource.observePeopleEnabled().first())
        }

    @Test
    fun systemSearchVisibility_canBePersisted() =
        runTest {
            dataSource.setSystemSearchVisibilityEnabled(true)

            assertEquals(true, dataSource.getSystemSearchVisibilityEnabled())
        }

    @Test
    fun androidPlatformSearchIndexState_canBePersisted() =
        runTest {
            dataSource.setAndroidPlatformSearchIndexState(
                generation = 42L,
                schemaVersion = 3,
            )

            assertEquals(42L, dataSource.getAndroidPlatformSearchIndexedGeneration())
            assertEquals(3, dataSource.getAndroidPlatformSearchSchemaVersion())
        }

    @Test
    fun favoriteNotes_arePersistedAndDeduplicated() =
        runTest {
            dataSource.addFavoriteNotes(setOf("note-1", "note-2"))
            dataSource.addFavoriteNotes(setOf("note-2", "note-3"))

            assertEquals(
                setOf("note-1", "note-2", "note-3"),
                dataSource
                    .userData
                    .first()
                    .favoriteNotes
                    .toSet(),
            )
        }

    @Test
    fun hasSeenRewindOnboarding_defaultsToFalse() =
        runTest {
            assertEquals(false, dataSource.hasSeenRewindOnboarding())
        }

    @Test
    fun hasSeenRewindOnboarding_canBePersisted() =
        runTest {
            dataSource.setHasSeenRewindOnboarding(true)

            assertEquals(true, dataSource.hasSeenRewindOnboarding())
            assertEquals(true, dataSource.observeHasSeenRewindOnboarding().first())
        }

    @Test
    fun rewindCurationStrictness_defaultsToStandard() =
        runTest {
            assertEquals("STANDARD", dataSource.getRewindCurationStrictness())
        }

    @Test
    fun rewindCurationStrictness_canBePersisted() =
        runTest {
            dataSource.setRewindCurationStrictness("STRICT")

            assertEquals("STRICT", dataSource.getRewindCurationStrictness())
            assertEquals("STRICT", dataSource.observeRewindCurationStrictness().first())
        }

    @Test
    fun rewindIncludeScreenshots_defaultsToFalse() =
        runTest {
            assertEquals(false, dataSource.isRewindIncludeScreenshots())
        }

    @Test
    fun rewindIncludeScreenshots_canBePersisted() =
        runTest {
            dataSource.setRewindIncludeScreenshots(true)

            assertEquals(true, dataSource.isRewindIncludeScreenshots())
            assertEquals(true, dataSource.observeRewindIncludeScreenshots().first())
        }
}

/**
 * A test implementation of [DataStore<Preferences>] for testing.
 */
private class TestPreferencesDataStore : DataStore<Preferences> {
    private val preferencesFlow = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = preferencesFlow

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updatedPreferences = transform(preferencesFlow.value)
        preferencesFlow.value = updatedPreferences
        return updatedPreferences
    }
}
