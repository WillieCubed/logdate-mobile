package app.logdate.client.e2e

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteException
import android.os.Build
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.database.encryption.DatabaseKeyRecoveryRequiredException
import app.logdate.client.database.encryption.DatabasePassphraseProvider
import app.logdate.client.database.encryption.PassphraseBackupStore
import app.logdate.client.device.storage.SecureStorage
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase

@RunWith(AndroidJUnit4::class)
class NativeRetainedKeyFailureTest {
    @Test
    fun missingRecoveryMaterialPreservesTheRetainedJournal() =
        withFixture {
            assertFailsWith<DatabaseKeyRecoveryRequiredException> { provider().getPassphraseCandidates() }
            assertEquals(0, storage.writes)
            assertFalse(backup.exists())
            verifyJournal()
        }

    @Test
    fun malformedAndTruncatedMaterialRemainsAvailableForRecovery() {
        for (retained in listOf("not-valid-base64!", Base64.encode(ByteArray(7) { 3 }))) {
            withFixture {
                backup.parentFile?.mkdirs()
                backup.writeText(retained)
                assertFailsWith<DatabaseKeyRecoveryRequiredException> { provider().getPassphraseCandidates() }
                assertTrue(backup.readText() == retained, "Invalid recovery bytes must remain unchanged")
                assertEquals(0, storage.writes)
                verifyJournal()
            }
        }
    }

    @Test
    fun wrongLengthPrimaryRecoversFromThePortableKey() =
        withFixture {
            store.write(originalKey)
            storage.primary = Base64.encode(ByteArray(7) { 4 })
            val provider = provider()
            val candidate = provider.getPassphraseCandidates().single()
            assertEquals(0, storage.writes)
            openJournal(candidate)
            provider.confirmOpenedDatabasePassphrase(candidate)
            assertTrue(storage.primary == Base64.encode(originalKey), "The accepted key must be committed")
            verifyJournal()
        }

    @Test
    fun unreadablePortableFileCannotReplaceTheJournalOrKey() =
        withFixture {
            store.write(originalKey)
            val retained = backup.readBytes()
            Os.chmod(backup.absolutePath, 0)
            try {
                assertFalse(backup.canRead(), "This case must exercise actual Android file permission denial")
                assertFailsWith<DatabaseKeyRecoveryRequiredException> { provider().getPassphraseCandidates() }
                assertEquals(0, storage.writes)
                assertTrue(backup.exists())
            } finally {
                Os.chmod(backup.absolutePath, 0x180)
            }
            assertTrue(backup.readBytes().contentEquals(retained), "Unreadable recovery material must remain unchanged")
            verifyJournal()
        }

    @Test
    fun mismatchedCandidatesDoNotOverwriteRetainedRecoveryMaterial() =
        withFixture {
            storage.primary = Base64.encode(ByteArray(32) { 4 })
            store.write(ByteArray(32) { 5 })
            val retained = backup.readBytes()
            val candidates = provider().getPassphraseCandidates()
            assertEquals(2, candidates.size)
            candidates.forEach { candidate -> assertFailsWith<SQLiteException> { openJournal(candidate) } }
            assertEquals(0, storage.writes)
            assertTrue(backup.readBytes().contentEquals(retained), "Rejected keys must not overwrite portable material")
            assertTrue(storage.primary == Base64.encode(ByteArray(32) { 4 }), "Rejected primary must remain unchanged")
            verifyJournal()
        }

    @Test
    fun unreadablePrimaryRecoversWithoutLeakingItsException() =
        withFixture {
            store.write(originalKey)
            val privateDetail = "private-storage-error-${Base64.encode(originalKey)}"
            storage.readFailure = IOException(privateDetail)
            val messages = mutableListOf<String>()
            val sink =
                object : Antilog() {
                    override fun performLog(
                        priority: LogLevel,
                        tag: String?,
                        throwable: Throwable?,
                        message: String?,
                    ) {
                        messages += message.orEmpty() + throwable?.stackTraceToString().orEmpty()
                    }
                }
            Napier.base(sink)
            try {
                val candidate = provider().getPassphraseCandidates().single()
                openJournal(candidate)
                assertTrue(messages.isNotEmpty(), "The read-failure diagnostic must run")
                assertFalse(messages.joinToString().contains(privateDetail), "Storage exception detail must stay private")
                assertFalse(messages.joinToString().contains(Base64.encode(originalKey)), "Raw keys must stay private")
                assertEquals(0, storage.writes)
                verifyJournal()
            } finally {
                Napier.takeLogarithm(sink)
            }
        }

