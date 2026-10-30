package app.logdate.client.database

import android.content.ContentValues
import android.database.Cursor
import android.database.DatabaseUtils
import android.os.CancellationSignal
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises our fence using real threads and mocked Android/JNI boundaries. Production Room
 * queries consume and close cursors synchronously; cross-thread cursor transfer is not promised.
 */
class NativeJournalDatabaseFenceTest {
    private val fixtures = mutableListOf<Fixture>()

    @BeforeTest
    fun setup() {
        mockkStatic(DatabaseUtils::class)
        every { DatabaseUtils.getSqlStatementType(any()) } returns 99
        every { DatabaseUtils.getSqlStatementType("BEGIN") } returns 4
        every { DatabaseUtils.getSqlStatementType("COMMIT") } returns 5
        every { DatabaseUtils.getSqlStatementType("ROLLBACK") } returns 6
    }

    @AfterTest
    fun cleanup() {
        fixtures.forEach { it.close() }
        unmockkStatic(DatabaseUtils::class)
    }

    @Test
    fun `plain transaction excludes snapshot until end`() = transactionBoundary { beginTransaction() }

    @Test
    fun `nonexclusive transaction excludes snapshot until end`() = transactionBoundary { beginTransactionNonExclusive() }

    @Test
    fun `read only transaction excludes snapshot until end`() = transactionBoundary { beginTransactionReadOnly() }

    @Test
    fun `listener transaction excludes snapshot until end`() = transactionBoundary { beginTransactionWithListener(mockk()) }

    @Test
    fun `nonexclusive listener transaction excludes snapshot until end`() =
        transactionBoundary { beginTransactionWithListenerNonExclusive(mockk()) }

    @Test
    fun `read only listener transaction excludes snapshot until end`() =
        transactionBoundary { beginTransactionWithListenerReadOnly(mockk()) }

    @Test
    fun `ending a nested transaction does not release its outer owner`() {
        val fixture = fixture()
        val database = fixture.database
        database.beginTransaction()
        try {
            database.beginTransactionReadOnly()
            database.endTransaction()
            fixture.probeSnapshot().also { it.requireBlocked() }
        } finally {
            database.endTransaction()
        }
        fixture.requireProbesCompleted()
    }

    @Test
    fun `failed begin releases only its own acquisition`() {
        val fixture = fixture()
        val database = fixture.database
        database.beginTransaction()
        try {
            every { fixture.delegate.beginTransactionReadOnly() } throws IllegalStateException("begin failed")
            assertFailsWith<IllegalStateException> { database.beginTransactionReadOnly() }
            fixture.probeSnapshot().requireBlocked()
        } finally {
            database.endTransaction()
        }
        fixture.requireProbesCompleted()
    }

    @Test
    fun `failed outer begin leaves snapshot admission available`() {
        val fixture = fixture()
        every { fixture.delegate.beginTransaction() } throws IllegalStateException("begin failed")
        assertFailsWith<IllegalStateException> { fixture.database.beginTransaction() }
        fixture.probeSnapshot().requireCompleted()
    }

    @Test
    fun `failed end still releases its transaction fence`() {
        val fixture = fixture()
        val database = fixture.database
        database.beginTransaction()
        every { fixture.delegate.endTransaction() } throws IllegalStateException("end failed")
        assertFailsWith<IllegalStateException> { database.endTransaction() }
        fixture.probeSnapshot().requireCompleted()
    }

    @Test
    fun `canonical aliases share snapshot exclusion`() {
        val fixture = fixture()
        val aliasDirectory = File(fixture.root, "alias").apply { mkdirs() }
        val alias = File(aliasDirectory, "../logdate")
        fixture.database.beginTransaction()
        try {
            fixture.probe { withNativeJournalDatabaseFence(alias) {} }.requireBlocked()
        } finally {
            fixture.database.endTransaction()
        }
        fixture.requireProbesCompleted()
    }

    @Test
    fun `a different database remains available during snapshot`() {
        val fixture = fixture()
        withNativeJournalDatabaseFence(fixture.file) {
            fixture.probe { withNativeJournalDatabaseFence(File(fixture.root, "other")) {} }.requireCompleted()
        }
    }

    @Test
    fun `snapshot excludes first helper open close and WAL reconfiguration`() {
        listOf<(Fixture) -> Unit>(
            { it.helper.writableDatabase },
            { it.helper.readableDatabase },
            { it.helper.setWriteAheadLoggingEnabled(true) },
            { it.helper.close() },
        ).forEach { operation ->
            val fixture = fixture()
            assertSnapshotBlocks(fixture) { operation(fixture) }
        }
    }

