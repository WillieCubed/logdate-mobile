package app.logdate.client.device.restore

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NativeRestoreAgentCleanupTest {
    @Test
    fun `unreadable workspace detection still finishes sessions and releases delivery`() =
        runBlocking {
            val calls = mutableListOf<String>()
            AndroidNativeRestoreDeliveryGate.beginRestoreCompletion()
            finishNativeRestoreAgent(
                schedule = { throw IOException("Unreadable retained workspace") },
                finishNativeSession = { calls += "native" },
                finishIncomingSession = { calls += "incoming" },
                destroy = { calls += "destroy" },
            )
            assertEquals(listOf("native", "incoming", "destroy"), calls)
            withTimeout(1000) { AndroidNativeRestoreDeliveryGate.withLock { calls += "released" } }
            assertEquals("released", calls.last())
        }

    @Test
    fun `session cleanup failure cannot skip incoming cleanup gate release or destruction`() =
        runBlocking {
            val calls = mutableListOf<String>()
            AndroidNativeRestoreDeliveryGate.beginRestoreCompletion()
            assertFailsWith<IOException> {
                finishNativeRestoreAgent(
                    schedule = {},
                    finishNativeSession = { throw IOException("Session cleanup failed") },
                    finishIncomingSession = { calls += "incoming" },
                    destroy = { calls += "destroy" },
                )
            }
            assertEquals(listOf("incoming", "destroy"), calls)
            withTimeout(1000) { AndroidNativeRestoreDeliveryGate.withLock {} }
        }
}
