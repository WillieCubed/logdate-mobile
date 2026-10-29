package app.logdate.client.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.database.dao.maintenance.IntegrityDao
import app.logdate.client.device.restore.NativeRestorePrivacyGuard
import app.logdate.client.device.restore.NativeRestorePrivacyMetadataRequiredException
import app.logdate.client.device.restore.PostRestoreDetector
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class NativeDatabasePrivacyPreflightTest {
    private val root = createTempDirectory("privacy-before-room").toFile()
    private val files = File(root, "files").apply { mkdirs() }
    private val privateFiles = File(root, "private").apply { mkdirs() }
    private val databases = File(root, "databases").apply { mkdirs() }
    private val context = mockk<Context>()
    private val preferences = MemoryStore()
    private val guard: NativeRestorePrivacyGuard
    private val key = ByteArray(32) { 7 }
    private val database = mockk<LogDateDatabase>(relaxed = true)
    private val integrity = mockk<IntegrityDao>()
    private val isolated = File(root, "owned-inspection").apply { mkdirs() }
    private var inspections = 0
    private var retainedRows = true
    private var inspectionFailure: Exception? = null
    private val inspect: (Context, Map<String, File>, List<ByteArray>, File) -> RetainedDatabaseCopy? =
        { actualContext, sources, passphrases, scratch ->
            inspections++
            assertEquals(context, actualContext)
            assertEquals(File(databases, "logdate"), sources["logdate"])
            assertContentEquals(key, passphrases.single())
            assertEquals(File(privateFiles, "native-privacy-inspection"), scratch)
            RetainedDatabaseCopy(database, isolated)
        }

    init {
        every { context.applicationContext } returns context
        every { context.filesDir } returns files
        every { context.noBackupFilesDir } returns privateFiles
        every { context.getDatabasePath(any()) } answers { File(databases, firstArg<String>()) }
        every { database.integrityDao() } returns integrity
        coEvery { integrity.hasRetainedJournalContent() } answers {
            inspectionFailure?.let { throw it }
            retainedRows
        }
        guard = NativeRestorePrivacyGuard(context, preferences)
    }

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    @Test
    fun `unknown privacy refuses retained rows before any canonical writable open`() =
        runTest {
            val original = "encrypted journal that must remain unchanged".encodeToByteArray()
            File(databases, "logdate").writeBytes(original)
            assertFailsWith<NativeRestorePrivacyMetadataRequiredException> {
                verifyNativePrivacyBeforeDatabaseOpen(context, listOf(key), guard, inspect)
            }
            assertEquals(1, inspections)
            assertContentEquals(original, File(databases, "logdate").readBytes())
            verify(exactly = 1) { database.close() }
            assertFalse(isolated.exists())
        }

    @Test
    fun `known privacy avoids copying or opening protected storage for inspection`() =
        runTest {
            File(databases, "logdate").writeText("encrypted journal")
            preferences.data.value = preferencesOf(stringPreferencesKey("security_level") to "BIOMETRIC")
            verifyNativePrivacyBeforeDatabaseOpen(context, listOf(key), guard, inspect)
            assertEquals(0, inspections)
        }

    @Test
    fun `fresh storage and credentials only need no database inspection`() =
        runTest {
            verifyNativePrivacyBeforeDatabaseOpen(context, listOf(key), guard, inspect)
            assertEquals(0, inspections)
        }

    @Test
    fun `a previously created but proven empty database can continue onboarding`() =
        runTest {
            File(databases, "logdate").writeText("valid encrypted empty database")
            retainedRows = false
            verifyNativePrivacyBeforeDatabaseOpen(context, listOf(key), guard, inspect)
            assertEquals(1, inspections)
            verify(exactly = 1) { database.close() }
        }

    @Test
    fun `retained drafts remain protected without a database`() =
        runTest {
            File(files, "datastore/entry_drafts.preferences_pb").apply {
                parentFile.mkdirs()
                writeText("owned draft fixture")
            }
            assertFailsWith<NativeRestorePrivacyMetadataRequiredException> {
                verifyNativePrivacyBeforeDatabaseOpen(context, listOf(key), guard, inspect)
            }
            assertEquals(0, inspections)
        }

    @Test
    fun `failed inspection never proves storage empty or changes the original`() =
        runTest {
            val original = "unreadable retained journal".encodeToByteArray()
            File(databases, "logdate").writeBytes(original)
            assertFailsWith<IllegalStateException> {
                verifyNativePrivacyBeforeDatabaseOpen(context, listOf(key), guard) { _, _, _, _ -> null }
            }
            assertContentEquals(original, File(databases, "logdate").readBytes())
        }

    @Test
    fun `query failure closes the copy and propagates without admitting content`() =
        runTest {
            File(databases, "logdate").writeText("encrypted journal")
            inspectionFailure = IllegalStateException("Unfamiliar protected schema")
            assertFailsWith<IllegalStateException> {
                verifyNativePrivacyBeforeDatabaseOpen(context, listOf(key), guard, inspect)
            }
            verify(exactly = 1) { database.close() }
            assertFalse(isolated.exists())
        }

    @Test
    fun `cancellation cannot be converted into an empty journal`() =
        runTest {
            File(databases, "logdate").writeText("encrypted journal")
            inspectionFailure = CancellationException("inspection cancelled")
            assertFailsWith<CancellationException> {
                verifyNativePrivacyBeforeDatabaseOpen(context, listOf(key), guard, inspect)
            }
            verify(exactly = 1) { database.close() }
        }

    @Test
    fun `unaccompanied journal sidecars cannot be treated as a fresh installation`() =
        runTest {
            File(databases, "logdate-wal").writeText("pending encrypted pages")
            assertFailsWith<IllegalStateException> {
                verifyNativePrivacyBeforeDatabaseOpen(context, listOf(key), guard, inspect)
            }
            assertEquals(0, inspections)
        }

    @Test
    fun `old unlocked preferences cannot admit a native journal with missing policy delivery`() =
        runTest {
            File(databases, "logdate").writeText("encrypted journal")
            preferences.data.value = preferencesOf(stringPreferencesKey("security_level") to "NONE")
            PostRestoreDetector(context).beginNativeRestoreSession()
            assertFailsWith<NativeRestorePrivacyMetadataRequiredException> {
                verifyNativePrivacyBeforeDatabaseOpen(context, listOf(key), guard, inspect)
            }
        }

    private class MemoryStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }
}
