package app.logdate.client.media.audio.transcription

import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class AndroidTranscriptionPriorityTest {
    @Test
    fun `distinct media with colliding String hashes keep independent unique work names`() {
        assertEquals("Aa:1".hashCode(), "BB:1".hashCode())
        val queued = QueueFixture()
        val queue = queued.queue(canExpedite = true)
        val noteId = Uuid.random()
        queue.enqueue(noteId, "Aa", "1", TranscriptionPriority.RECOVERY)
        queue.enqueue(noteId, "BB", "1", TranscriptionPriority.RECOVERY)
        assertEquals(2, queued.names.toSet().size)
    }

    @Test
    fun `visible queued audio updates the same work id once without duplicate recognition`() {
        val noteId = Uuid.random()
        val pending = buildTranscriptionWorkRequest(noteId, "memo.m4a")
        val queued = QueueFixture(listOf(WorkInfo(pending.id, WorkInfo.State.ENQUEUED, pending.tags)))
        val queue = queued.queue(canExpedite = true)
        queue.enqueue(noteId, "memo.m4a", "1", TranscriptionPriority.FOREGROUND)
        queue.enqueue(noteId, "memo.m4a", "1", TranscriptionPriority.FOREGROUND)
        queue.enqueue(noteId, "memo.m4a", "1", TranscriptionPriority.RECOVERY)
        assertTrue(queued.enqueued.isEmpty())
        val update = queued.updated.single()
        assertEquals(pending.id, update.id)
        assertEquals(pending.workSpec.input, update.workSpec.input)
        assertTrue(update.workSpec.expedited)
        assertFalse(update.workSpec.constraints.requiresBatteryNotLow())
        assertEquals(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST, update.workSpec.outOfQuotaPolicy)
    }

    @Test
    fun `foreground promotion preserves an active worker and never enqueues a duplicate`() {
        val noteId = Uuid.random()
        val running = buildTranscriptionWorkRequest(noteId, "memo.m4a")
        val queued = QueueFixture(listOf(WorkInfo(running.id, WorkInfo.State.RUNNING, running.tags)))
        queued.queue(canExpedite = true).enqueue(noteId, "memo.m4a", "1", TranscriptionPriority.FOREGROUND)
        assertTrue(queued.enqueued.isEmpty())
        assertTrue(queued.updated.isEmpty())
        assertEquals(WorkInfo.State.RUNNING, queued.current.single().state)
        assertEquals(running.id, queued.current.single().id)
    }

    @Test
    fun `Android 11 foreground work remains schedulable without an unsupported expedited worker`() {
        val queued = QueueFixture()
        queued.queue(canExpedite = false).enqueue(Uuid.random(), "memo.m4a", "1", TranscriptionPriority.FOREGROUND)
        val request = queued.enqueued.single()
        assertFalse(request.workSpec.expedited)
        assertFalse(request.workSpec.constraints.requiresBatteryNotLow())
    }

    private class QueueFixture(
        var current: List<WorkInfo> = emptyList(),
    ) {
        val enqueued = mutableListOf<OneTimeWorkRequest>()
        val names = mutableListOf<String>()
        val updated = mutableListOf<OneTimeWorkRequest>()

        fun queue(canExpedite: Boolean) =
            AndroidTranscriptionWorkQueue(
                find = { current },
                enqueueUnique = { name, request ->
                    names += name
                    enqueued += request
                },
                update = { request ->
                    updated += request
                    current = listOf(WorkInfo(request.id, WorkInfo.State.ENQUEUED, request.tags))
                },
                canExpedite = canExpedite,
            )
    }
}
