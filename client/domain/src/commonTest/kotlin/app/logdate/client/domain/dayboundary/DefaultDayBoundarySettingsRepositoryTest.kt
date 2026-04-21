@file:Suppress("ktlint:standard:function-naming")

package app.logdate.client.domain.dayboundary

import app.logdate.client.datastore.KeyValueStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests the persistence and retrieval of day boundary settings in [DefaultDayBoundarySettingsRepository].
 *
 * These tests ensure that user preferences for sleep-based boundaries are correctly stored
 * and retrieved from [KeyValueStorage], and that the repository's observable flows correctly
 * reflect updates to these settings.
 */
class DefaultDayBoundarySettingsRepositoryTest {
    @Test
    fun `default settings have sleep boundaries disabled`() =
        runTest {
            val repo = DefaultDayBoundarySettingsRepository(InMemoryKeyValueStorage())
            val settings = repo.getSettings()
            assertEquals(false, settings.sleepBasedBoundariesEnabled)
        }

    @Test
    fun `toggle disables sleep boundaries`() =
        runTest {
            val repo = DefaultDayBoundarySettingsRepository(InMemoryKeyValueStorage())
            repo.setSleepBasedBoundariesEnabled(false)
            val settings = repo.getSettings()
            assertEquals(false, settings.sleepBasedBoundariesEnabled)
        }

    @Test
    fun `observe reflects changes`() =
        runTest {
            val repo = DefaultDayBoundarySettingsRepository(InMemoryKeyValueStorage())
            repo.setSleepBasedBoundariesEnabled(false)
            val observed = repo.observeSettings().first()
            assertEquals(false, observed.sleepBasedBoundariesEnabled)
        }

    @Test
    fun `enable request persists even when health connect is not ready`() =
        runTest {
            val repo = DefaultDayBoundarySettingsRepository(InMemoryKeyValueStorage())

            repo.setSleepBasedBoundariesEnabled(true)

            val settings = repo.getSettings()
            assertEquals(true, settings.sleepBasedBoundariesEnabled)
        }

    @Test
    fun `stored enabled value is preserved when health data is unavailable`() =
        runTest {
            val storage = InMemoryKeyValueStorage()
            storage.putBoolean("day_boundary_sleep_based_enabled", true)
            val repo = DefaultDayBoundarySettingsRepository(storage)

            val settings = repo.getSettings()

            assertEquals(true, settings.sleepBasedBoundariesEnabled)
            assertEquals(true, storage.getBoolean("day_boundary_sleep_based_enabled", false))
        }
}

private class InMemoryKeyValueStorage : KeyValueStorage {
    private val booleans = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val strings = mutableMapOf<String, String?>()
    private val ints = mutableMapOf<String, Int>()
    private val longs = mutableMapOf<String, Long>()
    private val floats = mutableMapOf<String, Float>()

    override suspend fun getString(key: String): String? = strings[key]

    override fun getStringSync(key: String): String? = strings[key]

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        strings[key] = value
    }

    override fun observeString(key: String): Flow<String?> = MutableStateFlow(strings[key])

    override suspend fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = booleans[key]?.value ?: defaultValue

    override suspend fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        booleans.getOrPut(key) { MutableStateFlow(value) }.value = value
    }

    override fun observeBoolean(
        key: String,
        defaultValue: Boolean,
    ): Flow<Boolean> = booleans.getOrPut(key) { MutableStateFlow(defaultValue) }

    override suspend fun getInt(
        key: String,
        defaultValue: Int,
    ): Int = ints[key] ?: defaultValue

    override suspend fun putInt(
        key: String,
        value: Int,
    ) {
        ints[key] = value
    }

    override fun observeInt(
        key: String,
        defaultValue: Int,
    ): Flow<Int> = MutableStateFlow(ints[key] ?: defaultValue)

    override suspend fun getLong(
        key: String,
        defaultValue: Long,
    ): Long = longs[key] ?: defaultValue

    override suspend fun putLong(
        key: String,
        value: Long,
    ) {
        longs[key] = value
    }

    override fun observeLong(
        key: String,
        defaultValue: Long,
    ): Flow<Long> = MutableStateFlow(longs[key] ?: defaultValue)

    override suspend fun getFloat(
        key: String,
        defaultValue: Float,
    ): Float = floats[key] ?: defaultValue

    override suspend fun putFloat(
        key: String,
        value: Float,
    ) {
        floats[key] = value
    }

    override fun observeFloat(
        key: String,
        defaultValue: Float,
    ): Flow<Float> = MutableStateFlow(floats[key] ?: defaultValue)

    override suspend fun remove(key: String) {
        strings.remove(key)
        booleans.remove(key)
        ints.remove(key)
        longs.remove(key)
        floats.remove(key)
    }

    override suspend fun contains(key: String): Boolean = key in strings || key in booleans || key in ints || key in longs || key in floats

    override suspend fun clear() {
        strings.clear()
        booleans.clear()
        ints.clear()
        longs.clear()
        floats.clear()
    }
}
