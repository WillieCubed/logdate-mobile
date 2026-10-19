package app.logdate.client.launch

import app.logdate.client.database.encryption.DatabaseKeyRecoveryRequiredException
import app.logdate.client.restore.NativeMediaQuarantinePendingException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ApplicationStartupTest {
    @Test
    fun `database storage exhaustion gives a repair action before journal initialization`() {
        val failure = IllegalStateException("Database initialization failed", IllegalStateException("no space left on device"))

        assertEquals(ApplicationStartupFailureMessage.STORAGE_FULL, applicationStartupFailureMessage(failure))
    }

    @Test
    fun `missing database key reaches recovery guidance through dependency wrappers`() {
        val failure = IllegalStateException("Storage dependency failed", DatabaseKeyRecoveryRequiredException())

        assertNotEquals(ApplicationStartupFailureMessage.GENERIC, applicationStartupFailureMessage(failure))
    }

    @Test
    fun `native media recovery offers a storage-specific retry when the database is full`() {
        val failure = NativeMediaQuarantinePendingException(IllegalStateException("database or disk is full"))

        assertEquals(ApplicationStartupFailureMessage.STORAGE_FULL, applicationStartupFailureMessage(failure))
    }

    @Test
    fun `native media recovery keeps its failure message actionable without a storage error`() {
        val failure = NativeMediaQuarantinePendingException()

        assertEquals(ApplicationStartupFailureMessage.NATIVE_MEDIA_PENDING, applicationStartupFailureMessage(failure))
    }

    @Test
    fun `native media recovery recognizes a storage failure suppressed by a later enqueue failure`() {
        val failure = NativeMediaQuarantinePendingException(IllegalStateException("WorkManager enqueue failed"))
        failure.addSuppressed(IllegalStateException("database or disk is full"))

        assertEquals(ApplicationStartupFailureMessage.STORAGE_FULL, applicationStartupFailureMessage(failure))
    }

    @Test
    fun `callers cannot access the graph until initialization completes`() =
        runTest {
            val opened = CompletableDeferred<Unit>()
            val startup = ApplicationStartup(backgroundScope) { opened.await() }
            startup.start()
            val waiter = async { startup.awaitReady() }
            runCurrent()
            assertIs<ApplicationStartupState.Loading>(startup.state.value)
            assertFalse(waiter.isCompleted)
            opened.complete(Unit)
            runCurrent()
            assertTrue(waiter.await())
            assertIs<ApplicationStartupState.Ready>(startup.state.value)
        }

    @Test
    fun `failed initialization is visible and only explicit retry reruns it`() =
        runTest {
            var attempts = 0
            val startup =
                ApplicationStartup(backgroundScope) {
                    attempts++
                    check(attempts > 1) { "Storage unavailable" }
                }
            startup.start()
            runCurrent()
            assertIs<ApplicationStartupState.Failed>(startup.state.value)
            assertFalse(startup.awaitReady())
            assertEquals(1, attempts)
            startup.start()
            runCurrent()
            assertTrue(startup.awaitReady())
            assertEquals(2, attempts)
        }

    @Test
    fun `retry resolves the original durable binding without caching a failed replacement`() =
        runTest {
            val durableStore = DurableStore("retained journal")
            var attempts = 0
            val graph =
                koinApplication {
                    modules(
                        module {
                            single {
                                attempts++
                                check(attempts > 1) { "Encrypted store unavailable" }
                                durableStore
                            }
                        },
                    )
                }
            try {
                var resolved: DurableStore? = null
                val startup = ApplicationStartup(backgroundScope) { resolved = graph.koin.get() }
                startup.start()
                runCurrent()
                assertFalse(startup.awaitReady())
                assertEquals(null, resolved)
                assertEquals("retained journal", durableStore.content)
                startup.start()
                runCurrent()
                assertTrue(startup.awaitReady())
                assertSame(durableStore, resolved)
                assertEquals(2, attempts)
            } finally {
                graph.close()
            }
        }

    private data class DurableStore(
        val content: String,
    )

    @Test
    fun `repeated start requests share one initialization and ready is final`() =
        runTest {
            val opened = CompletableDeferred<Unit>()
            var attempts = 0
            val startup =
                ApplicationStartup(backgroundScope) {
                    attempts++
                    opened.await()
                }
            repeat(4) { startup.start() }
            runCurrent()
            assertEquals(1, attempts)
            opened.complete(Unit)
            runCurrent()
            startup.start()
            runCurrent()
            assertEquals(1, attempts)
        }
}
