package app.logdate.feature.core.restore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArchivePickerRequestGateTest {
    @Test
    fun `a result returned after cancellation is discarded`() {
        val gate = ArchivePickerRequestGate()
        gate.beginSelection()
        gate.invalidate()
        assertEquals(ArchivePickerResult.Ignored, gate.consumeResult())
    }

    @Test
    fun `a newer selection waits for the existing picker result to be drained`() {
        val gate = ArchivePickerRequestGate()
        val old = assertNotNull(gate.beginSelection())
        gate.invalidate()
        assertNull(gate.beginSelection())
        val queued = assertIs<ArchivePickerResult.Launch>(gate.consumeResult()).request
        assertTrue(queued != old)
        assertEquals(ArchivePickerResult.Selected(queued), gate.consumeResult())
        assertEquals(ArchivePickerResult.Ignored, gate.consumeResult())
    }

    @Test
    fun `only the latest queued selection launches and a cancelled queue cannot launch`() {
        val gate = ArchivePickerRequestGate()
        gate.beginSelection()
        assertNull(gate.beginSelection())
        assertNull(gate.beginSelection())
        gate.invalidate()
        assertEquals(ArchivePickerResult.Ignored, gate.consumeResult())
    }

    @Test
    fun `provider IO completing after cancellation cannot assign a URI or notify`() {
        val gate = ArchivePickerRequestGate()
        val request = assertNotNull(gate.beginSelection())
        assertEquals(ArchivePickerResult.Selected(request), gate.consumeResult())
        var selectedUri: String? = null
        var callbacks = 0
        commitArchiveSelectionRequest(Job(), request, gate, commit = { action ->
            gate.invalidate()
            action()
        }) {
            selectedUri = "stale-provider-uri"
            callbacks++
        }
        assertNull(selectedUri)
        assertEquals(0, callbacks)
    }

    @Test
    fun `cancellation while provider IO blocks propagates without error delivery`() {
        val gate = ArchivePickerRequestGate()
        val request = assertNotNull(gate.beginSelection())
        val job = Job()
        var failures = 0
        assertFailsWith<CancellationException> {
            commitArchiveSelectionRequest(job, request, gate, commit = { action ->
                job.cancel()
                action()
            }) { failures++ }
        }
        assertEquals(0, failures)
    }

    @Test
    fun `live selected metadata is delivered under the checked lock`() {
        val gate = ArchivePickerRequestGate()
        val request = assertNotNull(gate.beginSelection())
        assertEquals(ArchivePickerResult.Selected(request), gate.consumeResult())
        var locked = false
        var callbacks = 0
        commitArchiveSelectionRequest(Job(), request, gate, commit = { action ->
            locked = true
            action()
            locked = false
        }) {
            assertTrue(locked)
            callbacks++
        }
        assertFalse(locked)
        assertEquals(1, callbacks)
    }

    @Test
    fun `a failed launch is retired and a later result cannot be accepted`() {
        val gate = ArchivePickerRequestGate()
        val request = assertNotNull(gate.beginSelection())
        gate.launchFailed(request)
        assertEquals(ArchivePickerResult.Ignored, gate.consumeResult())
        assertNotNull(gate.beginSelection())
    }
}
