package app.logdate.server.database

import app.logdate.server.database.support.localTestPostgresDataSource
import app.logdate.server.database.support.localTestPostgresUrl
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.sql.Statement
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The migration that removes what deleted accounts left behind deletes only that, against a real
 * PostgreSQL. It runs in its own schema, migrated to the version before the cleanup, seeded, and
 * then migrated to the latest version.
 */
class DeletedAccountRepositoriesMigrationPostgresTest {
    @Test
    fun `cleanup removes deleted accounts' repositories and keeps every live one`() {
        val url = localTestPostgresUrl()
        val schema = "cleanup_${UUID.randomUUID().toString().replace("-", "")}"
        val source = localTestPostgresDataSource(url)
        try {
            migrate(source, schema, target = "40")
            val withDid = UUID.randomUUID()
            val withoutDid = UUID.randomUUID()
            val deleted = UUID.randomUUID()
            source.connection.use { connection ->
                connection.autoCommit = true
                connection.createStatement().use { sql ->
                    sql.execute("SET search_path TO $schema")
                    sql.insertAccount(withDid, did = "did:plc:live")
                    sql.insertAccount(withoutDid, did = null)
                    sql.insertSyncState(withDid, repoDid = "did:plc:live")
                    sql.insertRepository("did:plc:live")
                    // An account without a DID whose repository was written before its sync state was.
                    sql.insertRepository(syntheticDid(withoutDid))
                    sql.insertSyncState(deleted, repoDid = "did:plc:gone")
                    sql.insertRepository("did:plc:gone")
                    sql.insertRepository(syntheticDid(deleted))
                }
            }

            migrate(source, schema, target = null)

            source.connection.use { connection ->
                connection.autoCommit = true
                connection.createStatement().use { sql ->
                    sql.execute("SET search_path TO $schema")
                    assertEquals(
                        setOf("did:plc:live", syntheticDid(withoutDid)),
                        sql.strings(
                            "SELECT repo_did FROM atproto_repo_heads UNION SELECT repo_did FROM atproto_repo_commits " +
                                "UNION SELECT repo_did FROM atproto_repo_block_links",
                        ),
                    )
                    assertEquals(
                        setOf("shared", "only-did:plc:live", "only-${syntheticDid(withoutDid)}"),
                        sql.strings("SELECT cid FROM atproto_repo_blocks"),
                    )
                    assertEquals(
                        setOf(withDid.toString()),
                        sql.strings(SYNC_INDEX_USERS),
                    )

                    sql.execute("DELETE FROM accounts WHERE id = '$withDid'")
                    assertEquals(
                        emptySet(),
                        sql.strings(SYNC_INDEX_USERS),
                    )
                }
            }
        } finally {
            source.connection.use { connection ->
                connection.autoCommit = true
                connection.createStatement().use { it.execute("DROP SCHEMA IF EXISTS $schema CASCADE") }
            }
            source.close()
        }
    }

    private fun migrate(
        source: HikariDataSource,
        schema: String,
        target: String?,
    ) {
        Flyway
            .configure()
            .dataSource(source)
            .schemas(schema)
            .defaultSchema(schema)
            .locations("classpath:db/migration")
            .apply { if (target != null) target(target) }
            .load()
            .migrate()
    }

    private companion object {
        const val SYNC_INDEX_USERS =
            "SELECT user_id::text FROM logdate_collection_states UNION SELECT user_id::text FROM logdate_collection_records"
    }

    private fun syntheticDid(accountId: UUID) = "did:web:sync-${accountId.toString().replace("-", "")}.logdate.internal"

    private fun Statement.insertAccount(
        id: UUID,
        did: String?,
    ) {
        val didValue = did?.let { "'$it'" } ?: "NULL"
        execute(
            "INSERT INTO accounts(id, username, display_name, did, created_at) " +
                "VALUES ('$id', 'u${id.toString().replace("-", "").take(16)}', 'Person', $didValue, now())",
        )
    }

    private fun Statement.insertSyncState(
        userId: UUID,
        repoDid: String,
    ) {
        execute("INSERT INTO logdate_collection_states(user_id, repo_did, last_version) VALUES ('$userId', '$repoDid', 1)")
        execute(
            "INSERT INTO logdate_collection_records(user_id, collection, record_key, server_version) " +
                "VALUES ('$userId', 'entry', 'entry-1', 1)",
        )
    }

    /** Writes a repository whose blocks include one shared by every repository. */
    private fun Statement.insertRepository(repoDid: String) {
        execute("INSERT INTO atproto_repo_heads(repo_did, root_cid, commit_cid, revision) VALUES ('$repoDid', 'root', 'commit', 1)")
        execute(
            "INSERT INTO atproto_repo_commits(repo_did, revision, cid, root_cid, created_at_epoch_millis, record_count, signature) " +
                "VALUES ('$repoDid', 1, 'commit', 'root', 1, 1, 'signature')",
        )
        for (cid in listOf("shared", "only-$repoDid")) {
            execute("INSERT INTO atproto_repo_blocks(cid, bytes) VALUES ('$cid', '\\x01') ON CONFLICT DO NOTHING")
            execute("INSERT INTO atproto_repo_block_links(repo_did, cid) VALUES ('$repoDid', '$cid')")
        }
    }

    private fun Statement.strings(query: String): Set<String> =
        executeQuery(query).use { rows ->
            buildSet {
                while (rows.next()) add(rows.getString(1))
            }
        }
}
