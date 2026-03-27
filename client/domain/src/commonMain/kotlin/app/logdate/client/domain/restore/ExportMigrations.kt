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
        if (sourceVersion > ExportSchemaVersion.CURRENT) {
            return bundle
        }

        val migrationMap = migrations.associateBy { it.from }
        var currentVersion = sourceVersion
        var current = bundle

        while (currentVersion < ExportSchemaVersion.CURRENT) {
            val migration =
                migrationMap[currentVersion]
                    ?: error("Missing export migration from $currentVersion to ${ExportSchemaVersion.CURRENT}")
            current = migration.migrate(current)
            currentVersion = migration.to
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
        V1_1ToV1_2Migration(),
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

/**
 * v1.1 -> v1.2 is additive-only. Draft blocks and sync metadata deserialize via
 * default values, so no structural rewrite is needed beyond explicit version advancement.
 */
class V1_1ToV1_2Migration : ExportMigration {
    override val from = ExportSchemaVersion.V1_1
    override val to = ExportSchemaVersion.V1_2

    override fun migrate(bundle: ParsedExportBundle): ParsedExportBundle = bundle
}
