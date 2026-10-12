package app.logdate.client.domain.reminders

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.recommendation.FakeKeyValueStorage
import app.logdate.client.domain.watch.DefaultWatchPreferencesRepository
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.StampedPreferenceStore
import app.logdate.client.sync.peer.state.PeerRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderPreferencesTest {
    private val storage = FakeKeyValueStorage()
    private val store = StampedPreferenceStore(storage, PeerRole.PHONE)
    private val shared = DefaultWatchPreferencesRepository(store)
    private val rollout = MutableStateFlow(true)
    private val flags =
        object : FeatureFlagStore {
            override fun observe(flag: FeatureFlag) = rollout

            override suspend fun setEnabled(
                flag: FeatureFlag,
                enabled: Boolean,
            ) {
                rollout.value = enabled
            }
        }
    private val preferences = ReminderPreferences(shared, flags)

    @Test fun freshInstallIsReadyButRemainsOptedOut() =
        runTest {
            val state = preferences.observe().first()
            assertTrue(state.ready)
            assertTrue(state.rolloutEnabled)
            assertFalse(state.enabled)
            assertFalse(state.capturePlaces)
            assertTrue(store.snapshot().isEmpty())
        }

    @Test fun explicitPhoneChoiceSurvivesRecreationWithoutChangingPlaces() =
        runTest {
            assertTrue(preferences.setEnabled(true))
            val reopened = ReminderPreferences(DefaultWatchPreferencesRepository(StampedPreferenceStore(storage, PeerRole.PHONE)), flags)
            assertTrue(reopened.observe().first().enabled)
            assertFalse(shared.get(SharedPreferenceKey.CapturePlaces))
            assertEquals(setOf(SharedPreferenceKey.RemindersEnabled.name), store.snapshot().keys)
            assertTrue(reopened.setEnabled(false))
            assertFalse(shared.get(SharedPreferenceKey.RemindersEnabled))
        }

    @Test fun watchChoiceIsTheSameChoiceDisplayedOnThePhone() =
        runTest {
            val watch = StampedPreferenceStore(FakeKeyValueStorage(), PeerRole.WATCH)
            watch.write(SharedPreferenceKey.RemindersEnabled.name, SharedPreferenceKey.RemindersEnabled.encode(true))
            store.adopt(watch.snapshot())
            assertTrue(preferences.observe().first().enabled)
            assertTrue(preferences.setEnabled(false))
            watch.adopt(store.snapshot())
            assertFalse(DefaultWatchPreferencesRepository(watch).get(SharedPreferenceKey.RemindersEnabled))
        }

    @Test fun rolloutHoldPreventsWritesButPreservesAnExistingChoice() =
        runTest {
            shared.set(SharedPreferenceKey.RemindersEnabled, true)
            rollout.value = false
            assertFalse(preferences.setEnabled(false))
            val state = preferences.observe().first()
            assertFalse(state.rolloutEnabled)
            assertTrue(state.enabled)
        }

    @Test fun saveFailurePropagatesWithoutPretendingTheChoiceChanged() =
        runTest {
            val failing =
                object : KeyValueStorage by storage {
                    override suspend fun putString(
                        key: String,
                        value: String,
                    ) {
                        error("Disk unavailable")
                    }
                }
            val preference = ReminderPreferences(DefaultWatchPreferencesRepository(StampedPreferenceStore(failing, PeerRole.PHONE)), flags)
            assertFailsWith<IllegalStateException> { preference.setEnabled(true) }
            assertFalse(shared.get(SharedPreferenceKey.RemindersEnabled))
        }

    @Test fun placesNeedsItsOwnExplicitChoiceAndDoesNotEnableReminders() =
        runTest {
            assertTrue(preferences.setPlacesEnabled(true))
            assertTrue(preferences.observe().first().capturePlaces)
            assertFalse(preferences.observe().first().enabled)
        }

    @Test fun storageThatDropsAWrittenChoiceDoesNotReportSuccess() =
        runTest {
            val dropping =
                object : KeyValueStorage by storage {
                    override suspend fun putString(
                        key: String,
                        value: String,
                    ) = Unit
                }
            val preference = ReminderPreferences(DefaultWatchPreferencesRepository(StampedPreferenceStore(dropping, PeerRole.PHONE)), flags)
            assertFalse(preference.setEnabled(true))
            assertFalse(preference.setEnabled(false))
            assertFalse(preference.setPlacesEnabled(true))
            assertFalse(preference.setPlacesEnabled(false))
            assertFalse(preference.observe().first().enabled)
            assertFalse(preference.observe().first().capturePlaces)
        }
}
