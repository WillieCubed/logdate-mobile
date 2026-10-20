package app.logdate.client.sync

import androidx.work.WorkInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MediaReadinessHandoffTest {
    @Test
    fun `policy lift waits for a running writer then replaces its retry`() =
        runTest {
            val work = MutableStateFlow(listOf(request("original", WorkInfo.State.RUNNING)))
            val handoff = backgroundScope.async { awaitMediaReadinessHandoff(work) }
            runCurrent()
            assertFalse(handoff.isCompleted, "A running worker cannot consume the recovery wake")
            work.value = listOf(request("original", WorkInfo.State.ENQUEUED))
            runCurrent()
            assertEquals(work.value, assertIs<MediaReadinessResult.Replace>(handoff.await()).snapshot)
        }

    @Test
    fun `successful observed work completes the wake without another request`() =
        runTest {
            val work = MutableStateFlow(listOf(request("original", WorkInfo.State.RUNNING)))
            val handoff = backgroundScope.async { awaitMediaReadinessHandoff(work) }
            runCurrent()
            work.value = listOf(request("original", WorkInfo.State.SUCCEEDED))
            runCurrent()
            assertEquals(MediaReadinessResult.Completed, handoff.await())
        }

    @Test
    fun `historical success does not discard a startup recovery wake`() =
        runTest {
            val snapshot = listOf(request("historical", WorkInfo.State.SUCCEEDED))
            assertEquals(MediaReadinessResult.Replace(snapshot), awaitMediaReadinessHandoff(MutableStateFlow(snapshot)))
        }

    @Test
    fun `successful partial or legacy work cannot acknowledge full media recovery`() =
        runTest {
            val work = MutableStateFlow(listOf(request("partial", WorkInfo.State.RUNNING, recovered = false)))
            val handoff = backgroundScope.async { awaitMediaReadinessHandoff(work) }
            runCurrent()
            work.value = listOf(request("partial", WorkInfo.State.SUCCEEDED, recovered = false))
            runCurrent()
            assertIs<MediaReadinessResult.Replace>(handoff.await())
        }

    @Test
    fun `consented work may finish without a running emission`() =
        runTest {
            val work = MutableStateFlow(listOf(request("consented", WorkInfo.State.ENQUEUED, consent = true)))
            val handoff = backgroundScope.async { awaitMediaReadinessHandoff(work) }
            runCurrent()
            work.value = listOf(request("consented", WorkInfo.State.SUCCEEDED, consent = true))
            runCurrent()
            assertEquals(MediaReadinessResult.Completed, handoff.await())
        }

    @Test
    fun `failed cancelled or pruned consent remains retryable without copying its grant`() =
        runTest {
            for (terminal in listOf(WorkInfo.State.FAILED, WorkInfo.State.CANCELLED, null)) {
                val work = MutableStateFlow(listOf(request("consented", WorkInfo.State.BLOCKED, consent = true)))
                val handoff = backgroundScope.async { awaitMediaReadinessHandoff(work) }
                runCurrent()
                work.value = terminal?.let { listOf(request("consented", it, consent = true)) }.orEmpty()
                runCurrent()
                assertEquals(work.value, assertIs<MediaReadinessResult.Replace>(handoff.await()).snapshot)
            }
        }

    @Test
    fun `startup with a queued retry is immediately replaceable`() =
        runTest {
            val snapshot = listOf(request("retained", WorkInfo.State.ENQUEUED))
            assertEquals(MediaReadinessResult.Replace(snapshot), awaitMediaReadinessHandoff(MutableStateFlow(snapshot)))
        }

    @Test
    fun `unobserved or pruned completion cannot claim recovered media`() =
        runTest {
            val work = MutableStateFlow(listOf(request("original", WorkInfo.State.RUNNING)))
            val handoff = backgroundScope.async { awaitMediaReadinessHandoff(work) }
            runCurrent()
            work.value = listOf(request("other", WorkInfo.State.SUCCEEDED))
            runCurrent()
            assertEquals(work.value, assertIs<MediaReadinessResult.Replace>(handoff.await()).snapshot)
        }

    @Test
    fun `queued mobile data consent remains owned by its original work`() =
        runTest {
            val work = MutableStateFlow(listOf(request("consented", WorkInfo.State.ENQUEUED, consent = true)))
            val handoff = backgroundScope.async { awaitMediaReadinessHandoff(work) }
            runCurrent()
            assertFalse(handoff.isCompleted, "Do not replace a request or copy consent from its generic tag")
            work.value = listOf(request("consented", WorkInfo.State.RUNNING, consent = true))
            runCurrent()
            assertFalse(handoff.isCompleted)
            work.value = listOf(request("consented", WorkInfo.State.SUCCEEDED, consent = true))
            runCurrent()
            assertEquals(MediaReadinessResult.Completed, handoff.await())
        }

    @Test
    fun `a pending successor prevents premature completion`() =
        runTest {
            val work = MutableStateFlow(listOf(request("original", WorkInfo.State.RUNNING)))
            val handoff = backgroundScope.async { awaitMediaReadinessHandoff(work) }
            runCurrent()
            work.value = listOf(request("original", WorkInfo.State.SUCCEEDED), request("successor", WorkInfo.State.ENQUEUED))
            runCurrent()
            assertIs<MediaReadinessResult.Replace>(handoff.await())
        }

    @Test
    fun `query failure propagates without acknowledgement`() =
        runTest {
            val failure = IOException("Work storage unavailable")
            val thrown = runCatching { awaitMediaReadinessHandoff(flow { throw failure }) }.exceptionOrNull()
            assertTrue(thrown === failure)
        }

    @Test
    fun `account change cancels a waiting handoff without scheduling`() =
        runTest {
            var returned = false
            val work = MutableStateFlow(listOf(request("original", WorkInfo.State.RUNNING)))
            val handoff =
                backgroundScope.async {
                    awaitMediaReadinessHandoff(work)
                    returned = true
                }
            runCurrent()
            handoff.cancelAndJoin()
            work.value = listOf(request("original", WorkInfo.State.ENQUEUED))
            runCurrent()
            assertFalse(returned)
        }

    private fun request(
        id: String,
        state: WorkInfo.State,
        consent: Boolean = false,
        recovered: Boolean = true,
    ) = MediaReadinessWork(id, state, consent, recovered)
}

