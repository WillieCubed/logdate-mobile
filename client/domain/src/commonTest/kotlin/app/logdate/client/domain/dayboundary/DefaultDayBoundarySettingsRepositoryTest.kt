@file:Suppress("ktlint:standard:function-naming")

package app.logdate.client.domain.dayboundary

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.health.LocalFirstHealthRepository
import app.logdate.client.health.model.DayBounds
import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultDayBoundarySettingsRepositoryTest {
    @Test
    fun `default settings have sleep boundaries disabled`() =
        runTest {
            val repo = DefaultDayBoundarySettingsRepository(InMemoryKeyValueStorage(), FakeHealthRepository())
            val settings = repo.getSettings()
            assertEquals(false, settings.sleepBasedBoundariesEnabled)
        }

    @Test
    fun `toggle disables sleep boundaries`() =
        runTest {
            val repo = DefaultDayBoundarySettingsRepository(InMemoryKeyValueStorage(), FakeHealthRepository())
            repo.setSleepBasedBoundariesEnabled(false)
            val settings = repo.getSettings()
            assertEquals(false, settings.sleepBasedBoundariesEnabled)
        }

    @Test
    fun `observe reflects changes`() =
        runTest {
            val repo = DefaultDayBoundarySettingsRepository(InMemoryKeyValueStorage(), FakeHealthRepository())
            repo.setSleepBasedBoundariesEnabled(false)
            val observed = repo.observeSettings().first()
            assertEquals(false, observed.sleepBasedBoundariesEnabled)
        }

    @Test
    fun `enable request is rejected when sleep permissions are missing`() =
        runTest {
            val repo =
                DefaultDayBoundarySettingsRepository(
                    InMemoryKeyValueStorage(),
                    FakeHealthRepository(
                        isHealthAvailable = true,
                        hasSleepPermissions = false,
                    ),
                )

            repo.setSleepBasedBoundariesEnabled(true)

            val settings = repo.getSettings()
            assertEquals(false, settings.sleepBasedBoundariesEnabled)
        }

    @Test
    fun `stored enabled value is sanitized when health data is unavailable`() =
        runTest {
            val storage = InMemoryKeyValueStorage()
            storage.putBoolean("day_boundary_sleep_based_enabled", true)
            val repo =
                DefaultDayBoundarySettingsRepository(
                    storage,
                    FakeHealthRepository(
                        isHealthAvailable = false,
                        hasSleepPermissions = false,
                    ),
                )

            val settings = repo.getSettings()

            assertEquals(false, settings.sleepBasedBoundariesEnabled)
            assertEquals(false, storage.getBoolean("day_boundary_sleep_based_enabled", true))
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

private class FakeHealthRepository(
    private val isHealthAvailable: Boolean = true,
    private val hasSleepPermissions: Boolean = true,
) : LocalFirstHealthRepository {
    override suspend fun isHealthDataAvailable(): Boolean = isHealthAvailable

    override suspend fun getAvailableDataTypes(): List<String> = emptyList()

    override suspend fun hasSleepPermissions(): Boolean = hasSleepPermissions

    override suspend fun requestSleepPermissions(): Boolean = hasSleepPermissions

    override suspend fun getSleepSessions(
        start: kotlin.time.Instant,
        end: kotlin.time.Instant,
    ): List<SleepSession> = emptyList()

    override suspend fun getAverageWakeUpTime(
        timeZone: TimeZone,
        days: Int,
    ): TimeOfDay? = null

    override suspend fun getAverageSleepTime(
        timeZone: TimeZone,
        days: Int,
    ): TimeOfDay? = null

    override suspend fun getDayBoundsForDate(
        date: LocalDate,
        timeZone: TimeZone,
        sleepBasedBoundariesEnabled: Boolean,
    ): DayBounds = error("Not used in settings repository tests")
}
