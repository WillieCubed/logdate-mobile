package app.logdate.server.database

import app.logdate.server.space.SpaceBlob
import app.logdate.server.space.SpaceRecordWrite
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.Assume.assumeTrue
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

/** Explicit disposable PostgreSQL only; forces competing writes to wait on the same state row. */
class SpaceStorePostgresTest {
    @Test
    fun `concurrent writes get distinct sequence numbers and account deletion removes every space row`() =
        runBlocking {
            val url = System.getenv("LOGDATE_DIAGNOSTIC_TEST_DATABASE_URL")
            assumeTrue(System.getenv("LOGDATE_DIAGNOSTIC_TEST_DISPOSABLE") == "true" && url != null)
            require(java.net.URI(requireNotNull(url)).host in setOf("localhost", "127.0.0.1"))
            val previous = TransactionManager.defaultDatabase
            val source = DatabaseConfig.createDataSource(databaseUrl = url, username = null, password = null) as HikariDataSource
            val owner = UUID.randomUUID()
            val author = "did:plc:${owner.toString().replace("-", "").take(24)}"
            val space = "at://$author/space/app.logdate.space.personal/self"

            fun write(rkey: String) =
                SpaceRecordWrite(owner, space, author, rkey, sealed(), blobs = emptyList(), swapCid = null, createOnly = false)
            try {
                TransactionManager.defaultDatabase = DatabaseConfig.initializeDatabase(source, autoMigrate = true)
                insertTestAccountRow(owner)
                val store = PostgreSQLSpaceStore()

                val initial = (1..8).map { async(Dispatchers.IO) { store.put(write("initial-$it")).seq } }.awaitAll()
                assertEquals((1L..8L).toList(), initial.sorted(), "Concurrent first writes must share one sequence")

                source.connection.use { lock ->
                    lock.autoCommit = false
                    lock.prepareStatement("SELECT last_seq FROM space_states WHERE space_uri = ? AND author_did = ? FOR UPDATE").use {
                        it.setString(1, space)
                        it.setString(2, author)
                        it.executeQuery().close()
                    }
                    val first = async(Dispatchers.IO) { store.put(write("first")) }
                    val second = async(Dispatchers.IO) { store.put(write("second")) }
                    try {
                        withTimeout(15_000) {
                            while (blockedOnSpaceStates(source) < 2) delay(25)
                        }
                    } finally {
                        lock.rollback()
                    }
                    val seqs = listOf(first.await().seq, second.await().seq)
                    assertEquals(listOf(9L, 10L), seqs.sorted(), "Concurrent writes must not share a sequence number")
                }

                val blobPath = "users/$owner/spaces/blob"
                store.putBlob(owner, space, SpaceBlob("bafyblob", "application/octet-stream", 1, blobPath))
                store.put(write("with-blob").copy(blobs = listOf("bafyblob")))
                store.delete(owner, space, author, "first", swapCid = null)
                assertEquals(1, count(source, "SELECT count(*) FROM space_record_blobs WHERE space_uri = ?", space))

                deleteTestAccountRow(owner)

                assertEquals(0, count(source, "SELECT count(*) FROM space_states WHERE account_id = ?", owner))
                assertEquals(0, count(source, "SELECT count(*) FROM space_records WHERE account_id = ?", owner))
                assertEquals(0, count(source, "SELECT count(*) FROM space_record_blobs WHERE space_uri = ?", space))
                assertEquals(0, count(source, "SELECT count(*) FROM space_blobs WHERE account_id = ?", owner))
                assertEquals(
                    1,
                    count(
                        source,
                        "SELECT count(*) FROM account_blob_cleanup_paths WHERE owner_id = ? AND storage_path = ?",
                        owner,
                        blobPath,
                    ),
                    "Deleting the account must queue its space blobs for deletion from object storage",
                )
            } finally {
                deleteTestAccountRow(owner)
                source.close()
                TransactionManager.defaultDatabase = previous
            }
        }

    private fun sealed() =
        buildJsonObject {
            put("\$type", "app.logdate.crypto.sealed")
            put("version", 1)
            put("keyId", "abcdefghijklm")
            putJsonObject("nonce") { put("\$bytes", Base64.encode(ByteArray(12))) }
            putJsonObject("ciphertext") { put("\$bytes", Base64.encode(ByteArray(528))) }
        }

    private fun blockedOnSpaceStates(source: HikariDataSource): Int =
        source.connection.use { monitor ->
            monitor.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() " +
                            "AND wait_event_type = 'Lock' AND query LIKE '%space_states%'",
                    ).use { rows ->
                        rows.next()
                        rows.getInt(1)
                    }
            }
        }

    private fun count(
        source: HikariDataSource,
        sql: String,
        vararg parameters: Any,
    ): Int =
        source.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                parameters.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeQuery().use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        }
}