    @Test
    fun `already open Room ownership metadata does not wait behind another SQL owner`() {
        val fixture = fixture()
        fixture.database
        withNativeJournalDatabaseFence(fixture.file) {
            fixture
                .probe {
                    assertFalse(fixture.helper.writableDatabase.inTransaction())
                    assertFalse(fixture.helper.readableDatabase.inTransaction())
                }.requireCompleted()
        }
    }

    @Test
    fun `closed helper cannot reuse its cached handle`() {
        val fixture = fixture()
        fixture.database
        fixture.helper.close()
        assertSnapshotBlocks(fixture) { fixture.helper.writableDatabase }
    }

    @Test
    fun `a read only handle cannot satisfy a writable open without the fence`() {
        val fixture = fixture()
        every { fixture.delegate.isReadOnly } returns true
        fixture.helper.readableDatabase
        assertSnapshotBlocks(fixture) { fixture.helper.writableDatabase }
    }

    @Test
    fun `snapshot excludes database writes and WAL reconfiguration`() {
        val fixture = fixture()
        val database = fixture.database
        val values = mockk<ContentValues>()
        listOf<() -> Unit>(
            { database.execSQL("UPDATE notes SET value = 1") },
            { database.execSQL("UPDATE notes SET value = ?", arrayOf<Any>(1)) },
            { database.insert("notes", 0, values) },
            { database.update("notes", 0, values, null, null) },
            { database.delete("notes", null, null) },
            { database.enableWriteAheadLogging() },
            { database.disableWriteAheadLogging() },
            { database.close() },
        ).forEach { operation -> assertSnapshotBlocks(fixture, operation) }
    }

    @Test
    fun `compiled statement execution cannot enter during snapshot`() {
        val fixture = fixture()
        val statement = fixture.database.compileStatement("UPDATE notes SET value = 1")
        // Compilation alone must not retain the fence until statement disposal.
        fixture.probeSnapshot().requireCompleted()
        listOf<() -> Unit>(
            { statement.execute() },
            { statement.executeInsert() },
            { statement.executeUpdateDelete() },
            { statement.simpleQueryForLong() },
            { statement.simpleQueryForString() },
        ).forEach { operation -> assertSnapshotBlocks(fixture, operation) }
        statement.close()
    }

    @Test
    fun `cursor holds exclusion through lazy reads until structured close`() {
        val fixture = fixture()
        val cursor = fixture.database.query("SELECT value FROM notes")
        try {
            assertTrue(cursor.moveToFirst())
            fixture.probeSnapshot().requireBlocked()
            assertEquals("retained", cursor.getString(0))
        } finally {
            cursor.close()
        }
        fixture.requireProbesCompleted()
    }

