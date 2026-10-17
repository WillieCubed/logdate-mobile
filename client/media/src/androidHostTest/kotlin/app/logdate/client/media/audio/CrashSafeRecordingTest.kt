package app.logdate.client.media.audio

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashSafeRecordingTest {
    @Test
    fun phone_in_flight_and_remux_files_live_under_backup_excluded_staging() {
        val audioDirectory = Files.createTempDirectory("logdate-audio").toFile()
        val final = File(audioDirectory, "recording-123.m4a")

        val inFlight = CrashSafeRecording.inFlightFile(final, isolateForBackup = true)
        val remuxStaging = CrashSafeRecording.remuxStagingFile(inFlight, final)

        assertEquals(File(audioDirectory, "staging/recording-123.aac"), inFlight)
        assertEquals(File(audioDirectory, "staging/recording-123.m4a.tmp"), remuxStaging)
        assertTrue(inFlight.parentFile!!.isDirectory)
        assertEquals(final, CrashSafeRecording.finalFile(inFlight))
        audioDirectory.deleteRecursively()
    }

    @Test
    fun `phone recording is published only after the staging output is complete`() {
        val root = Files.createTempDirectory("recording-publish").toFile()
        try {
            val audioDirectory = File(root, "audio_notes").apply { mkdirs() }
            val final = File(audioDirectory, "recording-123.m4a")
            val staged = CrashSafeRecording.backupExcludedFile(final)
            assertTrue(staged.parentFile!!.isDirectory)
            staged.writeBytes(byteArrayOf(1, 2, 3))

            assertEquals(CompletedRecordingPublication.PUBLISHED, CrashSafeRecording.publishCompletedRecording(staged, final))
            assertTrue(final.isFile)
            assertEquals(byteArrayOf(1, 2, 3).toList(), final.readBytes().toList())
            assertFalse(staged.exists())
            assertFalse(File(staged.parentFile, "${staged.name}.ready").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `empty or missing phone staging output is never published`() {
        val root = Files.createTempDirectory("recording-publish-empty").toFile()
        try {
            val audioDirectory = File(root, "audio_notes").apply { mkdirs() }
            val final = File(audioDirectory, "recording-123.m4a")
            val staged = CrashSafeRecording.backupExcludedFile(final)
            assertTrue(staged.parentFile!!.isDirectory)
            staged.createNewFile()

            assertEquals(CompletedRecordingPublication.FAILED, CrashSafeRecording.publishCompletedRecording(staged, final))
            assertFalse(final.exists())
            assertTrue(staged.exists())
            assertEquals(
                CompletedRecordingPublication.FAILED,
                CrashSafeRecording.publishCompletedRecording(File(root, "missing.m4a"), final),
            )
            assertFalse(final.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `queued publish keeps the final recording path recoverable after restart`() {
        val root = Files.createTempDirectory("recording-retry").toFile()
        try {
            val audioDirectory = File(root, "audio_notes").apply { mkdirs() }
            val final = File(audioDirectory, "recording-owned-by-draft.m4a")
            val staged = CrashSafeRecording.backupExcludedFile(final)
            staged.writeBytes(byteArrayOf(7, 8, 9))

            var markerExistedBeforePublication = false
            val publish =
                CrashSafeRecording.publishCompletedRecording(staged, final) { _, _ ->
                    markerExistedBeforePublication = File(staged.parentFile, "${staged.name}.ready").isFile
                    false
                }

            assertEquals(CompletedRecordingPublication.QUEUED_FOR_RETRY, publish)
            assertTrue(markerExistedBeforePublication, "The completion marker must be durable before publication starts")
            assertFalse(final.exists())
            assertTrue(staged.isFile)
            assertTrue(File(staged.parentFile, "${staged.name}.ready").isFile)

            val recovered =
                CrashSafeRecording.recoverCompletedRecordings(audioDirectory) { source, destination ->
                    Files.move(source.toPath(), destination.toPath())
                    true
                }
            assertEquals(1, recovered)

            assertTrue(final.isFile, "The draft's durable final path must exist after startup recovery")
            assertEquals(byteArrayOf(7, 8, 9).toList(), final.readBytes().toList())
            assertFalse(staged.exists())
            assertFalse(File(staged.parentFile, "${staged.name}.ready").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `completed phone staging output is recovered after a failed publish`() {
        val root = Files.createTempDirectory("recording-recovery").toFile()
        try {
            val audioDirectory = File(root, "audio_notes").apply { mkdirs() }
            val final = File(audioDirectory, "recording-123.m4a")
            val staged = CrashSafeRecording.backupExcludedFile(final)
            staged.writeBytes(byteArrayOf(4, 5, 6))
            assertTrue(CrashSafeRecording.markCompletedForRetry(staged))

            assertEquals(1, CrashSafeRecording.recoverCompletedRecordings(audioDirectory))
            assertTrue(final.isFile)
            assertEquals(byteArrayOf(4, 5, 6).toList(), final.readBytes().toList())
            assertFalse(staged.exists())
            assertFalse(File(staged.parentFile, "${staged.name}.ready").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `startup removes marker left after destination was already published`() {
        val root = Files.createTempDirectory("recording-recovery-published").toFile()
        try {
            val audioDirectory = File(root, "audio_notes").apply { mkdirs() }
            val final = File(audioDirectory, "recording-123.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val staged = CrashSafeRecording.backupExcludedFile(final)
            staged.writeBytes(byteArrayOf(1, 2, 3))
            assertTrue(CrashSafeRecording.markCompletedForRetry(staged))
            assertTrue(staged.delete())

            assertEquals(0, CrashSafeRecording.recoverCompletedRecordings(audioDirectory))
            assertTrue(final.isFile)
            assertFalse(File(staged.parentFile, "${staged.name}.ready").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `startup salvages an unmarked raw phone recording into its draft target`() {
        val root = Files.createTempDirectory("recording-recovery-raw").toFile()
        try {
            val audioDirectory = File(root, "audio_notes").apply { mkdirs() }
            val final = File(audioDirectory, "recording-owned-by-draft.m4a")
            val raw = CrashSafeRecording.inFlightFile(final, isolateForBackup = true)
            raw.writeBytes(byteArrayOf(10, 11, 12))

            val recovered =
                CrashSafeRecording.recoverCompletedRecordings(
                    audioDirectory,
                    moveFile = { _, _ -> false },
                    remuxRawFile = { source, destination ->
                        assertEquals(raw, source)
                        assertEquals(final, destination)
                        destination.writeBytes(source.readBytes())
                        true
                    },
                )

            assertEquals(1, recovered)
            assertEquals(byteArrayOf(10, 11, 12).toList(), final.readBytes().toList())
            assertFalse(raw.exists())
            assertFalse(File(raw.parentFile, "${raw.name}.ready").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `startup does not bless a changed raw recording over its completion marker`() {
        val root = Files.createTempDirectory("recording-recovery-raw-invalid").toFile()
        try {
            val audioDirectory = File(root, "audio_notes").apply { mkdirs() }
            val final = File(audioDirectory, "recording-owned-by-draft.m4a")
            val raw = CrashSafeRecording.inFlightFile(final, isolateForBackup = true)
            raw.writeBytes(byteArrayOf(10, 11, 12))
            assertTrue(CrashSafeRecording.markCompletedForRetry(raw))
            raw.writeBytes(byteArrayOf(10, 11))

            val recovered =
                CrashSafeRecording.recoverCompletedRecordings(
                    audioDirectory,
                    moveFile = { _, _ -> false },
                    remuxRawFile = { _, _ -> error("Changed raw audio must not be remuxed") },
                )

            assertEquals(0, recovered)
            assertFalse(final.exists())
            assertTrue(raw.isFile)
            assertEquals("v1:3", File(raw.parentFile, "${raw.name}.ready").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `completed recording marker is ignored when staged file changed`() {
        val root = Files.createTempDirectory("recording-recovery-invalid").toFile()
        try {
            val audioDirectory = File(root, "audio_notes").apply { mkdirs() }
            val final = File(audioDirectory, "recording-123.m4a")
            val staged = CrashSafeRecording.backupExcludedFile(final)
            staged.writeBytes(byteArrayOf(4, 5, 6))
            assertTrue(CrashSafeRecording.markCompletedForRetry(staged))
            staged.writeBytes(byteArrayOf(7))

            assertEquals(0, CrashSafeRecording.recoverCompletedRecordings(audioDirectory))
            assertFalse(final.exists())
            assertTrue(staged.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun wearable_recording_layout_remains_compatible() {
        val audioDirectory = Files.createTempDirectory("logdate-audio").toFile()
        val final = File(audioDirectory, "recording-123.m4a")

        val inFlight = CrashSafeRecording.inFlightFile(final)

        assertEquals(File(audioDirectory, "recording-123.aac"), inFlight)
        assertEquals(final, CrashSafeRecording.finalFile(inFlight))
        audioDirectory.deleteRecursively()
    }
}
