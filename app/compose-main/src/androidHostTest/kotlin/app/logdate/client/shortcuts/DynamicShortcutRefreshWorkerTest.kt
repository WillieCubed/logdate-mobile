package app.logdate.client.shortcuts

import androidx.core.content.pm.ShortcutManagerCompat
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import app.logdate.client.feature.widgets.shortcuts.DynamicShortcutPublisher
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class DynamicShortcutRefreshWorkerTest {
    private val publisher = mockk<DynamicShortcutPublisher>()
    private val applier = AndroidDynamicShortcutApplier(RuntimeEnvironment.getApplication(), mockk())

    @Before fun setup() {
        startKoin {
            modules(
                module {
                    single { publisher }
                    single { applier }
                },
            )
        }
    }

    @After fun cleanup() {
        stopKoin()
        unmockkStatic(ShortcutManagerCompat::class)
    }

    @Test fun rateLimitedPublicationRetriesUntilAndroidAcceptsIt() =
        runBlocking {
            coEvery { publisher.computeShortcuts() } returns emptyList()
            mockkStatic(ShortcutManagerCompat::class)
            every { ShortcutManagerCompat.getDynamicShortcuts(any()) } returns emptyList()
            every { ShortcutManagerCompat.getMaxShortcutCountPerActivity(any()) } returns 5
            every { ShortcutManagerCompat.getShortcuts(any(), any()) } returns emptyList()
            every { ShortcutManagerCompat.setDynamicShortcuts(any(), any()) } returnsMany listOf(false, true)
            assertEquals(Result.retry(), worker().doWork())
            assertEquals(Result.success(), worker().doWork())
        }

    @Test fun canceledRefreshPropagatesCancellation() =
        runBlocking<Unit> {
            coEvery { publisher.computeShortcuts() } throws CancellationException("Refresh superseded")
            assertFailsWith<CancellationException> { worker().doWork() }
        }

    @Test fun unavailableJournalRetriesRefresh() =
        runBlocking {
            coEvery { publisher.computeShortcuts() } throws IllegalStateException("Journal still recovering")
            assertEquals(Result.retry(), worker().doWork())
        }

    private fun worker() = DynamicShortcutRefreshWorker(RuntimeEnvironment.getApplication(), mockk<WorkerParameters>(relaxed = true))
}
