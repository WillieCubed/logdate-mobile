package app.logdate.client.data.restore

import app.logdate.client.media.storage.UnavailableMediaReferences
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RetainedMediaReferencePolicyTest {
    @Test
    fun `missing source bytes cannot expose an unrelated working URI`() {
        val unavailable = retainedUnavailableMediaReference(ORIGINAL)
        assertTrue(UnavailableMediaReferences.isUnavailable(unavailable))
        assertEquals(ORIGINAL, UnavailableMediaReferences.nativeSource(unavailable))
        assertEquals(unavailable, retainedUnavailableMediaReference(unavailable))
    }

    @Test
    fun `verified bytes repair quarantine even when canonical URI equals original`() {
        val unavailable = retainedUnavailableMediaReference(ORIGINAL)
        assertEquals(ORIGINAL, repairRetainedMediaReference(unavailable, ORIGINAL, ORIGINAL))
        assertEquals(RECOVERED, repairRetainedMediaReference(unavailable, ORIGINAL, RECOVERED))
        assertEquals(RECOVERED, repairRetainedMediaReference(unavailable, unavailable, RECOVERED))
        assertEquals(RECOVERED, repairRetainedMediaReference(ORIGINAL, ORIGINAL, RECOVERED))
    }

    @Test
    fun `repair preserves a user replacement removal or still missing attachment`() {
        val unavailable = retainedUnavailableMediaReference(ORIGINAL)
        assertEquals("user-choice", repairRetainedMediaReference("user-choice", ORIGINAL, RECOVERED))
        assertNull(repairRetainedMediaReference(null, ORIGINAL, RECOVERED))
        assertEquals(unavailable, repairRetainedMediaReference(unavailable, ORIGINAL, unavailable))
        assertEquals(RECOVERED, repairRetainedMediaReference(RECOVERED, ORIGINAL, unavailable))
    }

    @Test
    fun `separately arriving draft attachments have independent idempotent repair receipts`() {
        val first = retainedMediaRepairId("generation", "draft", "1".repeat(64))
        val second = retainedMediaRepairId("generation", "draft", "2".repeat(64))
        assertNotEquals(first, second)
        assertEquals(second, retainedMediaRepairId("generation", "draft", "2".repeat(64)))
    }

    @Test
    fun `unchanged raw source is quarantined when retained bytes are still missing`() {
        val unavailable = retainedUnavailableMediaReference(ORIGINAL)
        assertEquals(unavailable, repairRetainedMediaReference(ORIGINAL, ORIGINAL, unavailable))
        assertEquals("user-choice", repairRetainedMediaReference("user-choice", ORIGINAL, unavailable))
    }

    @Test
    fun `already applied prefix does not replay media imports before late pending row`() =
        runTest {
            val entry = RetainedJournalMergeCheckpoint.Entry("generation", "note", "source", "1".repeat(64), "target", applied = true)
            var imports = 0
            repeat(500) { index ->
                val reference = if (index == 499) retainedUnavailableMediaReference(ORIGINAL) else RECOVERED
                if (shouldReimportRetainedMedia(entry, entry.sourceSha256, true, listOf(reference)) { true }) imports++
            }
            assertEquals(1, imports)
            assertFalse(
                shouldReimportRetainedMedia(entry, entry.sourceSha256, false, listOf(ORIGINAL)) { error("Deleted target is preserved") },
            )
            assertTrue(shouldReimportRetainedMedia(entry, entry.sourceSha256, true, listOf(ORIGINAL)) { false })
            assertTrue(shouldReimportRetainedMedia(null, entry.sourceSha256, true, listOf(ORIGINAL)) { true })
        }

    @Test
    fun `draft remains eligible until each separately arriving asset is available`() =
        runTest {
            val entry = RetainedJournalMergeCheckpoint.Entry("generation", "draft", "source", "1".repeat(64), "target", applied = true)
            assertTrue(
                shouldReimportRetainedMedia(
                    entry,
                    entry.sourceSha256,
                    true,
                    listOf(RECOVERED, retainedUnavailableMediaReference(ORIGINAL)),
                ) {
                    true
                },
            )
            assertFalse(shouldReimportRetainedMedia(entry, entry.sourceSha256, true, listOf(RECOVERED, ORIGINAL)) { true })
        }

    private companion object {
        const val ORIGINAL = "logdate-media://library/old.png"
        const val RECOVERED = "logdate-media://library/recovered.png"
    }
}
