package app.logdate.client.launch

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserUnlockAwaiterTest {
    @Test
    fun `locked startup does not access credential storage until a verified unlock`() =
        runBlocking {
            val platform = UnlockSignals()
            var initialized = false
            val startup =
                async(start = CoroutineStart.UNDISPATCHED) {
                    awaitUserUnlock(platform::isUnlocked, platform::register)
                    initialized = true
                }
            withTimeout(1_000) { platform.listening.await() }
            assertFalse(initialized)
            platform.notifyChanged()
            yield()
            assertFalse(initialized, "A signal alone must not authorize credential storage access")
            platform.unlocked = true
            platform.notifyChanged()
            withTimeout(1_000) { startup.await() }
            assertTrue(initialized)
            assertEquals(1, platform.closed)
        }

    @Test
    fun `already unlocked startup proceeds without a retained receiver`() =
        runBlocking {
            val platform = UnlockSignals().apply { unlocked = true }
            awaitUserUnlock(platform::isUnlocked, platform::register)
            assertEquals(0, platform.registered)
        }

    @Test
    fun `unlock between initial check and receiver registration cannot strand startup`() =
        runBlocking {
            val platform = UnlockSignals().apply { unlockDuringRegistration = true }
            withTimeout(1_000) { awaitUserUnlock(platform::isUnlocked, platform::register) }
            assertEquals(1, platform.closed)
        }

    @Test
    fun `duplicate unlock signals initialize once and unregister once`() =
        runBlocking {
            val platform = UnlockSignals()
            var initialized = 0
            val startup =
                async(start = CoroutineStart.UNDISPATCHED) {
                    awaitUserUnlock(platform::isUnlocked, platform::register)
                    initialized++
                }
            withTimeout(1_000) { platform.listening.await() }
            platform.unlocked = true
            repeat(3) { platform.notifyChanged() }
            withTimeout(1_000) { startup.await() }
            assertEquals(1, initialized)
            assertEquals(1, platform.closed)
        }

    @Test
    fun `canceling a locked startup releases its receiver without initialization`() =
        runBlocking {
            val platform = UnlockSignals()
            var initialized = false
            val startup =
                async(start = CoroutineStart.UNDISPATCHED) {
                    awaitUserUnlock(platform::isUnlocked, platform::register)
                    initialized = true
                }
            withTimeout(1_000) { platform.listening.await() }
            startup.cancel()
            startup.join()
            assertFalse(initialized)
            assertEquals(1, platform.closed)
        }

    private class UnlockSignals {
        val listening = CompletableDeferred<Unit>()
        var unlocked = false
        var unlockDuringRegistration = false
        var registered = 0
        var closed = 0
        private var listener: (() -> Unit)? = null

        fun isUnlocked(): Boolean = unlocked

        fun register(onChanged: () -> Unit): AutoCloseable {
            registered++
            listener = onChanged
            if (unlockDuringRegistration) unlocked = true
            listening.complete(Unit)
            return AutoCloseable {
                closed++
                listener = null
            }
        }

        fun notifyChanged() = listener?.invoke()
    }
}
