package app.logdate.reminders.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.PrefixedKeyValueStorage
import app.logdate.client.domain.recommendation.DefaultMemoriesSettingsRepository
import app.logdate.client.domain.reminders.LegacyReminderMigration
import app.logdate.client.domain.watch.DefaultWatchPreferencesRepository
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.StampedPreferenceStore
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.Stamped
import app.logdate.shared.model.user.UserData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Uses the installed app's DataStore without changing its active settings or onboarding metadata. */
@RunWith(AndroidJUnit4::class)
class LegacyReminderMigrationRuntimeTest {
    private val storage = PrefixedKeyValueStorage(KoinJavaComponent.getKoin().get<KeyValueStorage>(), "migration_runtime_test:")
    private val users = MutableStateFlow(UserData())
    private val clock =
        object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(1_000)
        }

    private fun repository(): DefaultWatchPreferencesRepository {
        val store = StampedPreferenceStore(storage, PeerRole.PHONE)
        val migration = LegacyReminderMigration(storage, store, users, DefaultMemoriesSettingsRepository(storage), clock)
        return DefaultWatchPreferencesRepository(store) { migration.ensure() }
    }

    @Before @After
    fun cleanUp() =
        runBlocking {
            storage.remove(StampedPreferenceStore.STORAGE_KEY)
            storage.remove(LegacyReminderMigration.STORAGE_KEY)
        }

    @Test(timeout = 30_000)
    fun establishedNudgeUserSurvivesRepositoryRecreation() =
        runBlocking {
            withTimeout(15_000) {
                users.value = UserData(isOnboarded = true, onboardedDate = Instant.fromEpochMilliseconds(900))
                assertTrue(repository().observe(SharedPreferenceKey.RemindersEnabled).first())
                assertTrue(repository().get(SharedPreferenceKey.RemindersEnabled))
            }
        }

    @Test(timeout = 30_000)
    fun freshUserStaysOffAfterOnboardingAndRepositoryRecreation() =
        runBlocking {
            withTimeout(15_000) {
                assertFalse(repository().get(SharedPreferenceKey.RemindersEnabled))
                users.value = UserData(isOnboarded = true, onboardedDate = Instant.fromEpochMilliseconds(900))
                assertFalse(repository().observe(SharedPreferenceKey.RemindersEnabled).first())
            }
        }

    @Test(timeout = 30_000)
    fun retainedWatchOffChoiceWinsAfterLegacyImport() =
        runBlocking {
            withTimeout(15_000) {
                users.value = UserData(isOnboarded = true)
                val repository = repository()
                assertTrue(repository.get(SharedPreferenceKey.RemindersEnabled))
                val store = StampedPreferenceStore(storage, PeerRole.PHONE)
                assertTrue(store.adopt(mapOf("reminders.enabled" to Stamped(JsonPrimitive(false), 900))))
                assertFalse(repository().observe(SharedPreferenceKey.RemindersEnabled).first())
            }
        }
}
