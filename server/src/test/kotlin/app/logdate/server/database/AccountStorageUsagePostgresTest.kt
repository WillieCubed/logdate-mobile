package app.logdate.server.database

import app.logdate.server.database.support.localTestPostgresDataSource
import app.logdate.server.database.support.withOwnPostgresDatabase
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.io.File
import java.sql.Connection
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/** Runs against the local test PostgreSQL, in a database of its own, through the real migrations. */
class AccountStorageUsagePostgresTest {
    @Test
    fun `migrating counts what each account already stores in spaces, media and backups`() =
        withOwnPostgresDatabase("storage_backfill_") { url ->
            localTestPostgresDataSource(url).use { source ->
                migrate(source, target = versionBefore("Add_account_storage_usage"))
                val owner = UUID.randomUUID()
                val empty = UUID.randomUUID()
                val backupOnly = UUID.randomUUID()
                source.sql { sql ->
                    for (account in listOf(owner, empty, backupOnly)) sql.insertAccount(account)
                    val repository = "'at://did:plc:owner/space/app.logdate.space.personal/self', 'did:plc:owner'"
                    sql.execute(
                        "INSERT INTO space_records(space_uri, author_did, rkey, account_id, seq, size_bytes, deleted) VALUES " +
                            "($repository, 'live', '$owner', 1, 600, false), ($repository, 'gone', '$owner', 2, 0, true)",
                    )
                    sql.execute(
                        "INSERT INTO space_blobs(space_uri, cid, account_id, mime_type, size_bytes, storage_path) VALUES " +
                            "('at://did:plc:owner/space/app.logdate.space.personal/self', 'bafyblob', '$owner', " +
                            "'application/octet-stream', 3000000000, 'users/$owner/spaces/blob')",
                    )
                    sql.insertMedia(owner, "kept", 30, deleted = false)
                    sql.insertMedia(owner, "removed", 40, deleted = true)
                    sql.insertBackup(owner, 50)
                    sql.insertBackup(owner, 20)
                    sql.insertBackup(backupOnly, 7)
                }

                migrate(source, target = null)

                source.sql { sql ->
                    assertEquals(
                        mapOf(
                            owner.toString() to Totals(space = 3_000_000_600L, media = 30, backup = 70),
                            backupOnly.toString() to Totals(space = 0, media = 0, backup = 7),
                        ),
                        sql.totals(),
                        "Accounts with nothing stored get no row; the rest match the rows they count",
                    )
                }
            }
        }

    @Test
    fun `media and backup writes keep the totals current, including by other server revisions`() =
        withOwnPostgresDatabase("storage_triggers_") { url ->
            localTestPostgresDataSource(url).use { source ->
                migrate(source, target = null)
                val account = UUID.randomUUID()
                val other = UUID.randomUUID()
                source.sql { sql ->
                    sql.insertAccount(account)
                    sql.insertAccount(other)
                    sql.insertMedia(account, "a", 100, deleted = false)
                    sql.insertMedia(account, "b", 40, deleted = false)
                    sql.insertMedia(other, "c", 9, deleted = false)
                    sql.insertBackup(account, 500)
                    assertEquals(Totals(0, 140, 500), sql.totals().getValue(account.toString()), "inserts add")

                    sql.execute("UPDATE logdate_media_records SET size_bytes = 60 WHERE user_id = '$account' AND media_id = 'b'")
                    assertEquals(Totals(0, 160, 500), sql.totals().getValue(account.toString()), "a changed size adds the difference")

                    sql.execute("UPDATE logdate_media_records SET deleted = true WHERE user_id = '$account' AND media_id = 'a'")
                    assertEquals(Totals(0, 60, 500), sql.totals().getValue(account.toString()), "deleting media stops counting it")

                    sql.execute("UPDATE logdate_media_records SET deleted = false WHERE user_id = '$account' AND media_id = 'a'")
                    sql.execute("UPDATE logdate_media_records SET version = 2 WHERE user_id = '$account'")
                    assertEquals(
                        Totals(0, 160, 500),
                        sql.totals().getValue(account.toString()),
                        "restoring counts again, other edits count nothing",
                    )

                    sql.execute("DELETE FROM logdate_media_records WHERE user_id = '$account' AND media_id = 'a'")
                    sql.execute("DELETE FROM logdate_backups WHERE user_id = '$account'")
                    assertEquals(Totals(0, 60, 0), sql.totals().getValue(account.toString()), "deleting rows removes their bytes")
                    assertEquals(Totals(0, 9, 0), sql.totals().getValue(other.toString()), "another account's total is untouched")

                    sql.execute("UPDATE logdate_media_records SET user_id = '$other' WHERE user_id = '$account'")
                    assertEquals(Totals(0, 0, 0), sql.totals().getValue(account.toString()), "moved media leaves its old account")
                    assertEquals(Totals(0, 69, 0), sql.totals().getValue(other.toString()), "moved media joins its new account")

                    sql.execute("DELETE FROM accounts WHERE id IN ('$account', '$other')")
                    assertEquals(emptyMap(), sql.totals(), "deleting an account deletes its rows and totals without error")
                }
            }
        }

