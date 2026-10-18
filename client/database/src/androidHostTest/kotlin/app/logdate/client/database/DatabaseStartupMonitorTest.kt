package app.logdate.client.database

import app.logdate.client.device.restore.NativeRestoreDeliveryGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DatabaseStartupMonitorTest {
    @Test
    fun `database is not ready before its key and retained contents have been checked`() {
        assertEquals(DatabaseStartupState.Initializing, DatabaseStartupMonitor().state.value)
    }

    @Test
    fun `database is not opened while native restore delivery is incomplete`() =
        runTest {
            var opened = false

            assertFailsWith<IllegalStateException> {
                openDatabaseAfterNativeRestoreCheck(TestDeliveryGate(), restoreInProgress = { true }) {
                    opened = true
                }
            }

            assertFalse(opened)
        }

    @Test
    fun `database opens after native restore delivery is complete`() =
        runTest {
            var opened = false

            val value =
                openDatabaseAfterNativeRestoreCheck(TestDeliveryGate(), restoreInProgress = { false }) {
                    opened = true
                    "database"
                }

            assertTrue(opened)
            assertEquals("database", value)
        }

    @Test
    fun `startup waits behind an active native restore before checking its marker`() =
        runTest {
            val gate = TestDeliveryGate()
            val restoreStarted = CompletableDeferred<Unit>()
            val releaseRestore = CompletableDeferred<Unit>()
            val restore =
                async {
                    gate.withLock {
                        restoreStarted.complete(Unit)
                        releaseRestore.await()
                    }
                }
            restoreStarted.await()
            var opened = false
            val startup =
                async {
                    openDatabaseAfterNativeRestoreCheck(gate, restoreInProgress = { false }) {
                        opened = true
                        "database"
                    }
                }
            runCurrent()

            assertFalse(opened)
            releaseRestore.complete(Unit)
            restore.await()

            assertEquals("database", startup.await())
            assertTrue(opened)
        }

    @Test
    fun `native transfer interruption receives a specific recovery reason`() {
        val monitor = DatabaseStartupMonitor()

        monitor.markRecoveryRequired(NativeRestoreInProgressException())

        val state = assertIs<DatabaseStartupState.RecoveryRequired>(monitor.state.value)
        assertEquals("Android device transfer is still finishing.", state.reason)
    }

    @Test
    fun `database opener falls back to the next retained key and returns the accepted key`() {
        val primary = ByteArray(32) { 7 }
        val portable = ByteArray(32) { 8 }
        var attempts = 0

        val opened =
            openDatabaseWithPassphraseCandidates(listOf(primary, portable)) { candidate ->
                attempts++
                if (candidate.contentEquals(portable)) "opened" else error("Wrong key")
            }

        assertEquals(2, attempts)
        assertEquals("opened", opened.database)
        assertContentEquals(portable, opened.passphrase)
    }
}

private class TestDeliveryGate : NativeRestoreDeliveryGate {
    private val mutex = Mutex()

    override suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
