package app.logdate.wear.launch

import android.content.Context
import app.logdate.wear.LogDateWearApplication
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WearStartupAccessTest {
    @Test
    fun playbackPreparationWaitsForColdStorageWithinItsOwnBudget() =
        runTest {
            val storage = CompletableDeferred<Unit>()
            val startup = WearStartup(backgroundScope, { storage.await() }, {}, {}, {})
            val context = mockk<Context>()
            val application = mockk<LogDateWearApplication>()
            every { context.applicationContext } returns application
            every { application.startup } returns startup
            startup.start()
            val remote = async { context.awaitWearStartupWithin(8_000) }
            runCurrent()
            advanceTimeBy(2_000)
            runCurrent()
            assertFalse(remote.isCompleted)
            storage.complete(Unit)
            runCurrent()
            assertTrue(remote.await())
        }

    @Test
    fun expiredRemoteReadinessCannotExecuteAfterSlowStorageEventuallyOpens() =
        runTest {
            val storage = CompletableDeferred<Unit>()
            val startup = WearStartup(backgroundScope, { storage.await() }, {}, {}, {})
            val context = mockk<Context>()
            val application = mockk<LogDateWearApplication>()
            every { context.applicationContext } returns application
            every { application.startup } returns startup
            startup.start()
            val remote = async { context.resolveWearServiceKoin() }
            runCurrent()
            assertFalse(context.isWearStartupReady())
            assertFalse(remote.isCompleted)
            advanceTimeBy(1_001)
            runCurrent()
            assertTrue(remote.isCompleted)
            assertNull(remote.await())
            storage.complete(Unit)
            runCurrent()
            assertTrue(context.isWearStartupReady())
            assertNull(remote.await())
        }
}
