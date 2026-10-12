package app.logdate.feature.onboarding.ui

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.reminders.ReminderPreferences
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.WatchPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderOnboardingChoiceTest {
    private val values = MutableStateFlow<Map<String, Any?>>(emptyMap())
    private var dropWrites = false
    private var rollout = true
    private var handled = 0
    private var failHandled = false
    private val shared =
        object : WatchPreferencesRepository {
            @Suppress("UNCHECKED_CAST")
            override fun <T> observe(key: SharedPreferenceKey<T>) = values.map { (it[key.name] ?: key.default) as T }

            @Suppress("UNCHECKED_CAST")
            override suspend fun <T> get(key: SharedPreferenceKey<T>): T = (values.value[key.name] ?: key.default) as T

            override suspend fun <T> set(
                key: SharedPreferenceKey<T>,
                value: T,
            ) {
                if (!dropWrites) values.value = values.value + (key.name to value)
            }
        }
    private val flags =
        object : FeatureFlagStore {
            override fun observe(flag: FeatureFlag) = MutableStateFlow(rollout)

            override suspend fun setEnabled(
                flag: FeatureFlag,
                enabled: Boolean,
            ) {
                rollout = enabled
            }
        }
    private val choice =
        ReminderOnboardingChoice(ReminderPreferences(shared, flags)) {
            if (failHandled) {
                Result.failure(IllegalStateException("Could not save progress"))
            } else {
                handled++
                Result.success(Unit)
            }
        }

    @Test fun explicitOptInIsSavedBeforeRequestingPermission() =
        runTest {
            assertEquals(ReminderOnboardingNext.REQUEST_PERMISSION, choice.choose(true, false).getOrThrow())
            assertTrue(shared.get(SharedPreferenceKey.RemindersEnabled))
            assertFalse(shared.get(SharedPreferenceKey.CapturePlaces))
            assertEquals(0, handled)
        }

    @Test fun alreadyGrantedPermissionStillRequiresAndSavesExplicitChoice() =
        runTest {
            assertFalse(shared.get(SharedPreferenceKey.RemindersEnabled))
            assertEquals(ReminderOnboardingNext.CONTINUE, choice.choose(true, true).getOrThrow())
            assertTrue(shared.get(SharedPreferenceKey.RemindersEnabled))
            assertEquals(1, handled)
        }

    @Test fun notNowDisablesAnExistingWatchChoiceAndCompletesWithoutPermission() =
        runTest {
            shared.set(SharedPreferenceKey.RemindersEnabled, true)
            assertEquals(ReminderOnboardingNext.CONTINUE, choice.choose(false, false).getOrThrow())
            assertFalse(shared.get(SharedPreferenceKey.RemindersEnabled))
            assertEquals(1, handled)
        }

    @Test fun failedChoiceCannotRequestPermissionOrCompleteTheStep() =
        runTest {
            dropWrites = true
            assertTrue(choice.choose(true, false).isFailure)
            assertTrue(choice.choose(true, true).isFailure)
            assertEquals(0, handled)
        }

    @Test fun failedProgressCanRetryWithoutLosingTheChosenPreference() =
        runTest {
            failHandled = true
            assertTrue(choice.choose(true, true).isFailure)
            assertTrue(shared.get(SharedPreferenceKey.RemindersEnabled))
            failHandled = false
            assertEquals(ReminderOnboardingNext.CONTINUE, choice.choose(true, true).getOrThrow())
            assertEquals(1, handled)
        }

    @Test fun rolloutHoldDoesNotWriteOrAdvance() =
        runTest {
            rollout = false
            assertTrue(choice.choose(true, true).isFailure)
            assertFalse(shared.get(SharedPreferenceKey.RemindersEnabled))
            assertEquals(0, handled)
        }

    @Test fun cancellationIsNotConvertedIntoASaveError() =
        runTest {
            val cancelled = ReminderOnboardingChoice(ReminderPreferences(shared, flags)) { throw CancellationException("Stopped") }
            assertFailsWith<CancellationException> { cancelled.choose(true, true) }
        }
}
