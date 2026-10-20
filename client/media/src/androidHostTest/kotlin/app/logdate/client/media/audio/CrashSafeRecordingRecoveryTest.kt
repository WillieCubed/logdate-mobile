package app.logdate.client.media.audio

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashSafeRecordingRecoveryTest {
    @Test
    fun `interrupted AAC is finalized without deleting its recovery source`() {
        val directory = Files.createTempDirectory("recording-recovery").toFile()
        try {
            val final = directory.resolve("entry.m4a")
            val raw = directory.resolve("entry.aac").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val recovered =
                CrashSafeRecording.recover(final) { source, target ->
                    source.copyTo(target)
                    true
                }
            assertTrue(recovered)
            assertTrue(final.exists())
            assertTrue(raw.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `failed recovery preserves source and never reports a complete file`() {
        val directory = Files.createTempDirectory("recording-recovery").toFile()
        try {
            val final = directory.resolve("entry.m4a")
            val raw = directory.resolve("entry.aac").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            assertFalse(CrashSafeRecording.recover(final) { _, _ -> false })
            assertTrue(raw.exists())
            assertFalse(final.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
