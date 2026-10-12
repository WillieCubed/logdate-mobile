package app.logdate.wear.sync.v2.window

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowDataEventsTest {
    private val f = WindowFixture()
    private var active = true
    private val applier = mockk<WindowApplier>(relaxed = true)
    private val reconciler = mockk<WindowReconciler>(relaxed = true)
    private val events = WindowDataEvents({ active }, applier, reconciler)

    @Test fun `window events keep their source and wake resync after applying`() =
        runTest {
            val item = f.item()
            assertTrue(events.consume(item))
            events.finishBatch()
            coVerify(exactly = 1) {
                applier.onItem(item)
                reconciler.resync()
            }
            val removal = f.tombstone()
            assertTrue(events.consume(removal))
            coVerify(exactly = 1) { applier.onTombstone(removal) }
        }

    @Test fun `an initial held item still triggers resync to establish ownership`() =
        runTest {
            coEvery { applier.onItem(any()) } returns false
            assertTrue(events.consume(f.item()))
            events.finishBatch()
            coVerify(exactly = 1) { reconciler.resync() }
        }

    @Test fun `legacy and unrelated events cannot bypass a negotiated window`() =
        runTest {
            assertFalse(events.legacyNotesAllowed())
            assertFalse(events.consume(f.item().copy(path = "/logdate/notes/old/delete")))
            coVerify(exactly = 0) {
                applier.onItem(any())
                applier.onTombstone(any())
                reconciler.resync()
            }
            active = false
            assertTrue(events.legacyNotesAllowed())
            assertTrue(events.consume(f.item()))
            coVerify(exactly = 0) {
                applier.onItem(any())
                reconciler.resync()
            }
        }

    @Test fun `a batch of notes triggers one complete catchup rather than a request per item`() =
        runTest {
            events.consume(f.item())
            events.consume(f.tombstone())
            coVerify(exactly = 0) { reconciler.resync() }
            events.finishBatch()
            events.finishBatch()
            coVerify(exactly = 1) { reconciler.resync() }
        }
}
