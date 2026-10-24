package app.logdate.client.e2e

import android.app.Activity
import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Looper
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.domain.restore.PreviewArchiveUseCase
import app.logdate.client.ensureLogDateApplicationStarted
import app.logdate.feature.core.restore.AndroidRestoreLauncher
import app.logdate.feature.core.restore.RestoreState
import app.logdate.feature.core.restore.UserDataRestoreViewModel
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The registry executes the same result contract as MainActivity without launching an external picker. */
@RunWith(AndroidJUnit4::class)
class ArchivePickerCancellationE2ETest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun cancellationDuringProviderIoAndLatePickerResults() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("archivePickerCancellationAcceptance") == "true")
        require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        require(context.packageName.endsWith(".debug"))
        ensureLogDateApplicationStarted(context.applicationContext as Application)
        cancelledProviderReadCannotPublish()
        lateResultCannotBecomeANewerSelection()
    }

    private fun cancelledProviderReadCannotPublish() {
        Fixture().use { fixture ->
            main {
                fixture.model.selectFile()
                fixture.registry.deliver(Activity.RESULT_OK, Uri.parse("content://synthetic.archive/blocked.zip"))
            }
            assertTrue(fixture.resolver.entered.await(3, TimeUnit.SECONDS), "The actual launcher must reach the provider boundary")
            assertFalse(fixture.resolver.calledOnMain.get(), "Provider access must not block ActivityResult delivery on Main")
            main { fixture.model.cancelRestore() }
            fixture.resolver.release.countDown()
            assertTrue(fixture.resolver.returned.await(3, TimeUnit.SECONDS))
            assertCancelledRemainsStable(fixture)
            assertEquals(1, fixture.resolver.calls.get())
        }
    }

    private fun lateResultCannotBecomeANewerSelection() {
        Fixture().use { fixture ->
            main {
                fixture.model.selectFile()
                fixture.model.cancelRestore()
                fixture.model.selectFile()
                assertEquals(1, fixture.registry.launches.size, "Only one system picker may be outstanding")
                fixture.registry.deliver(Activity.RESULT_OK, Uri.parse("content://synthetic.archive/stale.zip"))
                assertEquals(2, fixture.registry.launches.size, "Draining the old result launches the queued request")
                assertEquals(RestoreState.Selecting, fixture.model.restoreState.value)
                assertEquals(0, fixture.resolver.calls.get(), "A cancelled picker result must never touch its provider")
                fixture.registry.deliver(Activity.RESULT_CANCELED, null)
                assertEquals(RestoreState.Idle, fixture.model.restoreState.value)
                assertFalse(fixture.model.isSheetVisible.value)
            }
            assertEquals(0, fixture.resolver.calls.get())
        }
    }

    private fun assertCancelledRemainsStable(fixture: Fixture) {
        repeat(50) {
            main {
                assertEquals(RestoreState.Idle, fixture.model.restoreState.value)
                assertFalse(fixture.model.isSheetVisible.value)
            }
            Thread.sleep(10)
        }
    }

    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)

    private inner class Fixture : AutoCloseable {
        private val directory =
            File.createTempFile("archive-picker-acceptance-", "", context.cacheDir).apply {
                check(delete() && mkdir())
            }
        val resolver = BlockingPermissionResolver(context)
        private val wrapped =
            object : ContextWrapper(context) {
                override fun getContentResolver(): ContentResolver = resolver

                override fun getFilesDir(): File = directory.resolve("files").apply { mkdirs() }

                override fun getCacheDir(): File = directory.resolve("cache").apply { mkdirs() }
            }
        val launcher = AndroidRestoreLauncher(wrapped)
        val registry = PickerRegistry()
        lateinit var model: UserDataRestoreViewModel

        init {
            main {
                val picker =
                    registry.register("archive-restore", ActivityResultContracts.StartActivityForResult()) { result ->
                        launcher.onRestoreSourceSelected(if (result.resultCode == Activity.RESULT_OK) result.data?.data else null)
                    }
                launcher.setupActivityResultLauncher(picker)
                model = UserDataRestoreViewModel(launcher, PreviewArchiveUseCase())
            }
        }

        override fun close() {
            resolver.release.countDown()
            main { launcher.cancelRestore() }
            directory.deleteRecursively()
        }
    }

    private class BlockingPermissionResolver(
        context: Context,
    ) : ContentResolver(context) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val calls = AtomicInteger()
        val calledOnMain = AtomicBoolean()

        override fun takePersistableUriPermission(
            uri: Uri,
            modeFlags: Int,
        ) {
            calls.incrementAndGet()
            calledOnMain.set(Looper.myLooper() == Looper.getMainLooper())
            entered.countDown()
            try {
                check(release.await(5, TimeUnit.SECONDS)) { "Synthetic provider was not released" }
            } finally {
                returned.countDown()
            }
            throw IOException("Synthetic provider permission failure after blocked I/O")
        }
    }

    private class PickerRegistry : ActivityResultRegistry() {
        val launches = mutableListOf<Int>()

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            launches += requestCode
        }

        fun deliver(
            resultCode: Int,
            uri: Uri?,
        ) {
            check(launches.isNotEmpty())
            assertTrue(dispatchResult(launches.last(), resultCode, Intent().setData(uri)))
        }
    }
}
