package app.logdate.client.e2e

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RecoveryFixtureCleanupRuntimeTest {
    @Test fun failedRestorationDoesNotSkipRemainingSettings() =
        runBlocking {
            val visited = mutableListOf<Int>()
            val first = IllegalStateException("First fixture setting failed")
            val second = IllegalArgumentException("Another fixture setting failed")
            val error =
                runCatching {
                    restoreFixtureState(
                        {
                            visited += 1
                            throw first
                        },
                        { visited += 2 },
                        {
                            visited += 3
                            throw second
                        },
                    )
                }.exceptionOrNull()
            assertEquals(listOf(1, 2, 3), visited)
            assertSame(first, error)
            assertTrue(first.suppressed.any { it === second })
        }

    @Test fun repeatedStorageFailureDoesNotSuppressItselfOrSkipCleanup() =
        runBlocking {
            val failure = IllegalStateException("Shared storage failure")
            var restored = false
            val error =
                runCatching {
                    restoreFixtureState({ throw failure }, { throw failure }, { restored = true })
                }.exceptionOrNull()
            assertSame(failure, error)
            assertTrue(restored)
        }

    @Test fun cancellationStillRestoresFixtureState() =
        runBlocking {
            var restored = false
            val job =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        restoreFixtureState({ restored = true })
                    }
                }
            job.cancelAndJoin()
            assertTrue(restored)
        }
}
