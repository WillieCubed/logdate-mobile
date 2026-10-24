package app.logdate.client.device.restore

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeJournalTransferOwnershipTest {
    @Test
    fun `crash during export discards the one owned staging directory`() {
        fixture { root, marker ->
            val staging = File(root, ".source.preparing")
            NativeJournalTransferOwnership(marker, File(root, "current")).begin(staging)
            File(staging, "large-encrypted-database").writeText("still-owned source copy")
            NativeJournalTransferOwnership(marker, File(root, "current")).discardOwned()
            assertFalse(staging.exists())
            assertFalse(marker.exists())
        }
    }

    @Test
    fun `crash before atomic move discards committed owned staging`() {
        fixture { root, marker ->
            val staging = File(root, ".source.preparing")
            val ownership = NativeJournalTransferOwnership(marker, File(root, "current"))
            ownership.begin(staging)
            File(staging, NATIVE_SOURCE_RECEIPT_PATH).apply {
                checkNotNull(parentFile).mkdirs()
                writeText("sender proof")
            }
            ownership.record(staging)
            NativeJournalTransferOwnership(marker, File(root, "current")).discardOwned()
            assertFalse(staging.exists())
            assertFalse(marker.exists())
        }
    }

    @Test
    fun `crash after commit can discard only the locally owned sender snapshot`() {
        fixture { root, marker ->
            val staging = File(root, ".source.preparing").apply { mkdirs() }
            File(staging, NATIVE_SOURCE_RECEIPT_PATH).apply {
                checkNotNull(parentFile).mkdirs()
                writeText("sender proof")
            }
            val ownership = NativeJournalTransferOwnership(marker, File(root, "current"))
            ownership.record(staging)
            check(staging.renameTo(File(root, "current")))
            NativeJournalTransferOwnership(marker, File(root, "current")).discardOwned()
            assertFalse(File(root, "current").exists())
            assertFalse(marker.exists())
        }
    }

    @Test
    fun `incoming subtree without noBackup ownership remains intact`() {
        fixture { root, marker ->
            val current = File(root, "current").apply { mkdirs() }
            File(current, "original").writeText("retained incoming bytes")
            assertFailsWith<IllegalStateException> { NativeJournalTransferOwnership(marker, current).discardOwned() }
            assertTrue(File(current, "original").isFile)
        }
    }

    @Test
    fun `changed owned receipt cannot authorize deletion`() {
        fixture { root, marker ->
            val staging = File(root, ".source.preparing").apply { mkdirs() }
            val proof =
                File(staging, NATIVE_SOURCE_RECEIPT_PATH).apply {
                    checkNotNull(parentFile).mkdirs()
                    writeText("sender proof")
                }
            val ownership = NativeJournalTransferOwnership(marker, File(root, "current"))
            ownership.record(staging)
            proof.writeText("different incoming proof")
            check(staging.renameTo(File(root, "current")))
            assertFailsWith<IllegalStateException> { ownership.discardOwned() }
            assertTrue(File(root, "current").isDirectory)
        }
    }

    private fun fixture(block: (File, File) -> Unit) {
        val root = Files.createTempDirectory("native-transfer-owner-").toFile()
        try {
            block(root, File(root, "no-backup/sender-owner"))
        } finally {
            check(root.deleteRecursively())
        }
    }
}
