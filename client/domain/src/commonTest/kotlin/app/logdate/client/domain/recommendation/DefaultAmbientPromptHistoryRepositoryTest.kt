package app.logdate.client.domain.recommendation

import app.logdate.client.datastore.KeyValueStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Unit tests for [DefaultAmbientPromptHistoryRepository].
 *
 * Tests the history tracking and suppression logic for ambient prompts,
 * ensuring that cooldown periods, daily limits, and deduplication rules
 * are correctly enforced to prevent prompt fatigue.
 */
class DefaultAmbientPromptHistoryRepositoryTest {
    @Test
    fun `suppresses any prompt when another prompt was shown within the last hour`() =
        runTest {
            val storage = FakeKeyValueStorage()
            val repository =
                DefaultAmbientPromptHistoryRepository(
                    keyValueStorage = storage,
                    now = { Instant.fromEpochMilliseconds(1_000_000L) },
                )
            val earlierCandidate = captureCandidate(dedupeKey = "capture:morning:2026-04-02")
            val currentCandidate =
                AmbientPromptCandidate(
                    family = AmbientPromptFamily.MEMORY_RECALL,
                    score = 80,
                    dedupeKey = "recall:2024-04-02",
                    payload =
                        AmbientPromptPayload.MemoryRecall(
                            date = LocalDate.parse("2024-04-02"),
                            summary = "A walk you still remember",
                        ),
                )

            repository.recordShown(earlierCandidate, Instant.fromEpochMilliseconds(1_000_000L - 30.minutesInMillis))

            assertFalse(repository.canShow(currentCandidate))
        }

    @Test
    fun `allows capture nudges again after the minimum family cooldown expires`() =
        runTest {
            val baseTime = Instant.parse("2026-04-02T08:00:00Z")
            val storage = FakeKeyValueStorage()
            val repository =
                DefaultAmbientPromptHistoryRepository(
                    keyValueStorage = storage,
                    now = { baseTime + 4.hours },
                )
            val firstCandidate = captureCandidate(dedupeKey = "capture:morning:2026-04-02")
            val secondCandidate = captureCandidate(dedupeKey = "capture:novel_place:red-rock")

            repository.recordShown(firstCandidate, baseTime)

            assertTrue(repository.canShow(secondCandidate))
        }

    @Test
    fun `enforces the daily maximum for capture nudges`() =
        runTest {
            val baseTime = Instant.parse("2026-04-02T08:00:00Z")
            val storage = FakeKeyValueStorage()
            val repository =
                DefaultAmbientPromptHistoryRepository(
                    keyValueStorage = storage,
                    now = { baseTime + 8.hours },
                )

            repository.recordShown(captureCandidate("capture:morning:2026-04-02"), baseTime)
            repository.recordShown(captureCandidate("capture:novel_place:red-rock"), baseTime + 4.hours)

            assertFalse(repository.canShow(captureCandidate("capture:evening:2026-04-02")))
        }

    private fun captureCandidate(dedupeKey: String): AmbientPromptCandidate =
        AmbientPromptCandidate(
            family = AmbientPromptFamily.CAPTURE_NUDGES,
            score = 50,
            dedupeKey = dedupeKey,
            payload = AmbientPromptPayload.CaptureNudge(AmbientCaptureNudgeStyle.MORNING),
        )
}

private const val MINUTE_MILLIS = 60 * 1000L

private val Int.minutesInMillis: Long
    get() = this * MINUTE_MILLIS

internal class FakeKeyValueStorage : KeyValueStorage {
    private val store = mutableMapOf<String, Any?>()
    private val stringFlows = mutableMapOf<String, MutableStateFlow<String?>>()
    private val booleanFlows = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val intFlows = mutableMapOf<String, MutableStateFlow<Int>>()
    private val longFlows = mutableMapOf<String, MutableStateFlow<Long>>()
    private val floatFlows = mutableMapOf<String, MutableStateFlow<Float>>()

    override suspend fun getString(key: String): String? = store[key] as? String

    override fun getStringSync(key: String): String? = store[key] as? String

    override suspend fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = store[key] as? Boolean ?: defaultValue

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        store[key] = value
        stringFlows.getOrPut(key) { MutableStateFlow(value) }.value = value
    }

    override suspend fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        store[key] = value
        booleanFlows.getOrPut(key) { MutableStateFlow(value) }.value = value
    }

    override suspend fun getInt(
        key: String,
        defaultValue: Int,
    ): Int = store[key] as? Int ?: defaultValue

    override suspend fun putInt(
        key: String,
        value: Int,
    ) {
        store[key] = value
        intFlows.getOrPut(key) { MutableStateFlow(value) }.value = value
    }

    override suspend fun getLong(
        key: String,
        defaultValue: Long,
    ): Long = store[key] as? Long ?: defaultValue

    override suspend fun putLong(
        key: String,
        value: Long,
    ) {
        store[key] = value
        longFlows.getOrPut(key) { MutableStateFlow(value) }.value = value
    }

    override suspend fun getFloat(
        key: String,
        defaultValue: Float,
    ): Float = store[key] as? Float ?: defaultValue

    override suspend fun putFloat(
        key: String,
        value: Float,
    ) {
        store[key] = value
        floatFlows.getOrPut(key) { MutableStateFlow(value) }.value = value
    }

    override suspend fun remove(key: String) {
        store.remove(key)
        stringFlows.remove(key)
        booleanFlows.remove(key)
        intFlows.remove(key)
        longFlows.remove(key)
        floatFlows.remove(key)
    }

    override suspend fun contains(key: String): Boolean = store.containsKey(key)

    override suspend fun clear() {
        store.clear()
        stringFlows.clear()
        booleanFlows.clear()
        intFlows.clear()
        longFlows.clear()
        floatFlows.clear()
    }

    override fun observeString(key: String): Flow<String?> = stringFlows.getOrPut(key) { MutableStateFlow(store[key] as? String) }

    override fun observeBoolean(
        key: String,
        defaultValue: Boolean,
    ): Flow<Boolean> =
        booleanFlows.getOrPut(key) {
            MutableStateFlow(store[key] as? Boolean ?: defaultValue)
        }

    override fun observeInt(
        key: String,
        defaultValue: Int,
    ): Flow<Int> =
        intFlows.getOrPut(key) {
            MutableStateFlow(store[key] as? Int ?: defaultValue)
        }

    override fun observeLong(
        key: String,
        defaultValue: Long,
    ): Flow<Long> =
        longFlows.getOrPut(key) {
            MutableStateFlow(store[key] as? Long ?: defaultValue)
        }

    override fun observeFloat(
        key: String,
        defaultValue: Float,
    ): Flow<Float> =
        floatFlows.getOrPut(key) {
            MutableStateFlow(store[key] as? Float ?: defaultValue)
        }
}
