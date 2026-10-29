package app.logdate.client.device.restore

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativePrivacyRepairFileCollectionTest {
    @Test
    fun `protected source selection includes unknown database paths but excludes unrelated cohort extras`() {
        for (path in listOf(
            "database/logdate",
            "database/logdate-shm",
            "database/unfamiliar",
            "files/db_recovery/working_passphrase_backup",
            "files/identity_recovery/account_key_backup",
            "files/archive_recovery/records/id/record.json",
            "files/datastore/entry_drafts.preferences_pb",
        )) {
            assertTrue(isNativePrivacyRepairProtectedPath(path), path)
        }
        for (path in listOf(
            "files/logdate.preferences_pb",
            "files/datastore/Firebase.preferences_pb",
            "shared_prefs/widget.xml",
            "files/archive_recovery/sources/original",
            "files/db_recovery-other/key",
            "files/audio_notes/take.m4a",
            "files/datastore/entry_drafts.preferences_pb.part",
        )) {
            assertFalse(isNativePrivacyRepairProtectedPath(path), path)
        }
    }

    @Test
    fun `collector binds all protected roots and exact drafts while leaving preferences and media outside proof`() =
        fixture { f ->
            val paths =
                listOf(
                    "database/logdate",
                    "database/logdate-wal",
                    "database/logdate-shm",
                    "database/logdate-journal",
                    "files/db_recovery/passphrase_backup",
                    "files/db_recovery/nested/retained_workspace.json",
                    "files/identity_recovery/phrase_backup",
                    "files/archive_recovery/records/record/manifest.json",
                    "files/datastore/entry_drafts.preferences_pb",
                )
            paths.forEach { f.file(it, "protected $it") }
            f.file("files/logdate.preferences_pb", "CAS protected preferences")
            f.file("files/datastore/other.preferences_pb", "unrelated data")
            f.file("files/archive_recovery/sources/original", "unrelated archive bytes")
            f.file("files/audio_notes/take.m4a", "unrelated media")
            val before = f.bytes()
            val selected = captureNativePrivacyRepairFiles(f.context)
            assertEquals(paths.toSet(), selected.keys)
            assertEquals(before, f.bytes())
            selected.forEach { (path, file) -> assertEquals(File(f.root, path), file) }
        }

    @Test
    fun `inventory omits only rebuildable SHM and includes existing installation identity`() =
        fixture { f ->
            f.file("database/logdate", "journal")
            f.file("database/logdate-shm", "cache")
            f.file("files/db_recovery/passphrase_backup", "key")
            val installation = f.file("no-backup/.logdate_installation_id", "local installation")
            val first = captureNativePrivacyRepairInventory(f.context)
            assertEquals(
                setOf("database/logdate", "files/db_recovery/passphrase_backup", "local/.logdate_installation_id"),
                first.keys,
            )
            assertEquals(hash(installation), first["local/.logdate_installation_id"])
            f.file("database/logdate-shm", "new cache")
            assertEquals(first, captureNativePrivacyRepairInventory(f.context))
            f.file("files/db_recovery/passphrase_backup", "new key")
            assertTrue(first != captureNativePrivacyRepairInventory(f.context))
        }

    @Test
    fun `absent storage produces no invented files or installation identity`() =
        fixture { f ->
            val before = f.bytes()
            assertTrue(captureNativePrivacyRepairFiles(f.context).isEmpty())
            assertTrue(captureNativePrivacyRepairInventory(f.context).isEmpty())
            assertEquals(before, f.bytes())
            assertFalse(File(f.root, "database").exists())
            assertFalse(File(f.root, "no-backup").exists())
        }

    @Test
    fun `symlink recovery directories and file leaves never enter canonical proof`() =
        fixture { f ->
            f.file("outside/key", "private outside bytes")
            f.file("files/identity_recovery/phrase_backup", "identity")
            Files.createSymbolicLink(File(f.root, "files/db_recovery").toPath(), File(f.root, "outside").toPath())
            assertFailsWith<IllegalStateException> { captureNativePrivacyRepairFiles(f.context) }
            assertTrue(File(f.root, "files/db_recovery").delete())
            val draft = File(f.root, "files/datastore/entry_drafts.preferences_pb")
            assertTrue(draft.parentFile.mkdirs())
            Files.createSymbolicLink(draft.toPath(), File(f.root, "outside/key").toPath())
            assertFailsWith<IllegalStateException> { captureNativePrivacyRepairFiles(f.context) }
            assertEquals("private outside bytes", File(f.root, "outside/key").readText())
        }

    @Test
    fun `non-directory recovery root and symlink installation id fail closed`() =
        fixture { f ->
            val directory = f.file("files/archive_recovery", "not a directory")
            assertFailsWith<IllegalStateException> { captureNativePrivacyRepairFiles(f.context) }
            assertTrue(directory.delete())
            f.file("outside-id", "unrelated installation")
            val id = File(f.root, "no-backup/.logdate_installation_id")
            assertTrue(id.parentFile.mkdirs())
            Files.createSymbolicLink(id.toPath(), File(f.root, "outside-id").toPath())
            assertFailsWith<IllegalStateException> { captureNativePrivacyRepairInventory(f.context) }
        }

    @Test
    fun `unknown database member and source change during isolated inspection invalidate proof`() =
        fixture { f ->
            val current = linkedMapOf("database/logdate" to f.file("current", "journal"))
            val source = linkedMapOf("database/logdate" to f.file("incoming", "journal"))
            source["database/logdate-unknown"] = f.file("unknown", "extra database state")
            assertFailsWith<IllegalStateException> { verifyNativePrivacyRepairFiles(current, source) {} }
            source.remove("database/logdate-unknown")
            assertFailsWith<IllegalStateException> {
                verifyNativePrivacyRepairFiles(current, source) { source.getValue("database/logdate").writeText("changed source") }
            }
            assertEquals("journal", current.getValue("database/logdate").readText())
        }

    @Test
    fun `new map membership during inspection cannot be omitted by an old snapshot`() =
        fixture { f ->
            val current = linkedMapOf("database/logdate" to f.file("current", "journal"))
            val source = linkedMapOf("database/logdate" to f.file("incoming", "journal"))
            assertFailsWith<IllegalStateException> {
                verifyNativePrivacyRepairFiles(current, source) { current["database/logdate-wal"] = f.file("new-wal", "new current state") }
            }
        }

    private fun fixture(test: (Fixture) -> Unit) {
        val root = Files.createTempDirectory("privacy-files-collector-").toFile()
        try {
            test(Fixture(root))
        } finally {
            check(root.deleteRecursively())
        }
    }

    private class Fixture(
        val root: File,
    ) {
        val context =
            mockk<Context>().also {
                every { it.filesDir } returns File(root, "files")
                every { it.noBackupFilesDir } returns File(root, "no-backup")
                every { it.getDatabasePath(any()) } answers { File(root, "database/${firstArg<String>()}") }
            }

        fun file(
            path: String,
            value: String,
        ): File =
            File(root, path).apply {
                parentFile.mkdirs()
                writeText(value)
            }

        fun bytes(): Map<String, List<Byte>> =
            root.walkTopDown().filter(File::isFile).associate {
                it.relativeTo(root).path to
                    it.readBytes().toList()
            }
    }

    private fun hash(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
