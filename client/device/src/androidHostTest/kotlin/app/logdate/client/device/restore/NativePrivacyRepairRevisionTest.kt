package app.logdate.client.device.restore

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class NativePrivacyRepairRevisionTest {
    @Test
    fun `only the exact protected WAL revision may join the already accepted database`() =
        fixture { custody, id ->
            capture(custody, id, "database/logdate", "accepted database")
            capture(custody, id, "database/logdate-wal", "old WAL")
            capture(custody, id, "database/logdate-wal", "accepted WAL")
            val hash = custody.sealCohort(id)
            val selected = verifiedNativePrivacyRepairCohort(custody, id, hash, mapOf("database/logdate-wal" to sha("accepted WAL")))
            assertEquals("accepted WAL", selected.getValue("database/logdate-wal").readText())
            assertEquals(3, custody.verifiedCohort(id, hash).size, "Other complete revisions remain preserved")
        }

    @Test
    fun `unanchored mismatched policy and incomplete revisions remain refused`() {
        for (path in listOf("database/logdate-wal", "files/logdate.preferences_pb")) {
            fixture { custody, id ->
                capture(custody, id, path, "first")
                capture(custody, id, path, "second")
                val hash = custody.sealCohort(id)
                assertFailsWith<IllegalStateException> { verifiedNativePrivacyRepairCohort(custody, id, hash) }
                assertFailsWith<IllegalStateException> {
                    verifiedNativePrivacyRepairCohort(custody, id, hash, mapOf(path to sha("missing")))
                }
                if (path.startsWith("files/")) {
                    assertFailsWith<IllegalStateException> {
                        verifiedNativePrivacyRepairCohort(custody, id, hash, mapOf(path to sha("first")))
                    }
                }
            }
        }
        fixture { custody, id ->
            capture(custody, id, "database/logdate-wal", "accepted WAL")
            assertFailsWith<IOException> {
                custody.captureInCohort(id, "database/logdate-wal", 100, "incomplete".byteInputStream())
            }
            val hash = custody.sealCohort(id)
            assertFailsWith<IllegalStateException> {
                verifiedNativePrivacyRepairCohort(custody, id, hash, mapOf("database/logdate-wal" to sha("accepted WAL")))
            }
        }
    }

    private fun capture(
        custody: NativeJournalReplayCustody,
        id: String,
        path: String,
        value: String,
    ) = custody.captureInCohort(id, path, value.encodeToByteArray().size.toLong(), value.byteInputStream())

    private fun sha(value: String) =
        MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray()).joinToString("") {
            "%02x".format(it)
        }

    private fun fixture(block: (NativeJournalReplayCustody, String) -> Unit) {
        val root = Files.createTempDirectory("privacy-revisions").toFile()
        val context = mockk<Context> { every { noBackupFilesDir } returns root }
        try {
            block(NativeJournalReplayCustody(context), Uuid.random().toString())
        } finally {
            root.deleteRecursively()
        }
    }
}