    @Test
    fun failedPrimaryConfirmationRetainsTheAcceptedKeyAcrossProviderRestart() =
        withFixture {
            storage.primary = Base64.encode(ByteArray(32) { 4 })
            store.write(originalKey)
            val provider = provider()
            val accepted = provider.getPassphraseCandidates().last()
            openJournal(accepted)
            storage.writeFailure = IOException("Synthetic interrupted primary commit")
            assertFailsWith<IOException> { provider.confirmOpenedDatabasePassphrase(accepted) }
            storage.writeFailure = null
            val restarted = provider()
            val candidates = restarted.getPassphraseCandidates()
            assertEquals(2, candidates.size)
            openJournal(candidates.last())
            restarted.confirmOpenedDatabasePassphrase(candidates.last())
            assertTrue(storage.primary == Base64.encode(originalKey), "Restart must commit the accepted retained key")
            verifyJournal()
        }

    private fun withFixture(block: suspend Fixture.() -> Unit) =
        runBlocking {
            val fixture = Fixture(requireFixtureContext())
            try {
                fixture.block()
            } finally {
                fixture.close()
            }
        }

    fun verifyInterruptedKeyAcrossAppRestart() =
        runBlocking {
            val args = InstrumentationRegistry.getArguments()
            assumeTrue(args.getString("keyRestartStage") != null)
            val stage = requireNotNull(args.getString("keyRestartStage"))
            require(stage == "seed" || stage == "verify")
            val id = Uuid.parse(requireNotNull(args.getString("keyRestartId"))).toString()
            val fixture = Fixture(requireFixtureContext(), id, reopening = stage == "verify")
            try {
                if (stage == "seed") fixture.seedInterruptedKey() else fixture.verifyInterruptedKey()
            } finally {
                if (stage == "verify") fixture.close()
            }
        }

