package app.logdate.client.data.restore

import app.logdate.client.device.restore.NativeIncomingJournal
import app.logdate.client.device.restore.NativeJournalMediaInventory
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class RetainedJournalMediaAdmissionTest {
    @Test
    fun `complete legacy only custody reaches authenticated selector without becoming an independent candidate`() =
        fixture { payloads, inventory ->
            val candidates = retainedIndependentIncomingCandidates(payloads)
            assertTrue(candidates.isEmpty(), "Legacy files must not gain canonical or database candidate authority")
            val selected =
                if (shouldReconcileRetainedMedia(candidates, payloads)) {
                    selectNativeLegacyLateMediaCandidates(payloads, setOf(reference), PACKAGE, inventory)
                } else {
                    emptyList()
                }
            assertEquals(listOf(LEGACY_PATH), selected.map { it.logicalPath })
            assertEquals(setOf(reference), selected.single().references)
        }

    @Test
    fun `legacy custody still wakes worker after canonical acknowledgement`() =
        fixture { payloads, inventory ->
            val canonicalFile = payloads.single().file
            val digest = checksum(canonicalFile)
            val canonicalPath = "files/media/objects/sha256/${digest.take(2)}/$digest.m4a"
            val before = payloads + NativeIncomingJournal.Payload(canonicalPath, canonicalFile, true, canonicalFile.length())
            val beforeCandidates = retainedIndependentIncomingCandidates(before)
            assertTrue(shouldReconcileRetainedMedia(beforeCandidates, before))
            val afterAcknowledgement = before.filterNot { it.path == canonicalPath }
            val remainingCandidates = retainedIndependentIncomingCandidates(afterAcknowledgement)
            assertTrue(remainingCandidates.isEmpty())
            assertTrue(shouldReconcileRetainedMedia(remainingCandidates, afterAcknowledgement))
            assertEquals(
                listOf(LEGACY_PATH),
                selectNativeLegacyLateMediaCandidates(afterAcknowledgement, setOf(reference), PACKAGE, inventory).map { it.logicalPath },
            )
        }

    @Test
    fun `waking worker does not authorize an unreferenced or wrong hash original`() =
        fixture { payloads, inventory ->
            assertTrue(shouldReconcileRetainedMedia(emptyList(), payloads))
            assertTrue(selectNativeLegacyLateMediaCandidates(payloads, emptySet(), PACKAGE, inventory).isEmpty())
            val wrong = inventory.copy(files = inventory.files.map { it.copy(sha256 = "0".repeat(64)) })
            assertTrue(selectNativeLegacyLateMediaCandidates(payloads, setOf(reference), PACKAGE, wrong).isEmpty())
            assertTrue(
                selectNativeLegacyLateMediaCandidates(payloads, setOf(reference), PACKAGE, inventory.copy(files = emptyList())).isEmpty(),
            )
        }

    @Test
    fun `partial legacy and nonmedia inputs do not wake worker`() =
        fixture { payloads, _ ->
            assertFalse(shouldReconcileRetainedMedia(emptyList(), payloads.map { it.copy(complete = false) }))
            val file = payloads.single().file
            listOf(
                "database/logdate",
                "files/logdate.preferences_pb",
                "files/db_recovery/passphrase_backup",
                "files/audio_notes-other/take",
            ).forEach { path ->
                assertFalse(
                    shouldReconcileRetainedMedia(emptyList(), listOf(NativeIncomingJournal.Payload(path, file, true, file.length()))),
                    path,
                )
            }
        }

    @Test
    fun `complete user media wakes worker while exact private root is required`() =
        fixture { payloads, _ ->
            val payload = payloads.single()
            assertTrue(shouldReconcileRetainedMedia(emptyList(), listOf(payload.copy(path = "files/user_media/take.wav"))))
            assertFalse(shouldReconcileRetainedMedia(emptyList(), listOf(payload.copy(path = "user_media/take.wav"))))
        }

    @Test
    fun `authenticated library staging can reach legacy repair without admitting arbitrary library paths`() =
        fixture { payloads, _ ->
            val payload = payloads.single()
            assertTrue(shouldReconcileRetainedMedia(emptyList(), listOf(payload.copy(path = "files/media/staging/draft.wav"))))
            assertFalse(shouldReconcileRetainedMedia(emptyList(), listOf(payload.copy(path = "files/media/other.wav"))))
        }

    private fun fixture(block: (List<NativeIncomingJournal.Payload>, NativeJournalMediaInventory) -> Unit) {
        val root = Files.createTempDirectory("legacy-media-admission-").toFile()
        try {
            val journal = NativeIncomingJournal(File(root, "custody"))
            val id = journal.begin(Uuid.random().toString())
            val bytes = byteArrayOf(0, 0, 0, 20) + "ftypM4A original".encodeToByteArray()
            journal.capture(id, LEGACY_PATH, bytes.size.toLong(), bytes.inputStream())
            val payloads = journal.verified(id, journal.finish(id))
            val payload = payloads.single()
            val inventory =
                NativeJournalMediaInventory(
                    1,
                    listOf(NativeJournalMediaInventory.Entry(LEGACY_PATH, payload.file.length(), checksum(payload.file))),
                )
            block(payloads, inventory)
        } finally {
            check(root.deleteRecursively())
        }
    }

    private fun checksum(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private val reference = "logdate-media://recordings/take.m4a"

    private companion object {
        const val PACKAGE = "studio.hypertext.logdate.debug"
        const val LEGACY_PATH = "files/audio_notes/take.m4a"
    }
}
