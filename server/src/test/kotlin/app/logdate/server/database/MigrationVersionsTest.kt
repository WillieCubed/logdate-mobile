package app.logdate.server.database

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Checks the Flyway migration files themselves, without a database.
 *
 * Flyway refuses to start a server whose migrations share a version number. The server and the
 * server/client end-to-end tests run on an in-memory store unless `DATABASE_URL` is set, so on a
 * development machine nothing runs Flyway and a duplicate number from two branches landing at once
 * goes unnoticed until CI or a deploy. This test runs everywhere.
 */
class MigrationVersionsTest {
    @Test
    fun `every migration has its own version`() {
        val migrations = migrations()
        val duplicates = migrations.groupBy { it.version }.filterValues { it.size > 1 }
        val nextFree = migrations.maxOf { it.version.first() } + 1

        assertTrue(
            duplicates.isEmpty(),
            duplicates.values.joinToString(separator = "\n") { sharing ->
                "${sharing.joinToString(" and ") { it.fileName }} share version " +
                    "${sharing.first().version.joinToString(".")}. Flyway will refuse to start. " +
                    "Renumber the migration that is not yet deployed to V${nextFree}__."
            },
        )
    }

    @Test
    fun `every file in the migration folder follows the Flyway naming pattern`() {
        val misnamed =
            migrationFolder()
                .listFiles()
                .orEmpty()
                .map { it.name }
                .filter { it !in CALLBACK_FILES && VERSIONED_MIGRATION.matchEntire(it) == null }

        assertTrue(
            misnamed.isEmpty(),
            "Flyway skips these files, so their changes never reach a database: $misnamed. " +
                "Name each one V<version>__<Description>.sql.",
        )
    }

    private data class Migration(
        val fileName: String,
        val version: List<Long>,
    )

    private fun migrations(): List<Migration> =
        migrationFolder().listFiles().orEmpty().mapNotNull { file ->
            val match = VERSIONED_MIGRATION.matchEntire(file.name) ?: return@mapNotNull null
            // Flyway compares versions part by part and ignores trailing zeros, so 34 and 34.0 collide.
            val parts =
                match.groupValues[1]
                    .split('.', '_')
                    .map { it.toLong() }
                    .dropLastWhile { it == 0L }
            Migration(file.name, parts)
        }

    private fun migrationFolder(): File {
        val url = assertNotNull(javaClass.classLoader.getResource(MIGRATION_PATH), "$MIGRATION_PATH is not on the classpath")
        return File(url.toURI())
    }

    private companion object {
        const val MIGRATION_PATH = "db/migration"
        val VERSIONED_MIGRATION = Regex("""V(\d+(?:[._]\d+)*)__\w+\.sql""")

        /** Flyway callbacks, which run around migrations and carry no version. */
        val CALLBACK_FILES = setOf("beforeMigrate.sql")
    }
}