@OptIn(ExperimentalCoroutinesApi::class)
class MediaReadinessResumptionTest {
    @Test
    fun `a retained retry is acknowledged only after replacement persists`() =
        runTest {
            val snapshot = listOf(MediaReadinessWork("retained", WorkInfo.State.ENQUEUED))
            val persisted = mutableListOf<List<MediaReadinessWork>>()
            resumeMediaReadiness(MutableStateFlow(snapshot), { true }) { expected ->
                persisted += expected
                true
            }
            assertEquals(listOf(snapshot), persisted)
        }

    @Test
    fun `a concurrent request change is reexamined before acknowledgement`() =
        runTest {
            val original = listOf(MediaReadinessWork("original", WorkInfo.State.ENQUEUED))
            val replacement = listOf(MediaReadinessWork("concurrent", WorkInfo.State.ENQUEUED))
            val work = MutableStateFlow(original)
            val attempted = mutableListOf<List<MediaReadinessWork>>()
            resumeMediaReadiness(work, { true }) { expected ->
                attempted += expected
                if (expected == original) {
                    work.value = replacement
                    false
                } else {
                    true
                }
            }
            assertEquals(listOf(original, replacement), attempted)
        }

    @Test
    fun `changed account or policy cannot enqueue or acknowledge the old wake`() =
        runTest {
            var replacements = 0
            val thrown =
                runCatching {
                    resumeMediaReadiness(MutableStateFlow(emptyList()), { false }) {
                        replacements++
                        true
                    }
                }.exceptionOrNull()
            assertIs<IllegalStateException>(thrown)
            assertEquals(0, replacements)
        }

    @Test
    fun `work persistence failures leave the wake unacknowledged`() =
        runTest {
            val failure = IOException("Work queue persistence interrupted")
            val thrown =
                runCatching {
                    resumeMediaReadiness(MutableStateFlow(emptyList()), { true }) { throw failure }
                }.exceptionOrNull()
            assertTrue(thrown === failure)
        }
}
