package app.logdate.client.data.restore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class RetainedJournalMergePlanTest {
    @Test
    fun `new identity retains source id and exact duplicates are skipped`() {
        val empty = RetainedJournalMergeCheckpoint.empty()
        val first = empty.plan("generation-a", "note", SOURCE_ID, SOURCE_HASH, null)
        assertEquals(SOURCE_ID, first.targetId)
        assertEquals(RetainedJournalMergeCheckpoint.Decision.INSERT, first.decision)

        val duplicate = first.checkpoint.plan("generation-a", "note", SOURCE_ID, SOURCE_HASH, SOURCE_HASH)
        assertEquals(SOURCE_ID, duplicate.targetId)
        assertEquals(RetainedJournalMergeCheckpoint.Decision.ALREADY_PRESENT, duplicate.decision)
    }

    @Test
    fun `working collision gets deterministic generation scoped variant`() {
        val checkpoint = RetainedJournalMergeCheckpoint.empty()
        val first = checkpoint.plan("generation-a", "note", SOURCE_ID, SOURCE_HASH, WORKING_HASH)
        val retry = checkpoint.plan("generation-a", "note", SOURCE_ID, SOURCE_HASH, WORKING_HASH)
        val otherGeneration = checkpoint.plan("generation-b", "note", SOURCE_ID, SOURCE_HASH, WORKING_HASH)

        assertNotEquals(SOURCE_ID, first.targetId)
        assertEquals(first.targetId, retry.targetId)
        assertNotEquals(first.targetId, otherGeneration.targetId)
        assertEquals(RetainedJournalMergeCheckpoint.Decision.INSERT, first.decision)
    }

    @Test
    fun `source identity cannot silently change within retained generation`() {
        val checkpoint = RetainedJournalMergeCheckpoint.empty()
        val first = checkpoint.plan("generation-a", "note", SOURCE_ID, SOURCE_HASH, null)
        val changed = first.checkpoint.plan("generation-a", "note", SOURCE_ID, WORKING_HASH, null)
        assertEquals(RetainedJournalMergeCheckpoint.Decision.SOURCE_CHANGED, changed.decision)
        assertNull(changed.targetId)
    }

    @Test
    fun `a collision introduced after planning does not overwrite working row`() {
        val checkpoint = RetainedJournalMergeCheckpoint.empty()
        val before = checkpoint.plan("generation-a", "journal", SOURCE_ID, SOURCE_HASH, null)
        val after = before.checkpoint.plan("generation-a", "journal", SOURCE_ID, SOURCE_HASH, WORKING_HASH)
        assertEquals(SOURCE_ID, before.targetId)
        assertNotEquals(SOURCE_ID, after.targetId)
    }

    @Test
    fun `a durable variant is recognized after a crash before its applied checkpoint`() {
        val expectedTarget = "3".repeat(64)
        val planned =
            RetainedJournalMergeCheckpoint.empty().plan(
                "generation-a",
                "note_image",
                SOURCE_ID,
                SOURCE_HASH,
                WORKING_HASH,
                expectedTargetSha256 = expectedTarget,
            )
        val retry =
            planned.checkpoint.plan(
                "generation-a",
                "note_image",
                SOURCE_ID,
                SOURCE_HASH,
                WORKING_HASH,
                currentMappedTargetSha256 = expectedTarget,
                expectedTargetSha256 = expectedTarget,
            )

        assertEquals(planned.targetId, retry.targetId)
        assertEquals(RetainedJournalMergeCheckpoint.Decision.ALREADY_PRESENT, retry.decision)
        assertEquals(planned.key, retry.key)
    }

    @Test
    fun `applied variant remains stable when original later matches source`() {
        val checkpoint = RetainedJournalMergeCheckpoint.empty()
        val planned = checkpoint.plan("generation-a", "journal", SOURCE_ID, SOURCE_HASH, WORKING_HASH)
        val applied = planned.checkpoint.markApplied(planned.key, WORKING_HASH)

        val retry = applied.plan("generation-a", "journal", SOURCE_ID, SOURCE_HASH, SOURCE_HASH)

        assertEquals(planned.targetId, retry.targetId)
        assertEquals(RetainedJournalMergeCheckpoint.Decision.ALREADY_PRESENT, retry.decision)
        assertEquals(
            planned.targetId,
            retry.checkpoint.entries
                .getValue(retry.key)
                .targetId,
        )
    }

    @Test
    fun `a durable original id with transformed media is recognized after a crash`() {
        val expectedTarget = "4".repeat(64)
        val planned =
            RetainedJournalMergeCheckpoint.empty().plan(
                "generation-a",
                "note_image",
                SOURCE_ID,
                SOURCE_HASH,
                null,
                expectedTargetSha256 = expectedTarget,
            )
        val retry =
            planned.checkpoint.plan(
                "generation-a",
                "note_image",
                SOURCE_ID,
                SOURCE_HASH,
                expectedTarget,
                expectedTargetSha256 = expectedTarget,
            )

        assertEquals(SOURCE_ID, retry.targetId)
        assertEquals(RetainedJournalMergeCheckpoint.Decision.ALREADY_PRESENT, retry.decision)
    }

    @Test
    fun `applied recovered row changed by user is never replaced by a second stale variant`() {
        val planned = RetainedJournalMergeCheckpoint.empty().plan("generation-a", "note", SOURCE_ID, SOURCE_HASH, null)
        val applied = planned.checkpoint.markApplied(planned.key)
        val retry = applied.plan("generation-a", "note", SOURCE_ID, SOURCE_HASH, WORKING_HASH)
        assertEquals(planned.targetId, retry.targetId)
        assertEquals(RetainedJournalMergeCheckpoint.Decision.ALREADY_PRESENT, retry.decision)
        val deleted = applied.plan("generation-a", "note", SOURCE_ID, SOURCE_HASH, null)
        assertEquals(RetainedJournalMergeCheckpoint.Decision.ALREADY_PRESENT, deleted.decision)
    }

    @Test
    fun `pending original row survives crash when verified media arrives`() {
        val pendingHash = "5".repeat(64)
        val recoveredHash = "6".repeat(64)
        val planned =
            RetainedJournalMergeCheckpoint.empty().plan(
                "generation-a",
                "note_image",
                SOURCE_ID,
                SOURCE_HASH,
                null,
                expectedTargetSha256 = pendingHash,
            )
        val retry =
            planned.checkpoint.plan(
                "generation-a",
                "note_image",
                SOURCE_ID,
                SOURCE_HASH,
                pendingHash,
                expectedTargetSha256 = recoveredHash,
            )
        assertEquals(SOURCE_ID, retry.targetId)
        assertEquals(RetainedJournalMergeCheckpoint.Decision.ALREADY_PRESENT, retry.decision)
    }

    private companion object {
        const val SOURCE_ID = "2b0ee8b4-5dbb-46bf-94c6-52ad9e2b8450"
        val SOURCE_HASH = "1".repeat(64)
        val WORKING_HASH = "2".repeat(64)
    }
}
