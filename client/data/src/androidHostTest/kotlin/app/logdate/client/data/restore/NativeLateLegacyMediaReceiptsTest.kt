package app.logdate.client.data.restore

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NativeLateLegacyMediaReceiptsTest {
    @Test
    fun `canonical mapping survives restart only for exact source and payload revision`() {
        val root = Files.createTempDirectory("legacy-native-receipts-").toFile()
        try {
            val original = File(root, "payload").apply { writeText("old original") }
            val hash = nativeSnapshotChecksum(original)
            val candidate = NativeLegacyLateMediaCandidate("files/user_media/photo.jpg", original, hash, emptySet())
            val canonical = "logdate-media://library/objects/sha256/${hash.take(2)}/$hash.jpg"
            NativeLateLegacyMediaReceipts(root, "a".repeat(64)).remember(candidate, canonical)
            assertEquals(canonical, NativeLateLegacyMediaReceipts(root, "a".repeat(64)).read(candidate.logicalPath, hash))
            assertNull(NativeLateLegacyMediaReceipts(root, "b".repeat(64)).read(candidate.logicalPath, hash))
            assertNull(NativeLateLegacyMediaReceipts(root, "a".repeat(64)).read(candidate.logicalPath, "c".repeat(64)))
        } finally {
            check(root.deleteRecursively())
        }
    }
}
