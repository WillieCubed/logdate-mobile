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
import kotlin.time.Instant

class ProfileBirthdayPersistenceTest {
    @Test
    fun `birthday saves the UTC calendar day without time`() =
        runTest {
            val store = BirthdayPreferencesDataStore()
            val source = LogdatePreferencesDataSource(store)

            source.setBirthdate(Instant.parse("1992-06-03T18:25:00Z"))

            assertEquals(Instant.parse("1992-06-03T00:00:00Z"), source.userData.first().birthday)
        }

    @Test
    fun `legacy birthday timestamp reads as its UTC calendar day`() =
        runTest {
            val store = BirthdayPreferencesDataStore()
            store.updateData { original ->
                original.toMutablePreferences().apply {
                    this[LogdatePreferencesDataSource.BIRTHDAY] = Instant.parse("1992-06-03T18:25:00Z").toEpochMilliseconds()
                }
            }

            assertEquals(
                Instant.parse("1992-06-03T00:00:00Z"),
                LogdatePreferencesDataSource(store).userData.first().birthday,
            )
        }
}

private class BirthdayPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}