    @Test
    fun `snapshot excludes query creation and lazy cursor execution`() {
        val fixture = fixture()
        val database = fixture.database
        assertSnapshotBlocks(fixture) {
            database.query("SELECT value FROM notes").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("retained", cursor.getString(0))
            }
        }
    }

    @Test
    fun `all query overloads retain their cursor lease until close`() {
        val fixture = fixture()
        val database = fixture.database
        val query = mockk<SupportSQLiteQuery>(relaxed = true)
        val cancellation = mockk<CancellationSignal>()
        every { query.sql } returns "SELECT value FROM notes"
        listOf<() -> Cursor>(
            { database.query("SELECT value FROM notes") },
            { database.query("SELECT value FROM notes", emptyArray<Any>()) },
            { database.query(query) },
            { database.query(query, cancellation) },
        ).forEach { read ->
            val cursor = read()
            try {
                fixture.probeSnapshot().requireBlocked()
                assertTrue(cursor.moveToFirst())
            } finally {
                cursor.close()
            }
            fixture.requireProbesCompleted()
        }
    }

    @Test
    fun `double cursor close cannot release an outer transaction`() {
        val fixture = fixture()
        val database = fixture.database
        database.beginTransaction()
        try {
            val cursor = database.query("SELECT value FROM notes")
            cursor.close()
            cursor.close()
            fixture.probeSnapshot().requireBlocked()
        } finally {
            database.endTransaction()
        }
        fixture.requireProbesCompleted()
    }

    @Test
    fun `query creation failure releases its fence`() {
        val fixture = fixture()
        every { fixture.delegate.query(any<String>()) } throws IllegalStateException("query failed")
        assertFailsWith<IllegalStateException> { fixture.database.query("SELECT value FROM notes") }
        fixture.probeSnapshot().requireCompleted()
    }

    @Test
    fun `cursor close failure releases its fence`() {
        val fixture = fixture()
        every { fixture.cursor.close() } throws IllegalStateException("close failed")
        val cursor = fixture.database.query("SELECT value FROM notes")
        assertFailsWith<IllegalStateException> { cursor.close() }
        fixture.probeSnapshot().requireCompleted()
    }

    @Test
    fun `execSQL begin retains exclusion until commit or rollback`() {
        listOf("COMMIT", "ROLLBACK").forEach { ending ->
            val fixture = fixture()
            val database = fixture.database
            database.execSQL("BEGIN")
            try {
                fixture.probeSnapshot().requireBlocked()
            } finally {
                database.execSQL(ending)
            }
            fixture.requireProbesCompleted()
        }
    }

    @Test
    fun `compiled begin retains exclusion until compiled commit or rollback`() {
        listOf("COMMIT", "ROLLBACK").forEach { ending ->
            val fixture = fixture()
            val database = fixture.database
            database.compileStatement("BEGIN").use { it.execute() }
            try {
                fixture.probeSnapshot().requireBlocked()
            } finally {
                database.compileStatement(ending).use { it.execute() }
            }
            fixture.requireProbesCompleted()
        }
    }

    private fun transactionBoundary(begin: SupportSQLiteDatabase.() -> Unit) {
        val fixture = fixture()
        val database = fixture.database
        database.begin()
        try {
            fixture.probeSnapshot().requireBlocked()
        } finally {
            database.endTransaction()
        }
        fixture.requireProbesCompleted()
    }

    private fun assertSnapshotBlocks(
        fixture: Fixture,
        operation: () -> Unit,
    ) {
        val probe =
            withNativeJournalDatabaseFence(fixture.file) {
                fixture.probe(operation).also { it.requireBlocked() }
            }
        probe.requireCompleted()
    }

    private fun fixture(): Fixture = Fixture().also(fixtures::add)

    private class Fixture : AutoCloseable {
        val root = createTempDirectory("native-sql-fence").toFile()
        val file = File(root, "logdate")
        val delegate = mockk<SupportSQLiteDatabase>(relaxed = true)
        val cursor = mockk<Cursor>(relaxed = true)
        private val rawHelper = mockk<SupportSQLiteOpenHelper>(relaxed = true)
        private val rawFactory = mockk<SupportSQLiteOpenHelper.Factory>()
        private val probes = mutableListOf<Probe>()
        private val executor = Executors.newCachedThreadPool { task -> Thread(task, "native-sql-fence-test").apply { isDaemon = true } }
        val helper: SupportSQLiteOpenHelper
        val database: SupportSQLiteDatabase get() = helper.writableDatabase

        init {
            every { delegate.isOpen } returns true
            every { delegate.path } returns file.absolutePath
            every { cursor.moveToFirst() } returns true
            every { cursor.getString(0) } returns "retained"
            every { delegate.query(any<String>()) } returns cursor
            every { delegate.query(any<String>(), any()) } returns cursor
            every { delegate.query(any<SupportSQLiteQuery>()) } returns cursor
            every { delegate.query(any<SupportSQLiteQuery>(), any()) } returns cursor
            every { delegate.compileStatement(any()) } answers { mockk<SupportSQLiteStatement>(relaxed = true) }
            every { rawHelper.writableDatabase } returns delegate
            every { rawHelper.readableDatabase } returns delegate
            every { rawFactory.create(any()) } returns rawHelper
            helper = nativeJournalOpenHelperFactory(file, rawFactory).create(mockk(relaxed = true))
        }

        fun probe(action: () -> Unit): Probe = Probe(executor, action).also(probes::add)

        fun probeSnapshot(): Probe = probe { withNativeJournalDatabaseFence(file) {} }

        fun requireProbesCompleted() = probes.forEach { it.requireCompleted() }

        override fun close() {
            executor.shutdownNow()
            check(executor.awaitTermination(5, TimeUnit.SECONDS)) { "Fence test worker did not stop" }
            root.deleteRecursively()
        }
    }

    private class Probe(
        executor: java.util.concurrent.ExecutorService,
        action: () -> Unit,
    ) {
        private val started = CountDownLatch(1)
        private val completed = CountDownLatch(1)
        private val result =
            executor.submit(
                Callable {
                    started.countDown()
                    action()
                    completed.countDown()
                },
            )

        fun requireBlocked() {
            assertTrue(started.await(5, TimeUnit.SECONDS), "Fence contender never started")
            assertFalse(completed.await(150, TimeUnit.MILLISECONDS), "Snapshot and database operation overlapped")
        }

        fun requireCompleted() = result.get(5, TimeUnit.SECONDS)
    }
}
