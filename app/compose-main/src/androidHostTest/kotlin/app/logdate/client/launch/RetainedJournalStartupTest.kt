package app.logdate.client.launch

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RetainedJournalStartupTest {
    @Test
    fun `retention waits for failed startup and gates all new database initialization`() =
        runTest {
            val retained = CompletableDeferred<Unit>()
            var attempts = 0
            var released = false
            var deviceWork = 0
            val startup =
                ApplicationStartup(backgroundScope, startDeviceWork = { deviceWork++ }) {
                    attempts++
                    if (attempts == 1) {
                        try {
                            throw IllegalStateException("Locked journal")
                        } finally {
                            released = true
                        }
                    }
                    assertTrue(retained.isCompleted)
                }
            startup.start()
            runCurrent()
            assertIs<ApplicationStartupState.Failed>(startup.state.value)
            startup.recoverRetainedJournal {
                assertTrue(released)
                retained.await()
            }
            runCurrent()
            assertEquals(1, attempts)
            assertEquals(0, deviceWork)
            assertIs<ApplicationStartupState.Loading>(startup.state.value)
            retained.complete(Unit)
            runCurrent()
            assertIs<ApplicationStartupState.Ready>(startup.state.value)
            assertEquals(2, attempts)
            assertEquals(1, deviceWork)
        }

    @Test
    fun `retention IO failure does not open an empty database or report success`() =
        runTest {
            var attempts = 0
            val startup =
                ApplicationStartup(backgroundScope) {
                    attempts++
                    error("Locked journal")
                }
            startup.start()
            runCurrent()
            startup.recoverRetainedJournal { throw IOException("Controlled full disk") }
            runCurrent()
            assertEquals(1, attempts)
            assertIs<IOException>(assertIs<ApplicationStartupState.Failed>(startup.state.value).cause)
        }

    @Test
    fun `a recovery action cannot replace a ready journal`() =
        runTest {
            var retained = 0
            val startup = ApplicationStartup(backgroundScope) {}
            startup.start()
            runCurrent()
            startup.recoverRetainedJournal { retained++ }
            runCurrent()
            assertEquals(0, retained)
            assertIs<ApplicationStartupState.Ready>(startup.state.value)
        }
}
