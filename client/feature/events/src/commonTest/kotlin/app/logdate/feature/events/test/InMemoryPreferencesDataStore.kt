package app.logdate.feature.events.test

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Minimal in-memory `DataStore<Preferences>` used by the events feature ViewModel tests.
 *
 * Real production code talks to AndroidX DataStore via [LogdatePreferencesDataSource].
 * For unit tests we don't want a temp file or a process-bound singleton, so this fake
 * keeps the underlying [Preferences] in a [MutableStateFlow] and serializes
 * `updateData` calls behind a [Mutex] to mirror the real store's atomicity contract.
 *
 * That last detail matters: `LogdatePreferencesDataSource.recordEventInferenceRun`
 * relies on read-modify-write inside one `updateData` block. A naive fake that drops
 * the mutex would let two concurrent records race and the worker stats tests would go
 * silently green on broken code.
 */
class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(mutablePreferencesOf())
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock {
            val updated = transform(state.value)
            state.value = updated
            updated
        }
}