    private fun requireFixtureContext(): Context {
        assumeTrue(InstrumentationRegistry.getArguments().getString("retainedKeyFailures") == "true")
        require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "Emulator required" }
        require(Os.getuid() != 0) { "Permission-denial acceptance requires an unprivileged app process" }
        return InstrumentationRegistry.getInstrumentation().targetContext.also {
            require(it.packageName.endsWith(".debug")) { "Synthetic debug app required" }
        }
    }

    private class Fixture(
        application: Context,
        id: String = Uuid.random().toString(),
        reopening: Boolean = false,
    ) {
        private val root = application.createDeviceProtectedStorageContext().getDir("native-key-failure-fixtures", Context.MODE_PRIVATE)
        private val folder = File(root, "retained-key-failure-$id").apply { check(if (reopening) isDirectory else mkdirs()) }
        private val context =
            object : ContextWrapper(application) {
                override fun getApplicationContext(): Context = this

                override fun getFilesDir(): File = folder

                override fun getNoBackupFilesDir(): File = folder.resolve("private").apply { mkdirs() }

                override fun getDatabasePath(name: String): File = folder.resolve(name)
            }
        val storage = FaultingStorage()
        val originalKey = ByteArray(32) { (it + 11).toByte() }
        val store = PassphraseBackupStore(context)
        val backup = folder.resolve("db_recovery/passphrase_backup")
        private val database = context.getDatabasePath("retained-journal")
        private val media = folder.resolve("retained-original.media")
        private val originalDatabase: ByteArray

        init {
            System.loadLibrary("sqlcipher")
            val snapshot = folder.resolve("encrypted-journal-snapshot")
            if (!reopening) {
                createJournal()
                media.writeText("Retained original media")
                snapshot.writeBytes(database.readBytes())
            }
            originalDatabase = snapshot.readBytes()
        }

        private fun createJournal() {
            CipherDatabase
                .openDatabase(
                    database.absolutePath,
                    originalKey,
                    null,
                    CipherDatabase.OPEN_READWRITE or CipherDatabase.CREATE_IF_NECESSARY,
                    null,
                    null,
                ).use {
                    it.execSQL("CREATE TABLE retained_entries (content TEXT NOT NULL)")
                    it.execSQL("INSERT INTO retained_entries VALUES ('Journal retained through recovery failure')")
                }
        }

        fun provider() = DatabasePassphraseProvider(storage, PassphraseBackupStore(context)) { database.exists() }

        fun openJournal(key: ByteArray) {
            CipherDatabase.openDatabase(database.absolutePath, key, null, CipherDatabase.OPEN_READONLY, null, null).use {
                it.rawQuery("SELECT content FROM retained_entries", emptyArray<String>()).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Journal retained through recovery failure", cursor.getString(0))
                }
                it.rawQuery("PRAGMA quick_check", emptyArray<String>()).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("ok", cursor.getString(0))
                }
            }
        }

        fun verifyJournal() {
            assertTrue(database.readBytes().contentEquals(originalDatabase), "Encrypted journal bytes must be retained")
            assertEquals("Retained original media", media.readText())
            openJournal(originalKey)
        }

        fun seedInterruptedKey() {
            store.write(originalKey)
            check(backup.renameTo(folder.resolve("db_recovery/passphrase_backup.part")))
            backup.writeText(Base64.encode(ByteArray(32) { 5 }))
            folder.resolve("seed.pid").writeText(
                android.os.Process
                    .myPid()
                    .toString(),
            )
            verifyJournal()
        }

        suspend fun verifyInterruptedKey() {
            val previousPid = folder.resolve("seed.pid").readText().toInt()
            assertTrue(previousPid != android.os.Process.myPid(), "Verification must run in another Android app process")
            val pending = folder.resolve("db_recovery/passphrase_backup.part")
            assertTrue(pending.isFile, "The interrupted key must survive process death")
            storage.primary = Base64.encode(ByteArray(32) { 4 })
            val provider = provider()
            val candidates = provider.getPassphraseCandidates()
            assertEquals(3, candidates.size)
            val accepted =
                candidates.indexOfFirst {
                    try {
                        openJournal(it)
                        true
                    } catch (_: SQLiteException) {
                        false
                    }
                }
            assertEquals(2, accepted, "Only the pending key may open the retained journal")
            provider.confirmOpenedDatabasePassphrase(candidates[accepted])
            assertFalse(pending.exists())
            assertTrue(store.read()?.contentEquals(originalKey) == true, "The surviving key must be committed")
            verifyJournal()
        }

        fun close() {
            if (backup.exists()) Os.chmod(backup.absolutePath, 0x180)
            originalKey.fill(0)
            check(folder.deleteRecursively())
        }
    }

    private class FaultingStorage : SecureStorage {
        var primary: String? = null
        var readFailure: IOException? = null
        var writeFailure: IOException? = null
        var writes = 0

        override suspend fun getString(key: String): String? {
            readFailure?.let { throw it }
            return primary
        }

        override suspend fun putString(
            key: String,
            value: String,
        ) {
            writes++
            writeFailure?.let { throw it }
            primary = value
        }

        override suspend fun remove(key: String) = error("Key removal is forbidden during recovery")

        override suspend fun clear() = error("Storage reset is forbidden during recovery")

        override fun observeString(key: String): Flow<String?> = flowOf(primary)

        override fun observeAll(): Flow<Map<String, String>> = flowOf(emptyMap())

        override suspend fun encrypt(data: ByteArray): ByteArray = error("Unused by key lookup")

        override suspend fun decrypt(data: ByteArray): ByteArray? = error("Unused by key lookup")
    }
}

@RunWith(AndroidJUnit4::class)
class NativeKeyPersistenceRestartProbeTest {
    @Test
    fun pendingKeySurvivesAppProcessDeath() = NativeRetainedKeyFailureTest().verifyInterruptedKeyAcrossAppRestart()
}
