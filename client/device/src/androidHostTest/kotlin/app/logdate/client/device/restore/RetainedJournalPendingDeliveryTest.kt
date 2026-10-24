package app.logdate.client.device.restore

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class RetainedJournalPendingDeliveryTest {
    @Test
    fun abandoned_partial_does_not_block_retaining_a_second_locked_working_journal() {
        val root = Files.createTempDirectory("retained-pending-delivery-").toFile()
        try {
            val db =
                File(root, "database/logdate").apply {
                    checkNotNull(parentFile).mkdirs()
                    writeText("original A")
                }
            val key = File(root, "key").apply { writeText("retained key") }
            val retained = File(root, "retained")
            val workspace =
                RetainedJournalWorkspace(
                    File(root, "session.json"),
                    retained,
                    { mapOf("database/logdate" to db, "keys/old" to key) },
                    setOf("database/logdate"),
                    File(root, "no-backup/authority"),
                )
            val first = workspace.prepare()
            db.writeText("accepted working B")
            val incoming = workspace.beginIncomingDelivery(Uuid.random().toString())
            assertFailsWith<IOException> {
                workspace.captureIncomingFile(incoming, "database/logdate", 99, "partial C".byteInputStream())
            }
            val second = workspace.prepare()
            assertNotEquals(first.id, second.id)
            assertEquals(first.manifestSha256, second.priorGenerations[first.id])
            assertTrue(second.incomingCohorts.getValue(incoming).isNotEmpty())
            assertEquals(
                "partial C",
                workspace
                    .incomingPayloads()
                    .getValue(incoming)
                    .single()
                    .file
                    .readText(),
            )
            assertTrue(incoming !in workspace.coherentIncomingPayloads())
            val sources = workspace.retainedSources()
            assertEquals("original A", sources.getValue(first.id).getValue("database/logdate").readText())
            assertEquals("accepted working B", sources.getValue(second.id).getValue("database/logdate").readText())
            db.writeText("working D")
            workspace.requireBackupReady(true, db, key)
            assertFailsWith<IllegalStateException> { workspace.requireBackupReady(false, db, key) }
        } finally {
            root.deleteRecursively()
        }
    }
}
