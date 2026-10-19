package app.logdate.server.transfer

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TransferSlotsTest {
    @Test
    fun `each kind of transfer has its own slots, freed when a transfer ends`() {
        val slots = TransferSlots(TransferLimits(streamedUploads = 1, downloads = 2, bufferedTransfers = 1))

        val upload = assertNotNull(slots.tryStreamedUpload())
        assertNull(slots.tryStreamedUpload(), "the only upload slot is taken")
        val buffered = assertNotNull(slots.tryBuffered(), "buffered transfers have their own slot")
        assertNull(slots.tryBuffered())
        val first = assertNotNull(slots.tryDownload(), "downloads have their own slots")
        val second = assertNotNull(slots.tryDownload())
        assertNull(slots.tryDownload(), "both download slots are taken")

        first.release()
        first.release()
        val third = assertNotNull(slots.tryDownload(), "a released slot is free again")
        assertNull(slots.tryDownload(), "releasing twice frees one slot")

        upload.release()
        buffered.release()
        second.release()
        third.release()
        assertNotNull(slots.tryStreamedUpload())
        assertNotNull(slots.tryBuffered())
    }

    @Test
    fun `the defaults fit a 512 MiB instance`() {
        val limits = TransferLimits()
        val streamedBytes = limits.streamedUploads * (2L * 1024 * 1024 + 64 * 1024) + limits.downloads * (2L * 1024 * 1024 + 64 * 1024)
        val heapBytes = 384L * 1024 * 1024

        assert(streamedBytes < heapBytes / 4) { "Streamed transfers take $streamedBytes bytes of a $heapBytes byte heap" }
        assert(limits.bufferedTransfers == 1)
    }
}
