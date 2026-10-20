package app.logdate.client.ambient

import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.recommendation.AmbientPromptCandidate
import app.logdate.client.domain.recommendation.AmbientPromptFamily
import app.logdate.client.domain.recommendation.AmbientPromptHistoryRepository
import app.logdate.client.domain.recommendation.AmbientPromptPayload
import app.logdate.client.domain.recommendation.DefaultAmbientPromptHistoryRepository
import app.logdate.client.domain.recommendation.GenerateAmbientPromptCandidatesUseCase
import app.logdate.client.domain.reminders.PhoneReminderDelivery
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class AmbientPromptDeliveryRaceTest {
    private val context = RuntimeEnvironment.getApplication()
    private val candidate =
        AmbientPromptCandidate(AmbientPromptFamily.DRAFT_RESCUE, 1, "retained-draft", AmbientPromptPayload.DraftRescue(Uuid.random()))
    private val values = mutableMapOf<String, String>()
    private val storage =
        mockk<KeyValueStorage> {
            coEvery { getStringOrThrow(any()) } answers { values[firstArg()] }
            coEvery { putStringOrThrow(any(), any()) } answers { values[firstArg()] = secondArg() }
        }
    private val retainedHistory = DefaultAmbientPromptHistoryRepository(storage)
    private val coordinator =
        mockk<AmbientPromptNotificationCoordinator> {
            every { post(any()) } returns true
        }

    @Test fun concurrentImmediateAndPeriodicWorkersPostOnlyOnce() =
        runTest {
            val history =
                object : AmbientPromptHistoryRepository by retainedHistory {
                    override suspend fun canShow(candidate: AmbientPromptCandidate): Boolean {
                        val allowed = retainedHistory.canShow(candidate)
                        yield() // Both workers reach the same eligibility decision without a delivery lock.
                        return allowed
                    }
                }
            val immediate = async { worker(history).doWork() }
            val periodic = async { worker(history).doWork() }
            immediate.await()
            periodic.await()
            verify(exactly = 1) { coordinator.post(candidate) }
            assertFalse(retainedHistory.canShow(candidate))
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun historyWriteRetryDoesNotPostTheSameNotificationAgain() =
        runTest {
            var failed = false
            coEvery { storage.putStringOrThrow(any(), any()) } answers {
                if (!failed) {
                    failed = true
                    error("Synthetic disk full after notification post")
                }
                values[firstArg()] = secondArg()
            }
            assertEquals(Result.retry(), worker(retainedHistory).doWork())
            assertEquals(Result.success(), worker(retainedHistory).doWork())
            verify(exactly = 1) { coordinator.post(candidate) }
            assertFalse(retainedHistory.canShow(candidate))
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun canceledWaitingWorkerCannotPostAfterTheCurrentDelivery() =
        runTest {
            val recording = CompletableDeferred<Unit>()
            val finishRecording = CompletableDeferred<Unit>()
            val history =
                object : AmbientPromptHistoryRepository by retainedHistory {
                    override suspend fun recordShown(
                        candidate: AmbientPromptCandidate,
                        shownAt: kotlin.time.Instant,
                    ) {
                        recording.complete(Unit)
                        finishRecording.await()
                        retainedHistory.recordShown(candidate, shownAt)
                    }
                }
            val first = async { worker(history).doWork() }
            recording.await()
            val waiting = async { worker(retainedHistory).doWork() }
            runCurrent()
            waiting.cancel()
            finishRecording.complete(Unit)
            first.await()
            waiting.join()
            verify(exactly = 1) { coordinator.post(candidate) }
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun cancellationAfterPostingStillPersistsSuppressionBeforeNextWorker() =
        runTest {
            val recording = CompletableDeferred<Unit>()
            val finishRecording = CompletableDeferred<Unit>()
            val history =
                object : AmbientPromptHistoryRepository by retainedHistory {
                    override suspend fun recordShown(
                        candidate: AmbientPromptCandidate,
                        shownAt: kotlin.time.Instant,
                    ) {
                        recording.complete(Unit)
                        finishRecording.await()
                        retainedHistory.recordShown(candidate, shownAt)
                    }
                }
            val first = async { worker(history).doWork() }
            recording.await()
            first.cancel()
            val replacement = async { worker(retainedHistory).doWork() }
            runCurrent()
            finishRecording.complete(Unit)
            first.join()
            replacement.await()
            verify(exactly = 1) { coordinator.post(candidate) }
            assertFalse(retainedHistory.canShow(candidate))
        }

    private fun worker(history: AmbientPromptHistoryRepository): AmbientPromptWorker {
        val flags =
            object : FeatureFlagStore {
                override fun observe(flag: FeatureFlag) = flowOf(false)

                override suspend fun setEnabled(
                    flag: FeatureFlag,
                    enabled: Boolean,
                ) = Unit
            }
        val parameters =
            mockk<WorkerParameters>(relaxed = true) {
                every { inputData } returns Data.EMPTY
            }
        val generate =
            mockk<GenerateAmbientPromptCandidatesUseCase> {
                coEvery { this@mockk.invoke(any()) } returns listOf(candidate)
            }
        return AmbientPromptWorker(context, parameters, flags, mockk<PhoneReminderDelivery>(relaxed = true), generate, history, coordinator)
    }
}
