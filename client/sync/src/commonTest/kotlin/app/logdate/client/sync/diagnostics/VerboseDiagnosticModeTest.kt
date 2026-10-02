package app.logdate.client.sync.diagnostics

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerboseDiagnosticModeTest {
    private class Storage : DiagnosticStorage {
        var value: String? = null

        override suspend fun read() = value

        override suspend fun write(value: String) {
            this.value = value
        }

        override suspend fun clear() {
            value = null
        }
    }

    @Test
    fun `mode expires after thirty minutes across restart`() =
        runTest {
            val storage = Storage()
            var now = 1_000L
            val mode = VerboseDiagnosticMode(storage, { now })

            assertEquals(now + 30 * 60 * 1000L, mode.enable())
            assertTrue(VerboseDiagnosticMode(storage, { now }).isEnabled())
            now += 30 * 60 * 1000L
            assertFalse(mode.isEnabled())
            assertEquals(0L, mode.remainingMillis())
            assertEquals(null, storage.value)
        }

    @Test
    fun `disabling mode clears only its private setting`() =
        runTest {
            val storage = Storage()
            val mode = VerboseDiagnosticMode(storage, { 1_000L })
            mode.enable()
            mode.disable()
            assertFalse(mode.isEnabled())
            assertEquals(null, storage.value)
        }

    @Test
    fun `future persisted deadline and clock rollback cannot extend a verbose window`() =
        runTest {
            val storage = Storage().apply { value = Long.MAX_VALUE.toString() }
            var now = 1_000L
            val mode = VerboseDiagnosticMode(storage, { now })
            assertFalse(mode.isEnabled())
            assertEquals(null, storage.value)

            mode.enable()
            now -= 2 * 60 * 60 * 1000L
            assertFalse(mode.isEnabled())
            assertEquals(null, storage.value)
        }

    @Test
    fun `backward clock adjustment cannot outlive original elapsed budget`() =
        runTest {
            val storage = Storage()
            var wall = 1_000L
            var elapsed = 0L
            val mode = VerboseDiagnosticMode(storage, { wall }, { elapsed })
            mode.enable()

            wall += 10 * 60 * 1000L
            elapsed += 10 * 60 * 1000L
            assertEquals(20 * 60 * 1000L, mode.remainingMillis())

            wall -= 5 * 60 * 1000L
            elapsed += 20 * 60 * 1000L
            assertFalse(mode.isEnabled())
            assertEquals(null, storage.value)
        }
}
