package app.logdate.client.e2e

import android.os.Handler
import android.os.Looper
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.database.NativeJournalSnapshotScope
import app.logdate.client.database.captureNativeJournalDatabaseSnapshot
import app.logdate.client.database.entities.JournalEntity
import app.logdate.client.database.getDatabaseBuilder
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.database.verifyNativeJournalDatabaseSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class NativeJournalSnapshotContentionTest {
    @Test
    fun snapshotDoesNotFailNewRoomObservation() = exerciseLiveRoomContention(observeOnly = true)

    @Test
    fun snapshotSerializesNewRoomWriteWithoutChangingExport() = exerciseLiveRoomContention(observeOnly = false)

    private fun exerciseLiveRoomContention(observeOnly: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.filesDir, "snapshot-live-room-${Uuid.random()}").apply { check(mkdirs()) }
        val source = File(root, "source-logdate")
        val target = File(root, "transfer-logdate")
        val key = ByteArray(32) { (it + 2).toByte() }
        val room =
            getRoomDatabase(
                getDatabaseBuilder(context, key.copyOf(), source).setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING),
                driver = null,
            )
        val scope = NativeJournalSnapshotScope(Uuid.random().toString(), true, "https://logdate.app", "a".repeat(64))
        val now = Clock.System.now()
        val initial = JournalEntity(title = "Retained before backup", description = "", created = now, lastUpdated = now)
        val added = JournalEntity(title = "Accepted after backup", description = "", created = now, lastUpdated = now)
        val snapshotHeld = CountDownLatch(1)
        val releaseSnapshot = CountDownLatch(1)
        val enteredRoom = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            runBlocking { room.journalDao().create(initial) }
            assertTrue(room.openHelper.writableDatabase.isWriteAheadLoggingEnabled)
            assertWal(room)
            val export =
                executor.submit {
                    captureNativeJournalDatabaseSnapshot(source, key.copyOf(), target) { writeScope ->
                        snapshotHeld.countDown()
                        check(releaseSnapshot.await(15, TimeUnit.SECONDS)) { "Snapshot fixture was not released" }
                        writeScope(scope)
                    }
                }
            assertTrue(snapshotHeld.await(10, TimeUnit.SECONDS), "Production snapshot must hold its transaction")
            val operation =
                executor.submit {
                    enteredRoom.countDown()
                    runBlocking {
                        if (observeOnly) {
                            val observed = room.journalDao().observeAll().first()
                            assertEquals(listOf(initial.id), observed.map { it.id })
                            assertEquals(listOf(initial.title), observed.map { it.title })
                        } else {
                            room.journalDao().create(added)
                        }
                    }
                }
            assertTrue(enteredRoom.await(5, TimeUnit.SECONDS))
            // Exceed SQLCipher's competing-pool busy wait while the production snapshot owns SQL.
            Thread.sleep(6_000)
            if (operation.isDone) operation.get(1, TimeUnit.SECONDS)
            if (!observeOnly) assertFalse(operation.isDone, "Source writes must wait for the coherent snapshot boundary")
            releaseSnapshot.countDown()
            export.get(20, TimeUnit.SECONDS)
            operation.get(20, TimeUnit.SECONDS)
            assertTrue(room.openHelper.writableDatabase.isWriteAheadLoggingEnabled, "Backup must preserve the live WAL configuration")
            assertWal(room)
            assertEquals(scope, verifyNativeJournalDatabaseSnapshot(target, key))
            SQLiteDatabase.openDatabase(target.absolutePath, key, null, SQLiteDatabase.OPEN_READONLY, null, null).use {
                it.rawQuery("SELECT title FROM journals", emptyArray<String>()).use { rows ->
                    assertTrue(rows.moveToFirst())
                    assertEquals(initial.title, rows.getString(0))
                    assertFalse(rows.moveToNext(), "The coherent export must exclude the later Room write")
                }
            }
            runBlocking {
                val titles =
                    room
                        .journalDao()
                        .getAll()
                        .map { it.title }
                        .toSet()
                assertEquals(if (observeOnly) setOf(initial.title) else setOf(initial.title, added.title), titles)
            }
        } finally {
            releaseSnapshot.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            room.close()
            root.deleteRecursively()
            key.fill(0)
        }
    }

    @Test
    fun snapshotWaitsForSuspendingRoomTransactionCommit() = exerciseSuspendingTransaction(commit = true)

    @Test
    fun snapshotWaitsForSuspendingRoomTransactionRollback() = exerciseSuspendingTransaction(commit = false)

    private fun exerciseSuspendingTransaction(commit: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.filesDir, "snapshot-room-transaction-${Uuid.random()}").apply { check(mkdirs()) }
        val source = File(root, "source-logdate")
        val target = File(root, "transfer-logdate")
        val key = ByteArray(32) { (it + 3).toByte() }
        val room =
            getRoomDatabase(
                getDatabaseBuilder(context, key.copyOf(), source).setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING),
                driver = null,
            )
        val scope = NativeJournalSnapshotScope(Uuid.random().toString(), true, "https://logdate.app", "b".repeat(64))
        val now = Clock.System.now()
        val initial = JournalEntity(title = "Before transaction", description = "", created = now, lastUpdated = now)
        val added = JournalEntity(title = "Inside transaction", description = "", created = now, lastUpdated = now)
        val transactionHeld = CountDownLatch(1)
        val exportStarted = CountDownLatch(1)
        val resumeTransaction = CompletableDeferred<Unit>()
        val executor = Executors.newFixedThreadPool(2)
        try {
            runBlocking { room.journalDao().create(initial) }
            assertWal(room)
            val transaction =
                executor.submit {
                    runBlocking {
                        try {
                            room.withTransaction {
                                room.journalDao().create(added)
                                transactionHeld.countDown()
                                resumeTransaction.await()
                                delay(10)
                                assertEquals(
                                    setOf(initial.id, added.id),
                                    room
                                        .journalDao()
                                        .getAll()
                                        .map { it.id }
                                        .toSet(),
                                )
                                if (!commit) throw FixtureRollback()
                            }
                        } catch (expected: FixtureRollback) {
                            check(!commit)
                        }
                    }
                }
            assertTrue(transactionHeld.await(10, TimeUnit.SECONDS))
            val export =
                executor.submit {
                    exportStarted.countDown()
                    captureNativeJournalDatabaseSnapshot(source, key.copyOf(), target) { writeScope -> writeScope(scope) }
                }
            assertTrue(exportStarted.await(5, TimeUnit.SECONDS))
            Thread.sleep(250)
            assertFalse(export.isDone, "Backup must wait for the live Room transaction owner")
            resumeTransaction.complete(Unit)
            transaction.get(20, TimeUnit.SECONDS)
            export.get(20, TimeUnit.SECONDS)
            assertWal(room)
            assertEquals(scope, verifyNativeJournalDatabaseSnapshot(target, key))
            SQLiteDatabase.openDatabase(target.absolutePath, key, null, SQLiteDatabase.OPEN_READONLY, null, null).use { database ->
                database.rawQuery("SELECT title FROM journals", emptyArray<String>()).use { rows ->
                    val titles = buildSet { while (rows.moveToNext()) add(rows.getString(0)) }
                    assertEquals(if (commit) setOf(initial.title, added.title) else setOf(initial.title), titles)
                }
            }
        } finally {
            resumeTransaction.complete(Unit)
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            room.close()
            root.deleteRecursively()
            key.fill(0)
        }
    }

    @Test
    fun anOpenRoomOwnershipCheckDoesNotBlockMainBehindAnotherTransaction() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val root = File(context.filesDir, "snapshot-main-dispatch-${Uuid.random()}").apply { check(mkdirs()) }
            val source = File(root, "source-logdate")
            val key = ByteArray(32) { (it + 4).toByte() }
            val room = getRoomDatabase(getDatabaseBuilder(context, key.copyOf(), source), driver = null)
            val scopeJob = SupervisorJob()
            val scope = CoroutineScope(scopeJob + Dispatchers.Default)
            val held = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val mainEntered = CountDownLatch(1)
            val mainResponsive = CountDownLatch(1)
            val now = Clock.System.now()
            val note = JournalEntity(title = "Accepted transaction", description = "", created = now, lastUpdated = now)
            try {
                val owner =
                    scope.async {
                        room.withTransaction {
                            room.journalDao().create(note)
                            held.complete(Unit)
                            release.await()
                        }
                    }
                withTimeout(10_000) { held.await() }
                val reader =
                    scope.async(Dispatchers.Main) {
                        mainEntered.countDown()
                        room.journalDao().getAll()
                    }
                assertTrue(mainEntered.await(5, TimeUnit.SECONDS))
                Handler(Looper.getMainLooper()).post { mainResponsive.countDown() }
                assertTrue(mainResponsive.await(2, TimeUnit.SECONDS), "Room's pre-dispatch ownership check must not block Main")
                assertFalse(reader.isCompleted, "Actual SQL still waits for the transaction owner")
                release.complete(Unit)
                withTimeout(10_000) {
                    owner.await()
                    assertEquals(listOf(note.id), reader.await().map { it.id })
                }
            } finally {
                release.complete(Unit)
                withTimeout(10_000) { scopeJob.cancelAndJoin() }
                room.close()
                root.deleteRecursively()
                key.fill(0)
            }
        }

    private class FixtureRollback : RuntimeException()

    private fun assertWal(room: RoomDatabase) {
        room.openHelper.writableDatabase.query("PRAGMA journal_mode").use { rows ->
            assertTrue(rows.moveToFirst())
            assertEquals("wal", rows.getString(0).lowercase(), "Backup must retain the actual live WAL mode")
        }
    }

    @Test
    fun backupWaitsForWriterThenExportsConsistentSnapshot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.filesDir, "snapshot-contention-${Uuid.random()}").apply { check(mkdirs()) }
        val source = File(root, "source-logdate")
        val target = File(root, "transfer-logdate")
        val key = ByteArray(32) { (it + 1).toByte() }
        val room = getRoomDatabase(getDatabaseBuilder(context, key.copyOf(), source), driver = null)
        try {
            room.openHelper.writableDatabase
        } finally {
            room.close()
        }
        val owner = Uuid.random().toString()
        val preferencesHash =
            MessageDigest.getInstance("SHA-256").digest("fixture".encodeToByteArray()).joinToString("") {
                "%02x".format(it)
            }
        val writer =
            SQLiteDatabase.openDatabase(source.absolutePath, key, null, SQLiteDatabase.OPEN_READWRITE, null, null)
        val executor = Executors.newSingleThreadExecutor()
        try {
            writer.execSQL("CREATE TABLE backup_contention_fixture (id INTEGER PRIMARY KEY)")
            writer.beginTransaction()
            writer.execSQL("INSERT INTO backup_contention_fixture (id) VALUES (1)")
            val started = CountDownLatch(1)
            val export =
                executor.submit {
                    started.countDown()
                    captureNativeJournalDatabaseSnapshot(source, key.copyOf(), target) { writeScope ->
                        writeScope(NativeJournalSnapshotScope(owner, false, "https://logdate.app", preferencesHash))
                    }
                }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            Thread.sleep(6_000)
            assertFalse(export.isDone, "The snapshot must wait while a writer holds an uncommitted change")
            writer.endTransaction()
            export.get(20, TimeUnit.SECONDS)
            assertEquals(owner, verifyNativeJournalDatabaseSnapshot(target, key).ownerId)
            SQLiteDatabase.openDatabase(target.absolutePath, key, null, SQLiteDatabase.OPEN_READONLY, null, null).use {
                it.rawQuery("SELECT COUNT(*) FROM backup_contention_fixture", emptyArray<String>()).use { rows ->
                    assertTrue(rows.moveToFirst())
                    assertEquals(0, rows.getInt(0), "The export must omit the writer's uncommitted row")
                }
            }
        } finally {
            if (writer.inTransaction()) writer.endTransaction()
            writer.close()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
            root.deleteRecursively()
            key.fill(0)
        }
    }
}
