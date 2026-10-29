package app.logdate.client.device.restore

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class NativePrivacyRepairAccountingTest {
    @Test
    fun `accounted source remains physically preserved while no longer blocking backup or replay routing`() =
        fixture { f ->
            val bytes = f.payload.readBytes()
            writeNativePrivacyRepairAccounting(f.directory, f.receipt)
            assertTrue(isAccountedNativePrivacyRepairCohort(f.directory))
            val reopened = NativeJournalReplayCustody(f.context)
            assertFalse(reopened.hasInputs())
            assertFalse(reopened.hasJournalInputs())
            assertTrue(reopened.sealedPayloads().isEmpty())
            assertTrue(reopened.finish())
            assertEquals(bytes.toList(), f.payload.readBytes().toList())
            assertEquals(1, reopened.verifiedCohort(f.id, f.hash).size, "Accounting never deletes captured bytes")
        }

    @Test
    fun `missing corrupt foreign and changed manifest acknowledgements never hide custody`() =
        fixture { f ->
            assertFalse(isAccountedNativePrivacyRepairCohort(f.directory))
            assertTrue(f.custody.hasInputs())
            val marker = File(f.directory, "privacy-accounted.json")
            marker.writeText("incomplete")
            assertFails { f.custody.hasInputs() }
            marker.writeText(Json.encodeToString(f.receipt.copy(cohortId = Uuid.random().toString())))
            assertFails { f.custody.hasJournalInputs() }
            marker.delete()
            assertFails { writeNativePrivacyRepairAccounting(f.directory, f.receipt.copy(manifestSha256 = "a".repeat(64))) }
            assertTrue(f.custody.hasInputs())
            writeNativePrivacyRepairAccounting(f.directory, f.receipt)
            File(f.directory, "manifest.json").appendText("changed")
            assertFails { f.custody.hasInputs() }
        }

    @Test
    fun `an independent unaccounted cohort continues to block backup`() =
        fixture { f ->
            writeNativePrivacyRepairAccounting(f.directory, f.receipt)
            val other = Uuid.random().toString()
            f.custody.captureInCohort(other, "files/audio_notes/unproven.m4a", 5, "audio".byteInputStream())
            assertTrue(f.custody.hasInputs())
            assertEquals(1, f.custody.sealedPayloads().size)
        }

    private fun fixture(block: (Fixture) -> Unit) {
        val root = Files.createTempDirectory("privacy-accounted").toFile()
        try {
            block(Fixture(root))
        } finally {
            root.deleteRecursively()
        }
    }

    private class Fixture(
        root: File,
    ) {
        val context = mockk<Context> { every { noBackupFilesDir } returns root }
        val custody = NativeJournalReplayCustody(context)
        val id = Uuid.random().toString()
        val hash: String
        val payload: File
        val directory = File(root, "native-journal-replay-custody/$id")
        val receipt: NativePrivacyRepairAccounting

        init {
            custody.captureInCohort(id, "database/logdate", 7, "journal".byteInputStream())
            hash = custody.sealCohort(id)
            payload = custody.verifiedCohort(id, hash).single().file
            receipt =
                NativePrivacyRepairAccounting(
                    generation = Uuid.random().toString(),
                    accountDeliveryId = Uuid.random().toString(),
                    cohortId = id,
                    manifestSha256 = hash,
                    protectedInventorySha256 = "b".repeat(64),
                    acceptedPreferencesSha256 = "c".repeat(64),
                )
        }
    }
}