    @Test
    fun `a total that would go below zero is stored as zero`() =
        withOwnPostgresDatabase("storage_floor_") { url ->
            localTestPostgresDataSource(url).use { source ->
                migrate(source, target = null)
                val account = UUID.randomUUID()
                source.sql { sql ->
                    sql.insertAccount(account)
                    sql.insertBackup(account, 30)
                    sql.execute("UPDATE account_storage_usage SET backup_bytes = 10 WHERE account_id = '$account'")
                    sql.execute("DELETE FROM logdate_backups WHERE user_id = '$account'")
                    assertEquals(Totals(0, 0, 0), sql.totals().getValue(account.toString()))
                }
            }
        }

    private data class Totals(
        val space: Long,
        val media: Long,
        val backup: Long,
    )

    private fun migrate(
        source: HikariDataSource,
        target: String?,
    ) {
        Flyway
            .configure()
            .dataSource(source)
            .locations("classpath:db/migration")
            .apply { if (target != null) target(target) }
            .load()
            .migrate()
    }

    private fun HikariDataSource.sql(block: (java.sql.Statement) -> Unit) {
        connection.use { connection: Connection ->
            connection.autoCommit = true
            connection.createStatement().use(block)
        }
    }

    private fun java.sql.Statement.totals(): Map<String, Totals> =
        executeQuery("SELECT account_id::text, space_bytes, media_bytes, backup_bytes FROM account_storage_usage").use { rows ->
            buildMap { while (rows.next()) put(rows.getString(1), Totals(rows.getLong(2), rows.getLong(3), rows.getLong(4))) }
        }

    private fun java.sql.Statement.insertAccount(id: UUID) {
        execute(
            "INSERT INTO accounts(id, username, display_name, created_at) " +
                "VALUES ('$id', 'u${id.toString().replace("-", "").take(16)}', 'Person', now())",
        )
    }

    private fun java.sql.Statement.insertMedia(
        account: UUID,
        mediaId: String,
        bytes: Long,
        deleted: Boolean,
    ) {
        execute(
            "INSERT INTO logdate_media_records(user_id, media_id, content_id, file_name, mime_type, size_bytes, data, created_at, " +
                "version, device_id, deleted) VALUES ('$account', '$mediaId', 'content', 'file.jpg', 'image/jpeg', $bytes, " +
                "'\\x01', 1, 1, 'device', $deleted)",
        )
    }

    private fun java.sql.Statement.insertBackup(
        account: UUID,
        bytes: Long,
    ) {
        execute(
            "INSERT INTO logdate_backups(id, user_id, device_id, manifest, storage_path, created_at, size_bytes) VALUES " +
                "('${UUID.randomUUID()}', '$account', 'device', '{}', 'backups/${UUID.randomUUID()}', 1, $bytes)",
        )
    }

    /** The newest migration version before the one named [description], so the test starts from the schema it changes. */
    private fun versionBefore(description: String): String {
        val folder = File(requireNotNull(javaClass.classLoader.getResource("db/migration")) { "No migrations on the classpath" }.toURI())
        val versions = folder.list().orEmpty().mapNotNull { Regex("""V(\d+)__(.*)\.sql""").matchEntire(it)?.destructured }
        val mine = versions.single { (_, name) -> name == description }.component1().toInt()
        return versions
            .map { it.component1().toInt() }
            .filter { it < mine }
            .max()
            .toString()
    }
}
