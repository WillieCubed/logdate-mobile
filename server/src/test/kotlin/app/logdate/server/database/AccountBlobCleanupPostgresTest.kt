@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.database

import app.logdate.server.auth.AccountBlobCleanup
import app.logdate.server.auth.requireAccountBlobCleanupSchema
import app.logdate.server.database.support.localTestPostgresDataSource
import app.logdate.server.database.support.withOwnPostgresDatabase
import app.logdate.server.logdate.InMemoryLogDateBlobStorage
import app.logdate.server.logdate.LogDateBlobStorage
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AccountBlobCleanupPostgresTest {
    @Test
    fun `account cascade durably preserves every blob reference including deleted media`() =
        withDatabase { connection ->
            val owner = UUID.randomUUID()
            connection.createStatement().use { sql ->
                sql.execute(
                    "INSERT INTO accounts(id, username, display_name, created_at) VALUES ('$owner', 'cleanup_${owner.toString().replace(
                        "-",
                        "",
                    ).take(16)}', 'Delete', now())",
                )
                sql.execute(
                    """INSERT INTO logdate_media_records(user_id, media_id, content_id, file_name, mime_type,
                size_bytes, data, storage_path, created_at, version, device_id, deleted)
                VALUES ('$owner', 'media', 'entry', 'file', 'text/plain', 0, '', 'users/$owner/media/media/file', 1, 1, 'device', true)""",
                )
                sql.execute(
                    """INSERT INTO logdate_atproto_blobs(user_id, cid, mime_type, size_bytes, storage_path, created_at)
                VALUES ('$owner', 'cid', 'text/plain', 0, 'users/$owner/atproto/blobs/cid', 1)""",
                )
                sql.execute(
                    """INSERT INTO logdate_backups(id, user_id, device_id, manifest, storage_path, created_at, size_bytes)
                VALUES ('$owner', '$owner', 'device', '{}', 'users/$owner/backups/backup.enc', 1, 0)""",
                )
                sql.execute("DELETE FROM accounts WHERE id='$owner'")
                sql.executeQuery("SELECT count(*) FROM account_blob_cleanup_paths WHERE owner_id='$owner'").use {
                    it.next()
                    assertEquals(3, it.getInt(1))
                }
                sql.executeQuery("SELECT count(*) FROM account_blob_cleanup WHERE owner_id='$owner'").use {
                    it.next()
                    assertEquals(1, it.getInt(1))
                }
                assertFails {
                    sql.execute(
                        "INSERT INTO accounts(id, username, display_name, created_at) VALUES ('$owner', 'cleanup_${owner.toString().replace(
                            "-",
                            "",
                        ).take(16)}', 'Reuse', now())",
                    )
                }
            }
        }

    @Test
    fun `outbox persistence failure rolls back account cascade`() =
        withDatabase { connection ->
            val owner = UUID.randomUUID()
            connection.createStatement().use { sql ->
                sql.execute(
                    "INSERT INTO accounts(id, username, display_name, created_at) VALUES ('$owner', 'cleanup_${owner.toString().replace(
                        "-",
                        "",
                    ).take(16)}', 'Delete', now())",
                )
                sql.execute("ALTER TABLE account_blob_cleanup ADD CONSTRAINT reject_test_owner CHECK (owner_id <> '$owner')")
                try {
                    assertFails { sql.execute("DELETE FROM accounts WHERE id='$owner'") }
                    sql.executeQuery("SELECT count(*) FROM accounts WHERE id='$owner'").use {
                        it.next()
                        assertEquals(1, it.getInt(1))
                    }
                } finally {
                    sql.execute("ALTER TABLE account_blob_cleanup DROP CONSTRAINT reject_test_owner")
                }
            }
        }

    @Test
    fun `retry after restart catches untracked late uploads and missing objects`() =
        withDatabase { connection ->
            val owner = UUID.randomUUID()
            val storage = InMemoryLogDateBlobStorage()
            connection.createStatement().use { sql ->
                sql.execute(
                    "INSERT INTO accounts(id, username, display_name, created_at) VALUES ('$owner', 'cleanup_${owner.toString().replace(
                        "-",
                        "",
                    ).take(16)}', 'Delete', now())",
                )
                sql.execute("DELETE FROM accounts WHERE id='$owner'")
            }
            connection.createStatement().use { sql ->
                sql.execute(
                    "INSERT INTO account_blob_cleanup_paths(owner_id, storage_path) VALUES ('$owner', 'users/$owner/backups/already-missing.enc')",
                )
            }
            val path = storage.uploadBackup(owner, UUID.randomUUID(), byteArrayOf(1))
            val cleanup = AccountBlobCleanup(requireNotNull(DatabaseConfig.initializedDataSource), storage)
            assertEquals(1, cleanup.runOnce(owner))
            assertNull(storage.getBlob(path))
            val latePath = storage.uploadBackup(owner, UUID.randomUUID(), byteArrayOf(2))
            assertNotNull(storage.getBlob(latePath))
            connection.createStatement().use {
                it.execute(
                    "UPDATE account_blob_cleanup SET next_attempt_at=now(), lease_token='$owner', lease_until=now()+interval '15 minutes', reconcile_until=now()-interval '1 second' WHERE owner_id='$owner'",
                )
            }
            assertEquals(0, AccountBlobCleanup(requireNotNull(DatabaseConfig.initializedDataSource), storage).runOnce(owner))
            connection.createStatement().use {
                it.execute("UPDATE account_blob_cleanup SET lease_until=now()-interval '1 second' WHERE owner_id='$owner'")
            }
            assertEquals(1, AccountBlobCleanup(requireNotNull(DatabaseConfig.initializedDataSource), storage).runOnce(owner))
            assertNull(storage.getBlob(latePath))
            assertEquals(0, cleanup.runOnce(owner))
        }

    @Test
    fun `storage failure retains queue and expired lease resumes cleanup`() =
        withDatabase { connection ->
            val owner = UUID.randomUUID()
            val storage = InMemoryLogDateBlobStorage()
            val path = storage.uploadBackup(owner, UUID.randomUUID(), byteArrayOf(1))
            connection.createStatement().use { sql ->
                sql.execute(
                    "INSERT INTO accounts(id, username, display_name, created_at) VALUES ('$owner', 'cleanup_${owner.toString().replace(
                        "-",
                        "",
                    ).take(16)}', 'Delete', now())",
                )
                sql.execute("DELETE FROM accounts WHERE id='$owner'")
            }
            val failingStorage =
                object : LogDateBlobStorage by storage {
                    override fun deleteBlobIfExists(storagePath: String): Unit = throw IllegalStateException("unavailable")
                }
            val source = requireNotNull(DatabaseConfig.initializedDataSource)
            assertFails { AccountBlobCleanup(source, failingStorage).runOnce(owner) }
            assertNotNull(storage.getBlob(path))
            connection.createStatement().use {
                it.execute(
                    "UPDATE account_blob_cleanup SET next_attempt_at=now(), lease_until=now()-interval '1 second' WHERE owner_id='$owner'",
                )
            }
            assertEquals(1, AccountBlobCleanup(source, storage).runOnce(owner))
            assertNull(storage.getBlob(path))
        }

    @Test
    fun `overlapping workers lease exclusively and repeated delete keeps the marker`() =
        withDatabase { connection ->
            val owner = UUID.randomUUID()
            val otherOwner = UUID.randomUUID()
            val storage = InMemoryLogDateBlobStorage()
            val target = storage.uploadBackup(owner, UUID.randomUUID(), byteArrayOf(1))
            val unrelated = storage.uploadBackup(otherOwner, UUID.randomUUID(), byteArrayOf(2))
            connection.createStatement().use { sql ->
                sql.execute(
                    "INSERT INTO accounts(id, username, display_name, created_at) VALUES ('$owner', 'lease_${owner.toString().take(
                        8,
                    )}', 'Delete', now())",
                )
                assertEquals(1, sql.executeUpdate("DELETE FROM accounts WHERE id='$owner'"))
                assertEquals(0, sql.executeUpdate("DELETE FROM accounts WHERE id='$owner'"))
            }
            val started = CountDownLatch(1)
            val finish = CountDownLatch(1)
            val slow =
                object : LogDateBlobStorage by storage {
                    override fun deleteBlobIfExists(storagePath: String) {
                        started.countDown()
                        check(finish.await(10, TimeUnit.SECONDS))
                        storage.deleteBlobIfExists(storagePath)
                    }
                }
            val source = requireNotNull(DatabaseConfig.initializedDataSource)
            val executor = Executors.newSingleThreadExecutor()
            try {
                val first = executor.submit<Int> { AccountBlobCleanup(source, slow).runOnce(owner) }
                check(started.await(10, TimeUnit.SECONDS))
                assertEquals(0, AccountBlobCleanup(source, storage).runOnce(owner))
                finish.countDown()
                assertEquals(1, first.get(10, TimeUnit.SECONDS))
                assertNull(storage.getBlob(target))
                assertNotNull(storage.getBlob(unrelated))
            } finally {
                finish.countDown()
                executor.shutdownNow()
            }
        }

    @Test
    fun `active owners fail closed and enumeration failure leaves a pending marker`() =
        withDatabase { connection ->
            val owner = UUID.randomUUID()
            val storage = InMemoryLogDateBlobStorage()
            val target = storage.uploadBackup(owner, UUID.randomUUID(), byteArrayOf(1))
            connection.createStatement().use { sql ->
                sql.execute(
                    "INSERT INTO accounts(id, username, display_name, created_at) VALUES ('$owner', 'active_${owner.toString().take(
                        8,
                    )}', 'Active', now())",
                )
                sql.execute("INSERT INTO account_blob_cleanup(owner_id) VALUES ('$owner')")
            }
            val source = requireNotNull(DatabaseConfig.initializedDataSource)
            assertFails { AccountBlobCleanup(source, storage).runOnce(owner) }
            assertNotNull(storage.getBlob(target))
            connection.createStatement().use { sql ->
                sql.execute("DELETE FROM accounts WHERE id='$owner'")
                sql.execute("UPDATE account_blob_cleanup SET next_attempt_at=now() WHERE owner_id='$owner'")
            }
            val unavailable =
                object : LogDateBlobStorage by storage {
                    override fun listOwnerBlobPaths(ownerId: UUID): List<String> = throw IllegalStateException("unavailable")
                }
            assertFails { AccountBlobCleanup(source, unavailable).runOnce(owner) }
            assertNotNull(storage.getBlob(target))
        }

    @Test
    fun `runtime schema validation rejects a disabled cleanup trigger`() =
        withDatabase { connection ->
            val source = requireNotNull(DatabaseConfig.initializedDataSource)
            requireAccountBlobCleanupSchema(source)
            connection.createStatement().use { sql ->
                sql.execute("ALTER TABLE accounts DISABLE TRIGGER accounts_enqueue_blob_cleanup")
                try {
                    assertFails { requireAccountBlobCleanupSchema(source) }
                } finally {
                    sql.execute("ALTER TABLE accounts ENABLE TRIGGER accounts_enqueue_blob_cleanup")
                }
            }
        }

    @Test
    fun `concurrent account identifier reuse cannot pass an uncommitted deletion marker`() =
        withDatabase { connection ->
            val owner = UUID.randomUUID()
            val username = "reuse_${owner.toString().take(8)}"
            val source = requireNotNull(DatabaseConfig.initializedDataSource)
            connection.createStatement().use {
                it.execute(
                    "INSERT INTO accounts(id, username, display_name, created_at) VALUES ('$owner', '$username', 'Delete', now())",
                )
            }
            connection.autoCommit = false
            connection.createStatement().use { it.execute("DELETE FROM accounts WHERE id='$owner'") }
            val executor = Executors.newSingleThreadExecutor()
            val started = CountDownLatch(1)
            val backend =
                java.util.concurrent.atomic
                    .AtomicInteger()
            try {
                val reused =
                    executor.submit<Boolean> {
                        source.connection.use { attempt ->
                            attempt.autoCommit = true
                            attempt.createStatement().use { sql ->
                                sql.executeQuery("SELECT pg_backend_pid()").use {
                                    it.next()
                                    backend.set(it.getInt(1))
                                }
                                started.countDown()
                                runCatching {
                                    sql.execute(
                                        "INSERT INTO accounts(id, username, display_name, created_at) VALUES ('$owner', '$username', 'Reuse', now())",
                                    )
                                }.isSuccess
                            }
                        }
                    }
                check(started.await(10, TimeUnit.SECONDS))
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                var blocked = false
                while (!blocked && System.nanoTime() < deadline) {
                    source.connection.use { observer ->
                        observer.autoCommit = true
                        observer.createStatement().use { sql ->
                            sql.executeQuery("SELECT wait_event_type='Lock' FROM pg_stat_activity WHERE pid=${backend.get()}").use {
                                blocked = it.next() && it.getBoolean(1)
                            }
                        }
                    }
                    if (!blocked) Thread.sleep(10)
                }
                check(blocked) { "Concurrent insertion did not reach the database lock" }
                connection.commit()
                assertFalse(reused.get(10, TimeUnit.SECONDS))
            } finally {
                connection.rollback()
                connection.autoCommit = true
                executor.shutdownNow()
            }
        }

    /**
     * Runs [action] in a database of its own: deleted accounts stay in the cleanup queue on purpose, and
     * one test changes the queue table's constraints.
     */
    private fun withDatabase(action: (Connection) -> Unit) =
        withOwnPostgresDatabase("logdate_agent_blob_cleanup_") { url ->
            val source = localTestPostgresDataSource(url)
            try {
                DatabaseConfig.initializeDatabase(source, autoMigrate = true)
                source.connection.use {
                    it.autoCommit = true
                    action(it)
                }
            } finally {
                source.close()
            }
        }
}
