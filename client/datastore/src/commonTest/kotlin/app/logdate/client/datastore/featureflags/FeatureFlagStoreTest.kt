package app.logdate.client.datastore.featureflags

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import app.logdate.client.datastore.LogdatePreferencesDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeatureFlagStoreTest {
    private val preferences = TestPreferencesDataStore()
    private val store = DataStoreFeatureFlagStore(preferences)

    @Test
    fun unsetFlag_usesItsDeclaredDefault() =
        runTest {
            assertFalse(store.isEnabled(FeatureFlag.LIBRARY))
            assertTrue(store.isEnabled(FeatureFlag.EVENTS))
            assertTrue(store.isEnabled(FeatureFlag.PEOPLE))
        }

    @Test
    fun setEnabled_persistsAndOverridesTheDefault() =
        runTest {
            store.setEnabled(FeatureFlag.LIBRARY, enabled = true)
            store.setEnabled(FeatureFlag.EVENTS, enabled = false)

            assertTrue(store.isEnabled(FeatureFlag.LIBRARY))
            assertFalse(store.isEnabled(FeatureFlag.EVENTS))
        }

    @Test
    fun observe_emitsTheCurrentValue() =
        runTest {
            assertFalse(store.observe(FeatureFlag.LIBRARY).first())

            store.setEnabled(FeatureFlag.LIBRARY, enabled = true)

            assertTrue(store.observe(FeatureFlag.LIBRARY).first())
        }

    @Test
    fun flagsAreStoredUnderTheirDeclaredKey() =
        runTest {
            store.setEnabled(FeatureFlag.LIBRARY, enabled = true)

            val stored = preferences.data.first()[booleanPreferencesKey("library_enabled")]

            assertEquals(true, stored)
        }

    /**
     * The three flags that predate this type are also read through their own accessors on
     * [LogdatePreferencesDataSource]. Both paths have to agree, or a feature is on according to
     * one caller and off according to another.
     */
    @Test
    fun sharesStorageWithTheExistingFeatureAccessors() =
        runTest {
            val dataSource = LogdatePreferencesDataSource(preferences)

            store.setEnabled(FeatureFlag.LIBRARY, enabled = true)
            assertTrue(dataSource.observeLibraryEnabled().first())

            dataSource.setLibraryEnabled(false)
            assertFalse(store.isEnabled(FeatureFlag.LIBRARY))
        }

    /**
     * The defaults declared on the flags have to match what the existing accessors already return,
     * otherwise introducing the registry silently changes what a feature does on a fresh install.
     */
    @Test
    fun defaultsMatchTheExistingAccessors() =
        runTest {
            val dataSource = LogdatePreferencesDataSource(preferences)

            assertEquals(dataSource.observeLibraryEnabled().first(), FeatureFlag.LIBRARY.defaultEnabled)
            assertEquals(dataSource.observeEventsEnabled().first(), FeatureFlag.EVENTS.defaultEnabled)
            assertEquals(dataSource.observePeopleEnabled().first(), FeatureFlag.PEOPLE.defaultEnabled)
        }

    @Test
    fun forKey_findsAFlagAndRejectsAnUnknownKey() {
        assertEquals(FeatureFlag.LIBRARY, FeatureFlag.forKey("library_enabled"))
        assertNull(FeatureFlag.forKey("not_a_flag"))
    }

    @Test
    fun everyFlagOwnsADistinctKey() {
        val keys = FeatureFlag.entries.map { it.key }

        assertEquals(keys.size, keys.toSet().size)
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
