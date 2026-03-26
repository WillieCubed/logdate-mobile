@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:class-naming")

package app.logdate.client.domain.restore

import app.logdate.client.domain.export.ExportDraft
import app.logdate.client.domain.export.ExportNote
import app.logdate.client.domain.export.ExportSchemaVersion
import app.logdate.shared.model.Journal

/**
 * A parsed export bundle that migrations can transform.
 */
data class ParsedExportBundle(
    val journals: List<Journal>,
    val notes: List<ExportNote>,
    val drafts: List<ExportDraft>,
)

/**
 * A single forward-migration step between two adjacent schema versions.
 */
interface ExportMigration {
    val from: ExportSchemaVersion
    val to: ExportSchemaVersion

    fun migrate(bundle: ParsedExportBundle): ParsedExportBundle
}

/**
 * Chains applicable [ExportMigration]s to bring a [ParsedExportBundle]
 * from a source version up to the current schema.
 */
class ExportMigrationRunner(
    private val migrations: List<ExportMigration>,
) {
    fun run(
        sourceVersion: ExportSchemaVersion,
        bundle: ParsedExportBundle,
    ): ParsedExportBundle {
        var current = bundle
        for (migration in migrations) {
            if (sourceVersion <= migration.from) {
                current = migration.migrate(current)
            }
        }
        return current
    }
}

/**
 * The registered set of migrations for the LogDate export format.
 *
 * Add new migrations here in version order. The runner applies them
 * sequentially based on the archive's source version.
 */
val exportMigrations: List<ExportMigration> =
    listOf(
        V1_0ToV1_1Migration(),
    )

/**
 * v1.0 → v1.1: Populate [ExportDraft.journalIds] from the legacy
 * singular [ExportDraft.journalId] field.
 */
class V1_0ToV1_1Migration : ExportMigration {
    override val from = ExportSchemaVersion.V1_0
    override val to = ExportSchemaVersion.V1_1

    override fun migrate(bundle: ParsedExportBundle): ParsedExportBundle =
        bundle.copy(
            drafts =
                bundle.drafts.map { draft ->
                    if (draft.journalIds.isEmpty() && draft.journalId != null) {
                        draft.copy(journalIds = listOf(draft.journalId))
                    } else {
                        draft
                    }
                },
        )
}
