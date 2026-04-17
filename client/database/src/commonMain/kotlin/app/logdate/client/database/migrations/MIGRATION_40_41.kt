@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Adds the inferred People graph:
 * - unresolved inferred clusters
 * - evidence rows explaining why a cluster exists
 * - cross-primitive person links
 * - sticky resolution decisions for rejected names
 */
val MIGRATION_40_41 =
    object : Migration(40, 41) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS inferred_person_clusters (
                    id TEXT NOT NULL,
                    display_name_hint TEXT NOT NULL,
                    normalized_name TEXT NOT NULL,
                    status TEXT NOT NULL,
                    linked_person_id TEXT,
                    created INTEGER NOT NULL,
                    last_updated INTEGER NOT NULL,
                    PRIMARY KEY(id),
                    FOREIGN KEY(linked_person_id) REFERENCES people(id) ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_inferred_person_clusters_status ON inferred_person_clusters(status)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_inferred_person_clusters_linked_person_id ON inferred_person_clusters(linked_person_id)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_inferred_person_clusters_normalized_name ON inferred_person_clusters(normalized_name)",
            )

            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS inferred_person_evidence (
                    id TEXT NOT NULL,
                    cluster_id TEXT NOT NULL,
                    source_type TEXT NOT NULL,
                    source_id TEXT NOT NULL,
                    label TEXT,
                    confidence REAL NOT NULL,
                    created INTEGER NOT NULL,
                    PRIMARY KEY(id),
                    FOREIGN KEY(cluster_id) REFERENCES inferred_person_clusters(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_inferred_person_evidence_cluster_id ON inferred_person_evidence(cluster_id)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_inferred_person_evidence_source_type_source_id ON inferred_person_evidence(source_type, source_id)",
            )

            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS person_links (
                    id TEXT NOT NULL,
                    person_id TEXT NOT NULL,
                    target_type TEXT NOT NULL,
                    target_id TEXT NOT NULL,
                    provenance TEXT NOT NULL,
                    confidence REAL NOT NULL,
                    status TEXT NOT NULL,
                    created INTEGER NOT NULL,
                    last_updated INTEGER NOT NULL,
                    PRIMARY KEY(id),
                    FOREIGN KEY(person_id) REFERENCES people(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_person_links_person_id ON person_links(person_id)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_person_links_target_type_target_id ON person_links(target_type, target_id)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_person_links_status ON person_links(status)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_person_links_provenance ON person_links(provenance)",
            )
            connection.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_person_links_person_id_target_type_target_id ON person_links(person_id, target_type, target_id)",
            )

            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS person_resolution_decisions (
                    id TEXT NOT NULL,
                    normalized_name TEXT NOT NULL,
                    action TEXT NOT NULL,
                    person_id TEXT,
                    created INTEGER NOT NULL,
                    last_updated INTEGER NOT NULL,
                    PRIMARY KEY(id),
                    FOREIGN KEY(person_id) REFERENCES people(id) ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            connection.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_person_resolution_decisions_normalized_name ON person_resolution_decisions(normalized_name)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_person_resolution_decisions_person_id ON person_resolution_decisions(person_id)",
            )
        }
    }
