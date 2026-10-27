package app.logdate.server.database

import app.logdate.server.database.support.localTestPostgresDataSource
import app.logdate.server.database.support.localTestPostgresUrl
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.junit.Assume.assumeTrue
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Adding storage modes leaves every account that already exists in `legacy`, against a real
 * PostgreSQL. It runs in its own schema, migrated to the version before storage modes, seeded, and
 * then migrated to the latest version. It uses the safe local PostgreSQL test fixture, including
 * an explicitly disposable diagnostic database, and never migrates a database on another machine.
 */
class AccountStorageModeMigrationPostgresTest {
    @Test
    fun `accounts that exist before storage modes stay in legacy`() {
        val url = localTestPostgresUrl()
        val host = java.net.URI(url.removePrefix("jdbc:")).host
        assumeTrue(
            "DATABASE_URL points at $host; this test only migrates a database on this machine",
            host in setOf("localhost", "127.0.0.1"),
        )
        val schema = "storage_mode_${UUID.randomUUID().toString().replace("-", "")}"
        val source = localTestPostgresDataSource(url)
        try {
            migrate(source, schema, target = versionBeforeStorageModes())
            val existing = UUID.randomUUID()
            val deactivated = UUID.randomUUID()
            sql(source, schema) {
                execute(insertAccount(existing))
                execute(insertAccount(deactivated))
                execute("UPDATE accounts SET is_active = FALSE WHERE id = '$deactivated'")
            }

            migrate(source, schema, target = null)

            val created = UUID.randomUUID()
            sql(source, schema) {
                execute(insertAccount(created))
                executeQuery("SELECT id::text, storage_mode FROM accounts").use { rows ->
                    val modes = buildMap { while (rows.next()) put(rows.getString(1), rows.getString(2)) }
                    assertEquals(
                        mapOf(existing.toString() to "legacy", deactivated.toString() to "legacy", created.toString() to "legacy"),
                        modes,
                        "every account that existed is backfilled to legacy, and a new row without a mode starts in legacy",
                    )
                }
                executeQuery("SELECT public_identity_did FROM accounts").use { rows ->
                    while (rows.next()) assertNull(rows.getString(1), "upgrading never grants publication consent")
                }
            }
        } finally {
            sql(source, schema = null) { execute("DROP SCHEMA IF EXISTS $schema CASCADE") }
            source.close()
        }
    }

    /** The newest migration older than the one adding storage modes, whatever number each has. */
    private fun versionBeforeStorageModes(): String {
        val folder = java.io.File(requireNotNull(javaClass.classLoader.getResource("db/migration")).toURI())
        val versions =
            folder.listFiles().orEmpty().mapNotNull { file ->
                VERSIONED_MIGRATION.matchEntire(file.name)?.let { it.groupValues[1].toInt() to it.groupValues[2] }
            }
        val storageModes = versions.single { (_, description) -> description == "Add_account_storage_mode" }.first
        return versions
            .map { it.first }
            .filter { it < storageModes }
            .max()
            .toString()
    }

    private fun insertAccount(id: UUID): String =
        "INSERT INTO accounts(id, username, display_name, created_at) " +
            "VALUES ('$id', 'u${id.toString().replace("-", "").take(16)}', 'Person', now())"

    private fun sql(
        source: HikariDataSource,
        schema: String?,
        block: java.sql.Statement.() -> Unit,
    ) {
        source.connection.use { connection ->
            connection.autoCommit = true
            connection.createStatement().use { statement ->
                if (schema != null) statement.execute("SET search_path TO $schema")
                statement.block()
            }
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
        val VERSIONED_MIGRATION = Regex("""V(\d+)__(\w+)\.sql""")
    }
}
