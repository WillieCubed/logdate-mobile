package app.logdate.client.domain.restore

import app.logdate.client.domain.export.ExportDraft
import app.logdate.client.domain.export.ExportSchemaVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class ExportMigrationsTest {
    private val now = Clock.System.now()
    private val runner = ExportMigrationRunner(exportMigrations)

    @Test
    fun `V1_0ToV1_1 populates journalIds from journalId`() {
        val draft =
            ExportDraft(
                id = "draft-1",
                journalId = "journal-abc",
                content = "Hello",
                createdAt = now,
                updatedAt = now,
            )
        val bundle = ParsedExportBundle(journals = emptyList(), notes = emptyList(), drafts = listOf(draft))

        val result = runner.run(ExportSchemaVersion.V1_0, bundle)

        assertEquals(1, result.drafts.size)
        assertEquals(listOf("journal-abc"), result.drafts[0].journalIds)
    }

    @Test
    fun `V1_0ToV1_1 leaves draft unchanged when journalIds already set`() {
        val draft =
            ExportDraft(
                id = "draft-1",
                journalId = "journal-abc",
                journalIds = listOf("journal-abc", "journal-def"),
                content = "Hello",
                createdAt = now,
                updatedAt = now,
            )
        val bundle = ParsedExportBundle(journals = emptyList(), notes = emptyList(), drafts = listOf(draft))

        val result = runner.run(ExportSchemaVersion.V1_0, bundle)

        assertEquals(listOf("journal-abc", "journal-def"), result.drafts[0].journalIds)
    }

    @Test
    fun `runner is no-op for current version`() {
        val draft =
            ExportDraft(
                id = "draft-1",
                journalId = "journal-abc",
                content = "Hello",
                createdAt = now,
                updatedAt = now,
            )
        val bundle = ParsedExportBundle(journals = emptyList(), notes = emptyList(), drafts = listOf(draft))

        val result = runner.run(ExportSchemaVersion.CURRENT, bundle)

        assertTrue(result.drafts[0].journalIds.isEmpty(), "Should not migrate current version drafts")
    }

    @Test
    fun `V1_0ToV1_1 handles null journalId`() {
        val draft =
            ExportDraft(
                id = "draft-1",
                content = "No journal",
                createdAt = now,
                updatedAt = now,
            )
        val bundle = ParsedExportBundle(journals = emptyList(), notes = emptyList(), drafts = listOf(draft))

        val result = runner.run(ExportSchemaVersion.V1_0, bundle)

        assertTrue(result.drafts[0].journalIds.isEmpty())
    }
}
