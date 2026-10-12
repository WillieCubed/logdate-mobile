package app.logdate.client.domain.reminders

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.domain.recommendation.DefaultMemoriesSettingsRepository
import app.logdate.client.domain.recommendation.FakeKeyValueStorage
import app.logdate.client.domain.watch.DefaultWatchPreferencesRepository
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.StampedPreferenceStore
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.shared.model.user.UserData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class LegacyReminderMigrationTest {
    private val storage = FakeKeyValueStorage()
    private val preferences = StampedPreferenceStore(storage, PeerRole.PHONE)
    private val legacy = DefaultMemoriesSettingsRepository(storage)
    private val users = MutableStateFlow(UserData())
    private val clock =
        object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(1_000)
        }

    private fun migration(storage: KeyValueStorage = this.storage) = LegacyReminderMigration(storage, preferences, users, legacy, clock)

    private suspend fun enabled() = preferences.snapshot()[SharedPreferenceKey.RemindersEnabled.name]?.value

    @Test
    fun `established nudge user retains reminders`() =
        runTest {
            users.value = UserData(isOnboarded = true, onboardedDate = Instant.fromEpochMilliseconds(900))
            assertTrue(migration().ensure())
            assertEquals(JsonPrimitive(true), enabled())
        }

    @Test
    fun `legacy onboarding without a timestamp is retained`() =
        runTest {
            users.value = UserData(isOnboarded = true)
            assertTrue(migration().ensure())
            assertEquals(JsonPrimitive(true), enabled())
        }

    @Test
    fun `fresh install remains off across later onboarding and restart`() =
        runTest {
            assertTrue(migration().ensure())
            users.value = UserData(isOnboarded = true, onboardedDate = Instant.fromEpochMilliseconds(900))
            storage.remove(StampedPreferenceStore.STORAGE_KEY)
            assertTrue(migration().ensure())
            assertEquals(JsonPrimitive(false), enabled())
        }

    @Test
    fun `onboarding completed after construction cannot become legacy eligibility`() =
        runTest {
            val migration = migration()
            users.value = UserData(isOnboarded = true, onboardedDate = Instant.fromEpochMilliseconds(1_000))
            assertTrue(migration.ensure())
            assertEquals(JsonPrimitive(false), enabled())
        }

    @Test
    fun `each disabled legacy parent keeps reminders off`() =
        runTest {
            users.value = UserData(isOnboarded = true)
            for (disabled in listOf("contextual_recommendations", "ambient_prompts", "capture_nudges")) {
                val isolated = FakeKeyValueStorage()
                isolated.putBoolean("memories_${disabled}_enabled", false)
                val store = StampedPreferenceStore(isolated, PeerRole.PHONE)
                val migration = LegacyReminderMigration(isolated, store, users, DefaultMemoriesSettingsRepository(isolated), clock)
                assertTrue(migration.ensure())
                assertEquals(JsonPrimitive(false), store.snapshot()["reminders.enabled"]?.value, disabled)
            }
        }

    @Test
    fun `migration preserves an explicit off choice`() =
        runTest {
            users.value = UserData(isOnboarded = true)
            val explicit = preferences.write("reminders.enabled", JsonPrimitive(false))
            assertTrue(migration().ensure())
            assertEquals(explicit, preferences.snapshot()["reminders.enabled"])
        }

    @Test
    fun `unreadable preferences are not overwritten`() =
        runTest {
            users.value = UserData(isOnboarded = true)
            storage.putString(StampedPreferenceStore.STORAGE_KEY, "bad data")
            assertFalse(migration().ensure())
            assertEquals("bad data", storage.getString(StampedPreferenceStore.STORAGE_KEY))
        }

    @Test
    fun `dropped eligibility write cannot initialize reminders`() =
        runTest {
            users.value = UserData(isOnboarded = true)
            val dropping =
                object : KeyValueStorage by storage {
                    override suspend fun putString(
                        key: String,
                        value: String,
                    ) = Unit
                }
            assertFalse(migration(dropping).ensure())
            assertEquals(null, enabled())
        }

    @Test
    fun `invalid eligibility does not infer consent`() =
        runTest {
            users.value = UserData(isOnboarded = true)
            storage.putString(LegacyReminderMigration.STORAGE_KEY, "bad data")
            assertFalse(migration().ensure())
            assertEquals(null, enabled())
        }

    @Test
    fun `typed observation initializes legacy choice before emitting a default`() =
        runTest {
            users.value = UserData(isOnboarded = true)
            val migration = migration()
            val repository = DefaultWatchPreferencesRepository(preferences) { migration.ensure() }
            assertTrue(repository.observe(SharedPreferenceKey.RemindersEnabled).first())
        }

    @Test
    fun `typed read initializes legacy choice before returning a default`() =
        runTest {
            users.value = UserData(isOnboarded = true)
            val migration = migration()
            val repository = DefaultWatchPreferencesRepository(preferences) { migration.ensure() }
            assertTrue(repository.get(SharedPreferenceKey.RemindersEnabled))
        }
}
