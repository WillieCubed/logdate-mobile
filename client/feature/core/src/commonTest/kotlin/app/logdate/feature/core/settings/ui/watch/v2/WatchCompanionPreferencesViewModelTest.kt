package app.logdate.feature.core.settings.ui.watch.v2

import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.WatchPreferencesRepository
import app.logdate.client.sync.peer.state.Stamped
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WatchCompanionPreferencesViewModelTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `audio download choice persists through verified shared preference write`() =
        runTest {
            val repository = Preferences()
            val model = WatchCompanionPreferencesViewModel(repository)
            backgroundScope.launch { model.state.collect {} }
            advanceUntilIdle()
            val policy = app.logdate.client.domain.watch.WatchAudioDownloadPolicy.ON_DEMAND
            model.set(SharedPreferenceKey.AudioDownloadPolicy, policy.stored)
            advanceUntilIdle()
            assertEquals(policy, model.state.value?.audioDownloads)
            assertEquals(1, repository.verified)
            assertFalse(model.saveFailed.value)
        }

    @Test
    fun `library and capture choices follow shared preferences including peer changes`() =
        runTest {
            val repository = Preferences()
            val model = WatchCompanionPreferencesViewModel(repository)
            backgroundScope.launch { model.state.collect {} }
            advanceUntilIdle()
            assertEquals(WatchCompanionPreferences(), model.state.value)

            repository.set(SharedPreferenceKey.WindowDays, 30)
            repository.set(SharedPreferenceKey.WindowOnThisDay, false)
            repository.set(SharedPreferenceKey.WindowRewind, false)
            repository.set(SharedPreferenceKey.CapturePlaces, true)
            repository.set(SharedPreferenceKey.MoodAfterRecording, false)
            advanceUntilIdle()
            assertEquals(WatchCompanionPreferences(30, false, false, true, false), model.state.value)
        }

    @Test
    fun `a saved choice is verified and reflected without optimistic state`() =
        runTest {
            val repository = Preferences()
            val model = WatchCompanionPreferencesViewModel(repository)
            backgroundScope.launch { model.state.collect {} }
            advanceUntilIdle()
            model.set(SharedPreferenceKey.WindowDays, 7)
            advanceUntilIdle()
            assertEquals(7, model.state.value?.days)
            assertEquals(1, repository.verified)
            assertFalse(model.saveFailed.value)
            assertFalse(model.saving.value)
        }

    @Test
    fun `a dropped write shows failure and retains the saved value then allows retry`() =
        runTest {
            val repository = Preferences()
            val model = WatchCompanionPreferencesViewModel(repository)
            backgroundScope.launch { model.state.collect {} }
            advanceUntilIdle()
            repository.drop = true
            model.set(SharedPreferenceKey.WindowDays, 7)
            advanceUntilIdle()
            assertTrue(model.saveFailed.value)
            assertEquals(14, model.state.value?.days)
            repository.drop = false
            model.set(SharedPreferenceKey.WindowDays, 7)
            advanceUntilIdle()
            assertFalse(model.saveFailed.value)
            assertEquals(7, model.state.value?.days)
        }

    @Test
    fun `invalid window length is rejected without touching preferences`() =
        runTest {
            val repository = Preferences()
            val model = WatchCompanionPreferencesViewModel(repository)
            model.set(SharedPreferenceKey.WindowDays, 21)
            advanceUntilIdle()
            assertTrue(model.saveFailed.value)
            assertEquals(0, repository.verified)
        }

    @Test
    fun `a pending save disables controls and refuses overlapping writes`() =
        runTest {
            val repository = Preferences()
            repository.waitFor = CompletableDeferred()
            val model = WatchCompanionPreferencesViewModel(repository)
            model.set(SharedPreferenceKey.WindowDays, 7)
            assertTrue(model.saving.value)
            model.set(SharedPreferenceKey.WindowDays, 30)
            runCurrent()
            assertEquals(1, repository.verified)
            repository.waitFor?.complete(Unit)
            advanceUntilIdle()
            assertFalse(model.saving.value)
            assertEquals(7, repository.get(SharedPreferenceKey.WindowDays))
        }

    @Test
    fun `cancellation does not become a failed save`() =
        runTest {
            val repository = Preferences()
            repository.cancel = true
            val model = WatchCompanionPreferencesViewModel(repository)
            model.set(SharedPreferenceKey.WindowDays, 7)
            advanceUntilIdle()
            assertFalse(model.saving.value)
            assertFalse(model.saveFailed.value)
            assertEquals(14, repository.get(SharedPreferenceKey.WindowDays))
        }

    private class Preferences : WatchPreferencesRepository {
        private val values = MutableStateFlow<Map<String, Stamped>>(emptyMap())
        var drop = false
        var verified = 0
        var cancel = false
        var waitFor: CompletableDeferred<Unit>? = null

        override fun <T> observe(key: SharedPreferenceKey<T>): Flow<T> = values.map { key.read(it[key.name]) }

        override suspend fun <T> get(key: SharedPreferenceKey<T>): T = key.read(values.value[key.name])

        override suspend fun <T> set(
            key: SharedPreferenceKey<T>,
            value: T,
        ) {
            values.value = values.value + (key.name to Stamped(key.encode(value), 1))
        }

        override suspend fun <T> setAndVerify(
            key: SharedPreferenceKey<T>,
            value: T,
        ): Boolean {
            verified++
            if (cancel) throw CancellationException("Cancelled test write")
            waitFor?.await()
            if (drop) return false
            set(key, value)
            return true
        }
    }
}
