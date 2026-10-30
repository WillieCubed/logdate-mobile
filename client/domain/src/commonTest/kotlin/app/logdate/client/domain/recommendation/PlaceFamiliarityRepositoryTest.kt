package app.logdate.client.domain.recommendation

import app.logdate.client.datastore.KeyValueStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class PlaceFamiliarityRepositoryTest {
    /** Suspends between a read and the following write, as real storage does. */
    private class YieldingStorage(
        private val delegate: FakeKeyValueStorage = FakeKeyValueStorage(),
    ) : KeyValueStorage by delegate {
        override suspend fun getStringOrThrow(key: String): String? {
            yield()
            return delegate.getString(key)
        }
    }

    private val start = Instant.fromEpochSeconds(1_700_000_000)

    @Test
    fun `visits recorded at the same time are all counted`() =
        runTest {
            val repository = DefaultPlaceFamiliarityRepository(YieldingStorage())
            var day = 0

            repeat(2) {
                launch {
                    repeat(25) { repository.recordVisit("cafe", "Cafe", start + (day++).days) }
                }
            }
            testScheduler.advanceUntilIdle()

            assertEquals(50, repository.get("cafe")?.visitCount)
        }
}
