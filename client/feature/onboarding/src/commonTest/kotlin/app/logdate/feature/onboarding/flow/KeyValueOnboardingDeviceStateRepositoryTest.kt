package app.logdate.feature.onboarding.flow

import app.logdate.client.datastore.KeyValueStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Onboarding device state used to be held only in memory on iOS -- every
 * `*HandledOnThisDevice` flag and the active entry mode reset to default on process death,
 * so an iOS user interrupted mid-onboarding was re-asked for permissions they'd already
 * handled. This repository backs the same state with the shared, already cross-platform
 * [KeyValueStorage] abstraction instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KeyValueOnboardingDeviceStateRepositoryTest {
    private lateinit var fakeStorage: FakeKeyValueStorage

    @BeforeTest
    fun setup() {
        fakeStorage = FakeKeyValueStorage()
    }

    @Test
    fun `marking a step handled persists through the storage and is reflected in device state`() =
        runTest {
            val repository = createRepository()
            runCurrent()

            repository.markLocationHandled()
            runCurrent()
            repository.markNotificationsHandled()
            runCurrent()

            assertTrue(repository.deviceState.value.locationHandledOnThisDevice)
            assertTrue(repository.deviceState.value.notificationsHandledOnThisDevice)
            assertFalse(repository.deviceState.value.recommendationsHandledOnThisDevice)
            assertFalse(repository.deviceState.value.dayBoundariesHandledOnThisDevice)
        }

    @Test
    fun `a new repository instance reads state a prior instance persisted`() =
        runTest {
            val firstInstance = createRepository()
            runCurrent()
            firstInstance.markRecommendationsHandled()
            runCurrent()
            firstInstance.setActiveEntryMode(OnboardingEntryMode.CONTINUE_SETUP)
            runCurrent()

            // Simulates the app process restarting: a fresh repository over the same storage.
            val secondInstance = createRepository()
            runCurrent()

            assertTrue(secondInstance.deviceState.value.recommendationsHandledOnThisDevice)
            assertEquals(OnboardingEntryMode.CONTINUE_SETUP, secondInstance.deviceState.value.activeEntryMode)
        }

    @Test
    fun `clearing resets every flag without wiping the rest of the shared storage`() =
        runTest {
            val repository = createRepository()
            runCurrent()
            repository.markRecommendationsHandled()
            runCurrent()
            repository.markLocationHandled()
            runCurrent()
            repository.markDayBoundariesHandled()
            runCurrent()
            repository.markNotificationsHandled()
            runCurrent()
            repository.setActiveEntryMode(OnboardingEntryMode.CONTINUE_SETUP)
            runCurrent()
            fakeStorage.putString("unrelated_setting", "keep me")

            repository.clear()
            runCurrent()

            val state = repository.deviceState.value
            assertFalse(state.recommendationsHandledOnThisDevice)
            assertFalse(state.locationHandledOnThisDevice)
            assertFalse(state.dayBoundariesHandledOnThisDevice)
            assertFalse(state.notificationsHandledOnThisDevice)
            assertEquals(OnboardingEntryMode.FRESH, state.activeEntryMode)
            assertEquals("keep me", fakeStorage.getString("unrelated_setting"))
        }

    private fun TestScope.createRepository(): KeyValueOnboardingDeviceStateRepository =
        KeyValueOnboardingDeviceStateRepository(storage = fakeStorage, scope = backgroundScope)
}

private class FakeKeyValueStorage : KeyValueStorage {
    private val booleans = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val strings = mutableMapOf<String, MutableStateFlow<String?>>()

    private fun booleanFlow(
        key: String,
        default: Boolean,
    ): MutableStateFlow<Boolean> = booleans.getOrPut(key) { MutableStateFlow(default) }

    private fun stringFlow(key: String): MutableStateFlow<String?> = strings.getOrPut(key) { MutableStateFlow(null) }

    override suspend fun getString(key: String): String? = stringFlow(key).value

    override fun getStringSync(key: String): String? = stringFlow(key).value

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        stringFlow(key).value = value
    }

    override fun observeString(key: String): Flow<String?> = stringFlow(key)

    override suspend fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = booleanFlow(key, defaultValue).value

    override suspend fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        booleanFlow(key, value).value = value
    }

    override fun observeBoolean(
        key: String,
        defaultValue: Boolean,
    ): Flow<Boolean> = booleanFlow(key, defaultValue)

    override suspend fun getInt(
        key: String,
        defaultValue: Int,
    ): Int = defaultValue

    override suspend fun putInt(
        key: String,
        value: Int,
    ) = Unit

    override fun observeInt(
        key: String,
        defaultValue: Int,
    ): Flow<Int> = MutableStateFlow(defaultValue)

    override suspend fun getLong(
        key: String,
        defaultValue: Long,
    ): Long = defaultValue

    override suspend fun putLong(
        key: String,
        value: Long,
    ) = Unit

    override fun observeLong(
        key: String,
        defaultValue: Long,
    ): Flow<Long> = MutableStateFlow(defaultValue)

    override suspend fun getFloat(
        key: String,
        defaultValue: Float,
    ): Float = defaultValue

    override suspend fun putFloat(
        key: String,
        value: Float,
    ) = Unit

    override fun observeFloat(
        key: String,
        defaultValue: Float,
    ): Flow<Float> = MutableStateFlow(defaultValue)

    override suspend fun remove(key: String) {
        booleans[key]?.let { it.value = false }
        strings[key]?.let { it.value = null }
    }

    override suspend fun contains(key: String): Boolean = booleans[key]?.value == true || strings[key]?.value != null

    override suspend fun clear() {
        booleans.keys.forEach { booleans[it]?.value = false }
        strings.keys.forEach { strings[it]?.value = null }
    }
}
