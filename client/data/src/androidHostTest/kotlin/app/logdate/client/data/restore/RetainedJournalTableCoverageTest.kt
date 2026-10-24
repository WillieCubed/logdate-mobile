package app.logdate.client.data.restore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetainedJournalTableCoverageTest {
    @Test
    fun notesOnlySourceCanBeFullyCoveredWhenInstallStateIsReconstructed() {
        val result =
            RetainedJournalTableCoverage.assess(
                mapOf("journals" to 1, "text_notes" to 2, "journal_content_links" to 2, "user_devices" to 1),
            )

        assertTrue(result.complete)
        assertTrue(result.unsupportedNonEmptyTables.isEmpty())
        assertTrue(result.unknownNonEmptyTables.isEmpty())
    }

    @Test
    fun anyNonemptyUnimplementedBusinessOrUnknownTableBlocksCompletion() {
        val result =
            RetainedJournalTableCoverage.assess(
                mapOf("journals" to 1, "location_logs" to 1, "future_business_table" to 3),
            )

        assertFalse(result.complete)
        assertEquals(setOf("location_logs"), result.unsupportedNonEmptyTables)
        assertEquals(setOf("future_business_table"), result.unknownNonEmptyTables)
    }

    @Test
    fun emptyUnimplementedTablesDoNotBlockNotesOnlyRecovery() {
        val result =
            RetainedJournalTableCoverage.assess(
                mapOf("journals" to 1, "location_logs" to 0, "future_business_table" to 0),
            )

        assertTrue(result.complete)
    }

    @Test
    fun sourceTombstonesAndNonemptyOutboxStatePreventCompletion() {
        assertFalse(
            RetainedJournalTableCoverage.canComplete(
                mapOf("journals" to 1, "pending_uploads" to 1),
                checkpointsApplied = true,
                unresolvedRows = 1,
                mediaPending = 0,
                draftsCovered = true,
            ),
        )
        assertFalse(
            RetainedJournalTableCoverage.canComplete(
                mapOf("journals" to 1),
                checkpointsApplied = true,
                unresolvedRows = 1,
                mediaPending = 0,
                draftsCovered = true,
            ),
        )
    }
}
