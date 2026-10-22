package app.logdate.client.device.restore

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NativePreferenceCaptureTest {
    @Test
    fun `failed observation prevents the platform from replacing preference bytes`() {
        val store = MemoryStore()
        var destination = "existing choices"
        val capture = NativePreferenceCapture("generation", store, { destination.encodeToByteArray() }, { false })
        capture.begin()
        store.failWrites = true

        assertFailsWith<IOException> {
            capture.restore({ "incoming choices".encodeToByteArray() }, { destination = "incoming choices" }, { error("Not a duplicate") })
        }

        assertEquals("existing choices", destination)
    }

    @Test
    fun `successful copy is not accepted when its delivery receipt cannot persist`() {
        val store = MemoryStore()
        var destination = "existing choices"
        val capture = NativePreferenceCapture("generation", store, { destination.encodeToByteArray() }, { false })
        capture.begin()
        store.failedState = "delivered"

        assertFailsWith<IOException> {
            capture.restore({ "incoming choices".encodeToByteArray() }, { destination = "incoming choices" }, { error("Not a duplicate") })
        }

        assertEquals("incoming choices", destination)
        assertEquals("prepared", store.value?.state)
        assertFailsWith<IOException> { capture.deliveryDecision() }
    }

    @Test
    fun `receipt failure recovers after recreation without platform file redelivery`() {
        val store = MemoryStore()
        var destination = "existing choices"
        val capture = NativePreferenceCapture("generation", store, { destination.encodeToByteArray() }, { false })
        capture.begin()
        store.failedState = "delivered"
        assertFailsWith<IOException> {
            capture.restore({ "incoming choices".encodeToByteArray() }, { destination = "incoming choices" }, { error("Not a duplicate") })
        }
        store.failedState = null

        val recreated =
            NativePreferenceCapture(
                "generation",
                store,
                { destination.encodeToByteArray() },
                { true },
                repairDestination = { destination = it.decodeToString() },
            )

        assertEquals(true, recreated.deliveryDecision())
        assertContentEquals("incoming choices".encodeToByteArray(), recreated.read())
    }

    @Test
    fun `durable delivery receipt survives a failed byte snapshot and completion recreation`() {
        val store = MemoryStore()
        var destination = "existing choices"
        var completed = false
        val capture = NativePreferenceCapture("generation", store, { destination.encodeToByteArray() }, { completed })
        capture.begin()
        capture.restore({ "incoming choices".encodeToByteArray() }, { destination = "incoming choices" }, { error("Not a duplicate") })
        store.failedState = "captured"
        assertFailsWith<IOException> { capture.capture() }
        assertEquals("delivered", store.value?.state)
        store.failedState = null

        val recreated = NativePreferenceCapture("generation", store, { destination.encodeToByteArray() }, { completed })
        assertEquals(true, recreated.deliveryDecision())
        assertFailsWith<IOException> { recreated.read() }
        completed = true
        assertContentEquals("incoming choices".encodeToByteArray(), recreated.read())
    }

    @Test
    fun `unknown copy outcome cannot become an omitted preference on completion`() {
        val store = MemoryStore()
        val capture = NativePreferenceCapture("generation", store, { error("Unconfirmed payload") }, { true })
        capture.begin()
        assertEquals(false, capture.deliveryDecision())
        capture.observe()

        assertFailsWith<IOException> { capture.deliveryDecision() }
    }

    @Test
    fun `duplicate delivery drains its input without replacing the accepted file`() {
        val store = MemoryStore()
        var destination = "existing choices"
        var drained = false
        val capture = NativePreferenceCapture("generation", store, { destination.encodeToByteArray() }, { true })
        capture.begin()
        capture.restore({ "incoming choices".encodeToByteArray() }, { destination = "incoming choices" }, { error("Not a duplicate") })
        capture.restore({ "different duplicate".encodeToByteArray() }, { destination = "different duplicate" }, { drained = true })

        assertEquals(true, drained)
        assertEquals("incoming choices", destination)
        assertEquals(true, capture.deliveryDecision())
        assertContentEquals("incoming choices".encodeToByteArray(), capture.read())
    }

    @Test
    fun `a genuine new generation can accept its own preference payload`() {
        val store = MemoryStore()
        var source = "previous delivery".encodeToByteArray()
        val previous = NativePreferenceCapture("previous", store, { source }, { true })
        previous.begin()
        previous.capture()
        val replacement = NativePreferenceCapture("replacement", store, { source }, { true })
        replacement.begin()
        replacement.restore({
            "replacement delivery".encodeToByteArray()
        }, { source = "replacement delivery".encodeToByteArray() }, { error("New generation") })

        assertEquals(true, replacement.deliveryDecision())
        assertContentEquals("replacement delivery".encodeToByteArray(), replacement.read())
    }

    @Test
    fun `duplicate callback observation cannot replace captured delivery bytes`() {
        val store = MemoryStore()
        var source = "transferred choices".encodeToByteArray()
        val capture = NativePreferenceCapture("generation", store, { source }, { true })
        capture.begin()
        capture.observe()
        capture.capture()

        source = "later destination choices".encodeToByteArray()
        capture.observe()
        capture.capture()

        assertContentEquals("transferred choices".encodeToByteArray(), capture.read())
    }

    @Test
    fun `failed capture retains observed source and retries without accepting empty defaults`() {
        val store = MemoryStore()
        val source = "<map><string name=\"2.chosenNoteId\">entry B</string></map>".encodeToByteArray()
        val capture = NativePreferenceCapture("generation", store, { source }, { true })
        capture.begin()
        capture.observe()
        store.failWrites = true
        assertFailsWith<IOException> { capture.capture() }
        assertEquals("observed", store.value?.state)
        assertFailsWith<IOException> { capture.read() }
        store.failWrites = false
        assertContentEquals(source, NativePreferenceCapture("generation", store, { source }, { true }).read())
        assertEquals("captured", store.value?.state)
        val newer = NativePreferenceCapture("generation", store, { "edited after capture".encodeToByteArray() }, { true })
        assertContentEquals(source, newer.read())
        newer.finish(delivered = true)
        assertContentEquals(source, newer.read())
    }

    @Test
    fun `observed metadata cannot read an incomplete native payload`() {
        val store = MemoryStore()
        val capture = NativePreferenceCapture("generation", store, { error("Incomplete payload must not be read") }, { false })
        capture.begin()
        capture.observe()
        assertFailsWith<IOException> { capture.read() }
    }

    @Test
    fun `omitted preferences stay distinct from pending capture and previous deliveries`() {
        val store = MemoryStore()
        val capture = NativePreferenceCapture("generation", store, { error("Omitted payload must not be read") }, { true })
        capture.begin()
        assertFailsWith<IOException> { capture.read() }
        capture.finish(delivered = false)
        assertNull(capture.read())
        assertFailsWith<IOException> { NativePreferenceCapture("replacement generation", store, { byteArrayOf() }, { true }).read() }
    }

    @Test
    fun `failed omission finalization is repaired from the completed delivery decision after recreation`() {
        val store = MemoryStore()
        val source = { error("An omitted payload must never read current preferences") }
        NativePreferenceCapture("generation", store, source, { true }).begin()
        store.failWrites = true
        assertFailsWith<IOException> { NativePreferenceCapture("generation", store, source, { true }).finish(false) }
        store.failWrites = false

        val recreated = NativePreferenceCapture("generation", store, source, { true }, { false })

        assertNull(recreated.read())
        assertEquals("omitted", store.value?.state)
    }

    @Test
    fun `delivered payload remains recoverable when observation and snapshot persistence both failed`() {
        val store = MemoryStore()
        var source = "transferred widget choices".encodeToByteArray()
        val capture = NativePreferenceCapture("generation", store, { source }, { true })
        capture.begin()
        store.failWrites = true
        assertFailsWith<IOException> { capture.observe() }
        assertFailsWith<IOException> { capture.capture() }
        store.failWrites = false

        val recreated = NativePreferenceCapture("generation", store, { source }, { true }, { true })
        assertContentEquals("transferred widget choices".encodeToByteArray(), recreated.read())
        source = "destination edited later".encodeToByteArray()
        assertContentEquals("transferred widget choices".encodeToByteArray(), recreated.read())
    }

    @Test
    fun `completed omission decision repairs an absent snapshot without reading destination defaults`() {
        val store = MemoryStore()
        val capture =
            NativePreferenceCapture("generation", store, { error("Missing source was not delivered") }, { true }, { false })

        assertNull(capture.read())
        assertEquals("omitted", store.value?.state)
    }

    @Test
    fun `final decision cannot authorize pending snapshot recovery before Android finishes delivery`() {
        val store = MemoryStore()
        val capture = NativePreferenceCapture("generation", store, { error("Delivery is incomplete") }, { false }, { false })
        capture.begin()

        assertFailsWith<IOException> { capture.read() }
        assertEquals("pending", store.value?.state)
    }

    @Test
    fun `an observed callback without successful delivery cannot capture partial destination preferences`() {
        val store = MemoryStore()
        val source = { error("The final decision says this payload was not delivered") }
        val capture = NativePreferenceCapture("generation", store, source, { true })
        capture.begin()
        capture.observe()
        store.failWrites = true
        assertFailsWith<IOException> { capture.finish(false) }
        store.failWrites = false

        assertNull(NativePreferenceCapture("generation", store, source, { true }, { false }).read())
        assertEquals("omitted", store.value?.state)
    }

    @Test
    fun `failed pre-copy retention never mutates the destination`() {
        val store = MemoryStore()
        var destination = "existing choices"
        val capture = NativePreferenceCapture("generation", store, { destination.encodeToByteArray() }, { false })
        capture.begin()
        store.failedState = "prepared"

        assertFailsWith<IOException> {
            capture.restore({ "incoming choices".encodeToByteArray() }, { destination = it.decodeToString() }, { error("Duplicate") })
        }

        assertEquals("existing choices", destination)
        assertEquals("observed", store.value?.state)
    }

    @Test
    fun `interrupted platform copy is repaired from its complete retained input`() {
        val store = MemoryStore()
        var destination = "existing choices"
        val capture = NativePreferenceCapture("generation", store, { destination.encodeToByteArray() }, { false })
        capture.begin()
        assertFailsWith<IOException> {
            capture.restore(
                { "incoming choices".encodeToByteArray() },
                {
                    destination = "partial"
                    throw IOException("Interrupted framework copy")
                },
                { error("Duplicate") },
            )
        }
        val recreated =
            NativePreferenceCapture(
                "generation",
                store,
                { destination.encodeToByteArray() },
                { true },
                repairDestination = { destination = it.decodeToString() },
            )

        assertEquals(true, recreated.deliveryDecision())
        assertEquals("incoming choices", destination)
        assertContentEquals("incoming choices".encodeToByteArray(), recreated.read())
    }

    @Test
    fun `a second receipt failure during replay retains the original for another recreation`() {
        val store = MemoryStore()
        var destination = "existing choices"

        fun capture() =
            NativePreferenceCapture(
                "generation",
                store,
                { destination.encodeToByteArray() },
                { true },
                repairDestination = { destination = it.decodeToString() },
            )
        capture().begin()
        store.failedState = "delivered"
        assertFailsWith<IOException> {
            capture().restore({ "incoming choices".encodeToByteArray() }, { destination = it.decodeToString() }, { error("Duplicate") })
        }
        destination = "partial destination"

        assertFailsWith<IOException> { capture().deliveryDecision() }
        assertEquals("prepared", store.value?.state)
        assertEquals("incoming choices", destination)
        store.failedState = null
        destination = "another partial destination"

        assertEquals(true, capture().deliveryDecision())
        assertEquals("incoming choices", destination)
        assertContentEquals("incoming choices".encodeToByteArray(), capture().read())
    }

    @Test
    fun `duplicate after interrupted copy drains input and replays the first prepared payload`() {
        val store = MemoryStore()
        var destination = "existing choices"
        var drained = false
        val capture =
            NativePreferenceCapture(
                "generation",
                store,
                { destination.encodeToByteArray() },
                { true },
                repairDestination = { destination = it.decodeToString() },
            )
        capture.begin()
        assertFailsWith<IOException> {
            capture.restore({ "first choices".encodeToByteArray() }, { throw IOException("Copy interrupted") }, { error("Duplicate") })
        }

        capture.restore({ error("Duplicate input cannot replace prepared bytes") }, { error("Not a new delivery") }, { drained = true })

        assertEquals(true, drained)
        assertEquals("first choices", destination)
        assertContentEquals("first choices".encodeToByteArray(), capture.read())
    }

    private class MemoryStore : NativePreferenceSnapshotStore {
        var value: NativePreferenceSnapshot? = null
        var failWrites = false
        var failedState: String? = null

        override fun read() = value

        override fun write(snapshot: NativePreferenceSnapshot) {
            if (failWrites || snapshot.state == failedState) throw IOException("Synthetic storage failure")
            value = snapshot
        }
    }
}
