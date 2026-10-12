package app.logdate.reminders.e2e

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import app.logdate.client.ambient.AmbientPromptWorker
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.recommendation.AmbientCaptureNudgeStyle
import app.logdate.client.domain.recommendation.AmbientPromptCandidate
import app.logdate.client.domain.recommendation.AmbientPromptFamily
import app.logdate.client.domain.recommendation.AmbientPromptHistoryRepository
import app.logdate.client.domain.recommendation.AmbientPromptPayload
import app.logdate.client.domain.recommendation.GenerateAmbientPromptCandidatesUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class PhoneReminderModeTransitionRuntimeTest {
    @Test fun legacyDeliveryStillPostsWhenRolloutRemainsOff(): Unit = runBlocking { exercise(false) }

    @Test fun enablingRolloutWhileLegacyHistoryIsReadPreventsLegacyDelivery(): Unit = runBlocking { exercise(true) }

    private suspend fun exercise(enableDuringRead: Boolean) =
        coroutineScope {
            val context = ApplicationProvider.getApplicationContext<Context>()
            InstrumentationRegistry
                .getInstrumentation()
                .uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            val koin = KoinJavaComponent.getKoin()
            val flags = koin.get<FeatureFlagStore>()
            val previous = flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)
            val manager = context.getSystemService(NotificationManager::class.java)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var recorded = false
            val candidate =
                AmbientPromptCandidate(
                    AmbientPromptFamily.CAPTURE_NUDGES,
                    60,
                    "mode-transition",
                    AmbientPromptPayload.CaptureNudge(AmbientCaptureNudgeStyle.MORNING),
                )
            val generate = mockk<GenerateAmbientPromptCandidatesUseCase>()
            coEvery { generate(any()) } returns listOf(candidate)
            val history =
                object : AmbientPromptHistoryRepository {
                    override suspend fun canShow(candidate: AmbientPromptCandidate): Boolean {
                        entered.complete(Unit)
                        release.await()
                        return true
                    }

                    override suspend fun recordShown(
                        candidate: AmbientPromptCandidate,
                        shownAt: Instant,
                    ) {
                        recorded = true
                    }
                }
            try {
                flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, false)
                val worker =
                    TestListenableWorkerBuilder<AmbientPromptWorker>(context)
                        .setWorkerFactory(
                            object : WorkerFactory() {
                                override fun createWorker(
                                    appContext: Context,
                                    workerClassName: String,
                                    workerParameters: WorkerParameters,
                                ): ListenableWorker {
                                    check(workerClassName == AmbientPromptWorker::class.java.name)
                                    return AmbientPromptWorker(
                                        appContext,
                                        workerParameters,
                                        flags,
                                        koin.get(),
                                        generate,
                                        history,
                                        koin.get(),
                                    )
                                }
                            },
                        ).build()
                val evaluation = async(Dispatchers.Default) { worker.doWork() }
                withTimeout(5_000) { entered.await() }
                if (enableDuringRead) flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, true)
                release.complete(Unit)
                assertEquals(ListenableWorker.Result.success(), withTimeout(5_000) { evaluation.await() })
                assertEquals(!enableDuringRead, recorded)
            } finally {
                release.complete(Unit)
                flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, previous)
                manager.cancel(4001)
            }
        }
}
