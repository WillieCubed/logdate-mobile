package app.logdate.client.data.restore

import android.app.Application
import android.content.ContextWrapper
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import app.logdate.client.device.restore.NATIVE_JOURNAL_TRANSFER_PREFIX
import app.logdate.client.device.restore.NATIVE_MEDIA_INVENTORY_PATH
import app.logdate.client.device.restore.NATIVE_SOURCE_RECEIPT_PATH
import app.logdate.client.device.restore.NativeIncomingJournal
import app.logdate.client.device.restore.NativeJournalMediaInventory
import app.logdate.client.device.restore.NativeJournalReplayCustody
import app.logdate.client.device.restore.NativeJournalRestoreAuthority
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.client.device.restore.PostRestoreType
import app.logdate.client.device.restore.verifiedNativeJournalSources
import app.logdate.client.device.restore.verifyNativeJournalSourceReceipt
import app.logdate.client.device.restore.writeNativeJournalSourceReceipt
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Native SQLCipher validation is the boundary double; installation, state, files, and cleanup are real. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NativeJournalTransferRestoreTest {
    @Test
    fun `missing selected key leaves the journal encrypted and completes as missing content`() =
        fixture { f ->
            assertTrue(f.key.delete())
            val authority = NativeJournalRestoreAuthority(f.context)
            authority.requireSnapshot()
            val restore = f.restore { throw MissingNativeJournalTransferKeyException() }

            assertFalse(restore.restore())
            assertFalse(f.database.exists())
            assertTrue(f.current.isDirectory)

            val detector = PostRestoreDetector(f.context)
            detector.recordRestoreCompleted()
            assertEquals(PostRestoreType.CONTENT_MISSING, detector.detect(isOnboarded = true))
            restore.finish()
            f.restore { throw MissingNativeJournalTransferKeyException() }.finish()
            assertTrue(f.current.isDirectory)
            assertTrue(authority.isSnapshotRequired())
        }

    @Test
    fun `mismatched encrypted snapshot remains rejected without completing delivery`() =
        fixture { f ->
            NativeJournalRestoreAuthority(f.context).requireSnapshot()
            val restore =
                f.restore {
                    throw IllegalStateException("Encrypted source scope does not match native preferences")
                }

            assertFailsWith<IllegalStateException> { restore.restore() }
            assertEquals(PostRestoreType.RECOVERY_REQUIRED, PostRestoreDetector(f.context).detect(isOnboarded = true))
            assertTrue(f.current.isDirectory)
            assertFalse(f.database.exists())
        }

    @Test
    fun `clean install accepts either source key name and raw legacy DB delivered afterward`() {
        for (keyName in listOf("passphrase_backup", "working_passphrase_backup")) {
            fixture(keyName) { f ->
                // The vacancy was durably captured before these framework-delivered raw bytes.
                NativeJournalRestoreAuthority(f.context).requireFrameworkDatabaseWrite(f.database)
                f.database.writeText("torn framework database")
                NativeJournalRestoreAuthority(f.context).recordFrameworkDatabaseWrite(f.database)
                assertTrue(f.restore().restore())
                assertEquals("immutable sender database", f.database.readText())
                assertEquals("snapshot prefs", File(f.files, "logdate.preferences_pb").readText())
                assertEquals("accepted portable key", File(f.files, "db_recovery/passphrase_backup").readText())
            }
        }
    }

    @Test
    fun `raw database and WAL callbacks remain valid when each exact revision is recorded`() =
        fixture { f ->
            val authority = NativeJournalRestoreAuthority(f.context)
            val wal = File(f.database.path + "-wal")
            authority.requireFrameworkDatabaseWrite(f.database)
            f.database.writeText("framework database")
            authority.recordFrameworkDatabaseWrite(f.database)
            authority.requireFrameworkDatabaseWrite(wal)
            wal.writeText("framework WAL")
            authority.recordFrameworkDatabaseWrite(wal)
            assertTrue(f.restore().restore())
            assertEquals("immutable sender database", f.database.readText())
            assertFalse(wal.exists())
        }

    @Test
    fun `local database or WAL created after vacancy stays intact while source moves to custody`() {
        for (suffix in listOf("", "-wal")) {
            fixture { f ->
                val local = File(f.database.path + suffix).apply { writeText("local edit after vacancy") }
                assertFalse(f.restore().restore())
                assertEquals("local edit after vacancy", local.readText())
                f.assertStaged()
                assertFalse(PostRestoreDetector(f.context).isNativeRestoreDeliveryComplete())
            }
        }
    }

    @Test
    fun `preexisting preferences or key make a database-free destination occupied`() {
        for (path in listOf("logdate.preferences_pb", "db_recovery/passphrase_backup")) {
            fixture { f ->
                val local =
                    File(f.files, path).apply {
                        checkNotNull(parentFile).mkdirs()
                        writeText("accepted local identity and settings")
                    }
                val authority = NativeJournalRestoreAuthority(f.context)
                authority.begin(resumeInterrupted = false)
                assertTrue(authority.requiresCustodyForIncoming())
                assertFalse(f.restore().restore())
                assertEquals("accepted local identity and settings", local.readText())
                f.assertStaged()
            }
        }
    }

    @Test
    fun `local preference edit after a framework callback redirects later files to custody`() =
        fixture { f ->
            val authority = NativeJournalRestoreAuthority(f.context)
            val preferences = File(f.files, "logdate.preferences_pb")
            preferences.writeText("framework preferences")
            authority.recordFrameworkPortableState()
            preferences.writeText("new local preference edit")
            assertTrue(authority.requiresCustodyForIncoming())
            assertEquals("new local preference edit", preferences.readText())
        }

    @Test
    fun `local WAL edit after a raw DB callback prevents the next framework write`() =
        fixture { f ->
            val authority = NativeJournalRestoreAuthority(f.context)
            authority.requireFrameworkDatabaseWrite(f.database)
            f.database.writeText("framework database")
            authority.recordFrameworkDatabaseWrite(f.database)
            val wal = File(f.database.path + "-wal").apply { writeText("local WAL edit") }
            assertFailsWith<IllegalStateException> { authority.requireFrameworkDatabaseWrite(wal) }
            assertFalse(f.restore().restore())
            assertEquals("framework database", f.database.readText())
            assertEquals("local WAL edit", wal.readText())
            f.assertStaged()
        }

    @Test
    fun `completed snapshot retains verified source owner and origin`() =
        fixture { f ->
            assertTrue(f.restore().restore())
            val authority = NativeJournalRestoreAuthority(f.context)
            val receipt = checkNotNull(authority.installedReceipt())
            authority.rememberCompletedSnapshot(receipt, f.payloads())
            assertEquals("347c6231-19f4-49fa-bd1d-58ed34b9be03", authority.completedSourceReceipt()?.canonicalOwnerId)
            assertEquals("https://cloud.example", authority.completedSourceReceipt()?.backendOrigin)
        }

    @Test
    fun `preexisting destination sidecar without a main database is occupied and preserved`() {
        for (suffix in listOf("-wal", "-shm", "-journal")) {
            fixture { f ->
                val sidecar = File(f.database.path + suffix).apply { writeText("retained destination $suffix") }
                NativeJournalRestoreAuthority(f.context).begin(resumeInterrupted = false)
                assertFalse(f.restore().restore())
                assertFalse(f.database.exists())
                assertEquals("retained destination $suffix", sidecar.readText())
                f.assertStaged()
            }
        }
    }

    @Test
    fun `process death after atomic DB replacement resumes identical bytes`() =
        fixture { f ->
            assertTrue(f.restore().restore())
            assertTrue(f.restore().restore())
            assertEquals("immutable sender database", f.database.readText())
        }

    @Test
    fun `edited DB after installation begins is never overwritten by retry`() =
        fixture { f ->
            assertTrue(f.restore().restore())
            f.database.writeText("new user entry after interruption")
            assertFalse(f.restore().restore())
            assertEquals("new user entry after interruption", f.database.readText())
            f.assertStaged()
            assertFalse(PostRestoreDetector(f.context).isNativeRestoreDeliveryComplete())
        }

    @Test
    fun `WAL-only edit after installation is preserved even while main DB bytes are identical`() =
        fixture { f ->
            assertTrue(f.restore().restore())
            val wal = File(f.database.path + "-wal").apply { writeText("new committed WAL-only edit") }
            assertFalse(f.restore().restore())
            assertEquals("new committed WAL-only edit", wal.readText())
            assertEquals("immutable sender database", f.database.readText())
            f.assertStaged()
        }

    @Test
    fun `preference edit after installation is preserved on retry`() =
        fixture { f ->
            assertTrue(f.restore().restore())
            val preferences = File(f.files, "logdate.preferences_pb").apply { writeText("new local settings") }
            assertFalse(f.restore().restore())
            assertEquals("new local settings", preferences.readText())
            assertEquals("immutable sender database", f.database.readText())
            f.assertStaged()
        }

    @Test
    fun `missing receipt fails without completion or removing incoming custody`() =
        fixture { f ->
            check(File(f.current, NATIVE_SOURCE_RECEIPT_PATH).delete())
            assertFailsWith<IllegalStateException> { f.restore().restore() }
            assertFalse(f.database.exists())
            assertTrue(f.current.isDirectory)
            assertFalse(PostRestoreDetector(f.context).isNativeRestoreDeliveryComplete())
        }

    @Test
    fun `occupied local journal refuses otherwise valid incoming install`() =
        fixture { f ->
            f.database.writeText("existing local journal")
            NativeJournalRestoreAuthority(f.context).begin(resumeInterrupted = false)
            assertFalse(f.restore().restore())
            assertEquals("existing local journal", f.database.readText())
            f.assertStaged()
        }

    @Test
    fun `staged source survives a new restore instance and completion without changing local data`() =
        fixture { f ->
            f.database.writeText("accepted local journal")
            NativeJournalRestoreAuthority(f.context).begin(resumeInterrupted = false)
            assertFalse(f.restore().restore())
            f.assertStaged()
            assertFalse(f.restore().restore())
            assertEquals("accepted local journal", f.database.readText())
            f.complete()
            f.restore().finish()
            assertEquals("accepted local journal", f.database.readText())
            assertTrue(NativeJournalReplayCustody(f.context).hasInputs())
        }

    @Test
    fun `interrupted staged copy replaces and retires only its owned partial cohort`() {
        for (sealed in listOf(false, true)) {
            fixture { f ->
                val files = f.payloads()
                val hashes = files.mapValues { nativeSnapshotChecksum(it.value) }
                val receipt = hashes.getValue(NATIVE_SOURCE_RECEIPT_PATH)
                val cohorts = hashes.mapValues { Uuid.random().toString() }
                File(f.noBackup, "native-journal-transfer-staged").writeText(
                    """{"receipt":${Json.encodeToString(
                        receipt,
                    )},"files":${Json.encodeToString(hashes)},"cohorts":${Json.encodeToString(cohorts)}}""",
                )
                val source = files.getValue("database/logdate")
                val oldId = cohorts.getValue("database/logdate")
                val custody = NativeJournalReplayCustody(f.context)
                assertFailsWith<IOException> {
                    custody.captureInCohort(
                        oldId,
                        NATIVE_JOURNAL_TRANSFER_PREFIX + "database/logdate",
                        source.length(),
                        source.readBytes().copyOfRange(0, 1).inputStream(),
                    )
                }
                if (sealed) custody.sealedPayloadsInCohort(oldId)
                f.database.writeText("accepted local journal")
                NativeJournalRestoreAuthority(f.context).begin(resumeInterrupted = false)

                assertFalse(f.restore().restore())

                assertEquals("accepted local journal", f.database.readText())
                assertFalse(custody.hasCohort(oldId))
                f.assertStaged()
            }
        }
    }

    @Test
    fun `staged source retains exact scope and auxiliary hashes after source files are removed`() =
        fixture { f ->
            val receipt = nativeSnapshotChecksum(f.payloads().getValue(NATIVE_SOURCE_RECEIPT_PATH))
            f.database.writeText("accepted local journal")
            NativeJournalRestoreAuthority(f.context).begin(resumeInterrupted = false)
            assertFalse(f.restore().restore())
            f.assertStaged()
            val index = NativeJournalStagedSourceIndex(f.context)
            assertTrue(index.hasRemembered(receipt))
            assertTrue(index.admittedSources().isEmpty())
            val callbacks = NativeJournalReplayCustody(f.context).sealedPayloads()

            index.admitAfterMerged(receipt)

            val source = index.admittedSources().getValue(receipt)
            assertEquals("347c6231-19f4-49fa-bd1d-58ed34b9be03", source.receipt.canonicalOwnerId)
            assertEquals("https://cloud.example", source.receipt.backendOrigin)
            assertEquals(null, source.mediaInventory)
            assertEquals(
                setOf(callbacks.single { it.path.endsWith("passphrase_backup") }.file.canonicalPath),
                index.verifiedAuxiliary(receipt, callbacks).keys,
            )
            assertEquals(
                NATIVE_JOURNAL_TRANSFER_PREFIX + "files/db_recovery/passphrase_backup",
                callbacks.single { it.file == index.verifiedAuxiliary(receipt, callbacks).values.single() }.path,
            )
            val rawKey = File(f.root, "raw-late-key").apply { writeText("accepted portable key") }
            assertEquals(
                emptySet(),
                index
                    .verifiedAuxiliary(
                        receipt,
                        listOf(NativeIncomingJournal.Payload("files/db_recovery/passphrase_backup", rawKey, true, rawKey.length())),
                    ).keys,
            )
            val future = NativeJournalReplayCustody(f.context)
            rawKey.inputStream().use {
                future.capture(NATIVE_JOURNAL_TRANSFER_PREFIX + "files/db_recovery/passphrase_backup", rawKey.length(), it)
            }
            val oldKeyPath = callbacks.single { it.path.endsWith("passphrase_backup") }.file.canonicalPath
            val laterKey =
                future.sealedPayloads().single {
                    it.path == NATIVE_JOURNAL_TRANSFER_PREFIX + "files/db_recovery/passphrase_backup" && it.file.canonicalPath != oldKeyPath
                }
            assertEquals(emptyMap(), index.verifiedAuxiliary(receipt, listOf(laterKey)))
        }

    @Test
    fun `identical receipt in a new callback cohort retires both exact key revisions`() =
        fixture { f ->
            val receipt = nativeSnapshotChecksum(f.payloads().getValue(NATIVE_SOURCE_RECEIPT_PATH))
            f.database.writeText("accepted local journal")
            NativeJournalRestoreAuthority(f.context).begin(resumeInterrupted = false)
            assertFalse(f.restore().restore())
            val custody = NativeJournalReplayCustody(f.context)
            val originals = custody.sealedPayloads()
            val sourceFiles = originals.associate { it.path.removePrefix(NATIVE_JOURNAL_TRANSFER_PREFIX) to it.file }
            val callback = NativeJournalReplayCustody(f.context)
            sourceFiles.forEach { (path, file) ->
                file.inputStream().use { callback.capture(NATIVE_JOURNAL_TRANSFER_PREFIX + path, file.length(), it) }
            }
            val oldPaths = originals.mapTo(mutableSetOf()) { it.file.canonicalPath }
            val repeated = callback.sealedPayloads().filter { it.file.canonicalPath !in oldPaths }
            val repeatedFiles = repeated.associate { it.path.removePrefix(NATIVE_JOURNAL_TRANSFER_PREFIX) to it.file }
            val index = NativeJournalStagedSourceIndex(f.context)
            index.admitAfterMerged(receipt)

            index.remember(repeatedFiles)

            val keys =
                index.verifiedAuxiliary(receipt, callback.sealedPayloads()).values.filter {
                    it.readText() == "accepted portable key"
                }
            assertEquals(2, keys.size)
            val accepted = verifiedNativeJournalSources(callback.sealedPayloads()).values.single()
            val admitted = index.admittedSources().getValue(receipt)
            callback.acknowledgeMergedSource(receipt, accepted, admitted.sourceCorePhysicalPaths)
            val verified = index.verifiedAuxiliary(receipt, callback.sealedPayloads()).values.mapTo(mutableSetOf()) { it.canonicalPath }
            callback.acknowledgeStagedAuxiliary(verified, admitted.pathSha256)
            assertFalse(callback.hasInputs())
        }

    @Test
    fun `staged v3 source retains its verified media inventory after source files are removed`() =
        fixture(withInventory = true) { f ->
            val receipt = nativeSnapshotChecksum(f.payloads().getValue(NATIVE_SOURCE_RECEIPT_PATH))
            f.database.writeText("accepted local journal")
            NativeJournalRestoreAuthority(f.context).begin(resumeInterrupted = false)
            assertFalse(f.restore().restore())

            val index = NativeJournalStagedSourceIndex(f.context)
            index.admitAfterMerged(receipt)

            val source = index.admittedSources().getValue(receipt)
            assertEquals(3, source.receipt.version)
            assertEquals(emptyList(), source.mediaInventory?.files)
            val inventoryPayload =
                NativeJournalReplayCustody(f.context).sealedPayloads().single { it.path.endsWith(NATIVE_MEDIA_INVENTORY_PATH) }
            assertTrue(inventoryPayload.file.canonicalPath in index.verifiedAuxiliary(receipt, listOf(inventoryPayload)))
        }

    @Test
    fun `late v3 media inventory can join an already staged and admitted source after restart`() =
        fixture(withInventory = true) { f ->
            val receipt = nativeSnapshotChecksum(f.payloads().getValue(NATIVE_SOURCE_RECEIPT_PATH))
            val lateInventory =
                File(f.root, "late-inventory").apply {
                    writeBytes(f.payloads().getValue(NATIVE_MEDIA_INVENTORY_PATH).readBytes())
                }
            check(File(f.current, NATIVE_MEDIA_INVENTORY_PATH).delete())
            f.database.writeText("accepted local journal")
            NativeJournalRestoreAuthority(f.context).begin(resumeInterrupted = false)
            assertFalse(f.restore().restore())
            val index = NativeJournalStagedSourceIndex(f.context)
            index.admitAfterMerged(receipt)
            assertEquals(null, index.admittedSources().getValue(receipt).mediaInventory)
            val wrong = File(f.root, "wrong-inventory").apply { writeText("wrong") }
            assertFailsWith<IllegalStateException> { index.retainMediaInventory(receipt, wrong) }

            NativeJournalStagedSourceIndex(f.context).retainMediaInventory(receipt, lateInventory)

            val restarted = NativeJournalStagedSourceIndex(f.context)
            assertEquals(
                emptyList(),
                restarted
                    .admittedSources()
                    .getValue(receipt)
                    .mediaInventory
                    ?.files,
            )
            assertEquals(
                setOf(lateInventory.canonicalPath),
                restarted
                    .verifiedAuxiliary(
                        receipt,
                        listOf(
                            NativeIncomingJournal.Payload(
                                NATIVE_JOURNAL_TRANSFER_PREFIX + NATIVE_MEDIA_INVENTORY_PATH,
                                lateInventory,
                                true,
                                lateInventory.length(),
                            ),
                        ),
                    ).keys,
            )
            assertEquals(
                setOf(lateInventory.canonicalPath),
                restarted
                    .verifiedAuxiliary(
                        receipt,
                        listOf(NativeIncomingJournal.Payload(NATIVE_MEDIA_INVENTORY_PATH, lateInventory, true, lateInventory.length())),
                    ).keys,
            )
        }

    @Test
    fun `consumed rename survives missing receipt and exact replay preserves subsequent edit`() =
        fixture { f ->
            assertTrue(f.restore().restore())
            f.complete()
            val authority = NativeJournalRestoreAuthority(f.context)
            val receipt = checkNotNull(authority.installedReceipt())
            val original = f.payloads().mapValues { it.value.readBytes() }
            authority.rememberCompletedSnapshot(receipt, f.payloads())
            authority.beginCleanup(receipt)
            val consumed = File(f.current.parentFile, ".$receipt.consumed")
            Files.move(f.current.toPath(), consumed.toPath(), StandardCopyOption.ATOMIC_MOVE)
            check(File(consumed, NATIVE_SOURCE_RECEIPT_PATH).delete())
            f.restore().finish()
            assertFalse(consumed.exists())
            f.database.writeText("new user edit after completed transfer")
            original.forEach { (path, bytes) ->
                File(f.current, path).apply {
                    checkNotNull(parentFile).mkdirs()
                    writeBytes(bytes)
                }
            }
            PostRestoreDetector(f.context).beginNativeRestoreSession()
            authority.begin(resumeInterrupted = false)
            assertTrue(f.restore().restore())
            assertEquals("new user edit after completed transfer", f.database.readText())
            f.complete()
            f.restore().finish()
            assertFalse(f.current.exists())
        }

    @Test
    fun `empty key-only and unknown partial callbacks are never old replay authority`() =
        fixture { f ->
            assertTrue(f.restore().restore())
            f.complete()
            val authority = NativeJournalRestoreAuthority(f.context)
            authority.rememberCompletedSnapshot(checkNotNull(authority.installedReceipt()), f.payloads())
            assertFalse(authority.matchesCompletedSnapshot(emptyMap()))
            assertFalse(authority.matchesCompletedSnapshot(mapOf("files/db_recovery/passphrase_backup" to f.key)))
            val unknown = File(f.root, "unknown").apply { writeText("new original media") }
            assertFalse(authority.matchesCompletedSnapshot(f.payloads() + ("files/media/new.png" to unknown)))
        }

    private fun fixture(
        keyName: String = "passphrase_backup",
        withInventory: Boolean = false,
        block: (Fixture) -> Unit,
    ) {
        Fixture(keyName, withInventory).use(block)
    }

    private class Fixture(
        keyName: String,
        withInventory: Boolean,
    ) : AutoCloseable {
        val root = Files.createTempDirectory("native-journal-install-").toFile()
        val files = File(root, "files").apply { mkdirs() }
        val noBackup = File(root, "no-backup").apply { mkdirs() }
        val database = File(root, "database/logdate").apply { checkNotNull(parentFile).mkdirs() }
        val current = File(files, "native_journal_transfer/current")
        val context =
            object : ContextWrapper(ApplicationProvider.getApplicationContext<Application>()) {
                override fun getFilesDir() = this@Fixture.files

                override fun getNoBackupFilesDir() = noBackup

                override fun getApplicationContext() = this

                override fun getDatabasePath(name: String) = File(root, "database/$name")
            }
        val key =
            File(current, "files/db_recovery/$keyName").apply {
                checkNotNull(parentFile).mkdirs()
                writeText("accepted portable key")
            }

        init {
            val incoming =
                File(current, "database/logdate").apply {
                    checkNotNull(parentFile).mkdirs()
                    writeText("immutable sender database")
                }
            val prefs =
                File(current, "files/logdate.preferences_pb").apply {
                    checkNotNull(parentFile).mkdirs()
                    writeText("snapshot prefs")
                }
            val inventory =
                if (withInventory) {
                    File(current, NATIVE_MEDIA_INVENTORY_PATH).apply {
                        checkNotNull(parentFile).mkdirs()
                        writeText(Json.encodeToString(NativeJournalMediaInventory(1, emptyList())))
                    }
                } else {
                    null
                }
            writeNativeJournalSourceReceipt(
                File(current, NATIVE_SOURCE_RECEIPT_PATH),
                mutablePreferencesOf(
                    stringPreferencesKey("identity.canonical_owner_id") to "347c6231-19f4-49fa-bd1d-58ed34b9be03",
                    stringPreferencesKey("backend_url") to "https://cloud.example",
                ),
                prefs,
                mapOf("database/logdate" to incoming),
                inventory,
            )
            PostRestoreDetector(context).beginNativeRestoreSession()
            NativeJournalRestoreAuthority(context).begin(resumeInterrupted = false)
        }

        fun payloads() = current.walkTopDown().filter(File::isFile).associateBy { it.relativeTo(current).invariantSeparatorsPath }

        fun assertStaged() {
            assertFalse(current.exists())
            assertTrue(NativeJournalTransferStaging(context).wasStaged())
            val custody = NativeJournalReplayCustody(context)
            assertTrue(custody.hasInputs())
            assertTrue(custody.sealedPayloads().any { it.path.endsWith(NATIVE_SOURCE_RECEIPT_PATH) && it.complete })
        }

        fun restore(validateSnapshot: ((android.content.Context) -> Map<String, File>?)? = null) =
            NativeJournalTransferRestore(
                context,
                { source -> source.copyTo(File(files, "logdate.preferences_pb"), overwrite = true) },
                validateSnapshot ?: {
                    payloads().also { snapshot ->
                        verifyNativeJournalSourceReceipt(
                            checkNotNull(snapshot[NATIVE_SOURCE_RECEIPT_PATH]),
                            checkNotNull(snapshot["files/logdate.preferences_pb"]),
                            mapOf("database/logdate" to checkNotNull(snapshot["database/logdate"])),
                        )
                    }
                },
            )

        fun complete() {
            PostRestoreDetector(context).apply {
                markNativeRestorePayloadReceived(databaseDelivered = true)
                markNativePrivacyPayloadReceived()
                recordRestoreCompleted()
            }
        }

        override fun close() {
            check(root.deleteRecursively())
        }
    }
}
