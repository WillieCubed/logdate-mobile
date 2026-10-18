package app.logdate.client.restore

import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class NativeMediaQuarantineSchedulerTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var workManager: WorkManager

    @Before
    fun initializeWorkManager() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration
                .Builder()
                .setWorkerFactory(
                    object : WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ): ListenableWorker =
                            object : ListenableWorker(appContext, workerParameters) {
                                private var pending: CallbackToFutureAdapter.Completer<Result>? = null

                                override fun startWork(): ListenableFuture<Result> =
                                    CallbackToFutureAdapter.getFuture { completer ->
                                        pending = completer
                                        "Hold media recovery during scheduler test"
                                    }

                                override fun onStopped() {
                                    pending?.setCancelled()
                                }
                            }
                    },
                ).build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    @After
    fun closeWorkManager() {
        workManager.cancelAllWork().result.get(10, TimeUnit.SECONDS)
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun `repeated startup schedules one work request per delivery`() =
        runBlocking {
            val deliveryId = "delivery-1"
            NativeMediaQuarantineScheduler.enqueue(context, deliveryId)
            NativeMediaQuarantineScheduler.enqueue(context, deliveryId)

            val workInfos =
                workManager
                    .getWorkInfosForUniqueWork(NativeMediaQuarantineScheduler.workName(deliveryId))
                    .get(10, TimeUnit.SECONDS)

            assertEquals(1, workInfos.size)
        }
}
