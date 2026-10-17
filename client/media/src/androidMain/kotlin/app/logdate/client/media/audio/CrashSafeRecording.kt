package app.logdate.client.media.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import io.github.aakira.napier.Napier
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Wraps a raw AAC stream into the MPEG-4 audio file the rest of the app plays and uploads. */
fun interface AudioRemuxer {
    /**
     * @return true when [destination] holds a playable file made from [source]. A false result
     *   leaves no partial [destination] behind.
     */
    fun remuxToM4a(
        source: File,
        destination: File,
    ): Boolean
}

/** Whether a completed recording is already published, durably queued for retry, or unrecoverable. */
enum class CompletedRecordingPublication {
    PUBLISHED,
    QUEUED_FOR_RETRY,
    FAILED,
}

/**
 * File names for a recording that is written as raw AAC and wrapped into MPEG-4 when it ends.
 *
 * An MPEG-4 file keeps its index at the end, so a recording cut short by the process dying cannot
 * be played. Raw AAC frames are self-delimiting, so what was written before the cut still is. The
 * in-flight file sits beside the final one under the same name, which lets a later launch find the
 * recording a dead process left behind.
 */
object CrashSafeRecording {
    const val IN_FLIGHT_EXTENSION = "aac"
    const val FINAL_EXTENSION = "m4a"
    const val BACKUP_EXCLUDED_STAGING_DIRECTORY = "staging"

    fun inFlightFile(
        finalFile: File,
        isolateForBackup: Boolean = false,
    ): File {
        val parent =
            if (isolateForBackup) {
                File(finalFile.parentFile, BACKUP_EXCLUDED_STAGING_DIRECTORY).also(File::mkdirs)
            } else {
                finalFile.parentFile
            }
        return File(parent, "${finalFile.nameWithoutExtension}.$IN_FLIGHT_EXTENSION")
    }

    fun backupExcludedFile(finalFile: File): File = File(backupExcludedDirectory(finalFile), finalFile.name)

    private fun backupExcludedDirectory(finalFile: File): File =
        File(finalFile.parentFile, BACKUP_EXCLUDED_STAGING_DIRECTORY).also { directory ->
            check(directory.isDirectory || directory.mkdirs()) { "Could not create recording staging directory" }
        }

    /** Publishes a finished phone recording without exposing a partial destination to backup. */
    fun publishCompletedRecording(
        stagingFile: File,
        finalFile: File,
    ): CompletedRecordingPublication = publishCompletedRecording(stagingFile, finalFile, ::moveCompletedFile)

    internal fun publishCompletedRecording(
        stagingFile: File,
        finalFile: File,
        moveFile: (File, File) -> Boolean,
    ): CompletedRecordingPublication {
        if (!stagingFile.isFile || stagingFile.length() == 0L || finalFile.exists()) return CompletedRecordingPublication.FAILED
        val marker = readyMarker(stagingFile)
        val markerWritten = markCompletedForRetry(stagingFile)
        if (moveFile(stagingFile, finalFile)) {
            if (marker.exists() && !marker.delete()) Napier.w("Could not remove completed recording retry marker ${marker.name}")
            return CompletedRecordingPublication.PUBLISHED
        }
        if (markerWritten) {
            Napier.w("Completed recording ${finalFile.name} was preserved for retry")
            return CompletedRecordingPublication.QUEUED_FOR_RETRY
        }
        return CompletedRecordingPublication.FAILED
    }

    /** Records a durable completion marker so app startup can retry a failed publish. */
    fun markCompletedForRetry(stagingFile: File): Boolean {
        if (!stagingFile.isFile || stagingFile.length() == 0L) return false
        val marker = readyMarker(stagingFile)
        val temporary = File(stagingFile.parentFile, "${stagingFile.name}.ready.part")
        return try {
            FileOutputStream(temporary).use { output ->
                output.write("v1:${stagingFile.length()}".encodeToByteArray())
                output.fd.sync()
            }
            try {
                Files.move(temporary.toPath(), marker.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: Exception) {
                Files.move(temporary.toPath(), marker.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            true
        } catch (e: Exception) {
            Napier.e("Could not preserve completed recording ${stagingFile.name} for retry", e)
            temporary.delete()
            false
        }
    }

    /** Removes the durable retry marker after its target has been published. */
    fun clearCompletedRetryMarker(stagingFile: File) {
        val marker = readyMarker(stagingFile)
        if (marker.exists() && !marker.delete()) Napier.w("Could not remove completed recording retry marker ${marker.name}")
    }

    /** Retries complete phone recordings and salvages raw AAC left by a process interruption. */
    fun recoverCompletedRecordings(audioDirectory: File): Int =
        recoverCompletedRecordings(audioDirectory, ::moveCompletedFile, AdtsToM4aRemuxer::remuxToM4a)

    internal fun recoverCompletedRecordings(
        audioDirectory: File,
        moveFile: (File, File) -> Boolean,
    ): Int = recoverCompletedRecordings(audioDirectory, moveFile, AdtsToM4aRemuxer::remuxToM4a)

    internal fun recoverCompletedRecordings(
        audioDirectory: File,
        moveFile: (File, File) -> Boolean,
        remuxRawFile: (File, File) -> Boolean,
    ): Int {
        val stagingDirectory = File(audioDirectory, BACKUP_EXCLUDED_STAGING_DIRECTORY)
        val unmarkedRawFiles = stagingDirectory.listFiles().orEmpty()
        for (file in unmarkedRawFiles) {
            if (file.isFile && file.extension == IN_FLIGHT_EXTENSION && !readyMarker(file).isFile) {
                markCompletedForRetry(file)
            }
        }
        return stagingDirectory.listFiles().orEmpty().count { marker ->
            runCatching {
                if (!marker.name.endsWith(READY_SUFFIX)) return@count false
                val staged = File(stagingDirectory, marker.name.removeSuffix(READY_SUFFIX))
                val expectedLength = marker.readText().removePrefix("v1:").toLongOrNull()
                val finalFile = finalFile(staged)
                if (expectedLength == null) return@count false
                if (!staged.isFile) {
                    if (finalFile.isFile && finalFile.length() == expectedLength && !marker.delete()) {
                        Napier.w("Could not remove stale completed recording retry marker ${marker.name}")
                    }
                    return@count false
                }
                if (staged.length() != expectedLength) return@count false
                val published =
                    if (staged.extension == IN_FLIGHT_EXTENSION) {
                        remuxRawFile(staged, finalFile) && finalFile.isFile && finalFile.length() > 0L && staged.delete()
                    } else {
                        moveFile(staged, finalFile)
                    }
                if (!published) return@count false
                if (!marker.delete()) Napier.w("Could not remove completed recording retry marker ${marker.name}")
                true
            }.getOrElse { error ->
                Napier.w("Could not inspect completed recording retry marker ${marker.name}", error)
                false
            }
        }
    }

    private fun moveCompletedFile(
        stagingFile: File,
        finalFile: File,
    ): Boolean =
        try {
            if (!stagingFile.isFile || stagingFile.length() == 0L || finalFile.exists()) return false
            try {
                Files.move(stagingFile.toPath(), finalFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(stagingFile.toPath(), finalFile.toPath())
            }
            finalFile.isFile && finalFile.length() > 0L && !stagingFile.exists()
        } catch (e: Exception) {
            Napier.w("Could not retry publishing completed recording ${finalFile.name}", e)
            false
        }

    fun finalFile(inFlightFile: File): File {
        val parent = inFlightFile.parentFile
        val finalParent = if (parent?.name == BACKUP_EXCLUDED_STAGING_DIRECTORY) parent.parentFile else parent
        return File(finalParent, "${inFlightFile.nameWithoutExtension}.$FINAL_EXTENSION")
    }

    fun remuxStagingFile(
        source: File,
        destination: File,
    ): File = File(source.parentFile, "${destination.name}.tmp")

    private const val READY_SUFFIX = ".ready"

    private fun readyMarker(stagingFile: File): File = File(stagingFile.parentFile, "${stagingFile.name}$READY_SUFFIX")
}

/** Copies the AAC frames of a raw AAC file into an MPEG-4 container without re-encoding them. */
object AdtsToM4aRemuxer : AudioRemuxer {
    private const val SAMPLE_BUFFER_BYTES = 1 shl 16

    override fun remuxToM4a(
        source: File,
        destination: File,
    ): Boolean {
        val staging = CrashSafeRecording.remuxStagingFile(source, destination)
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            staging.delete()
            extractor.setDataSource(source.absolutePath)
            val track = audioTrack(extractor)
            if (track == null) {
                Napier.w("No audio found in ${source.name}")
                return false
            }
            extractor.selectTrack(track)
            val activeMuxer = MediaMuxer(staging.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = activeMuxer
            val outputTrack = activeMuxer.addTrack(extractor.getTrackFormat(track))
            activeMuxer.start()
            val samples = copySamples(extractor, activeMuxer, outputTrack)
            activeMuxer.stop()
            muxer = null
            activeMuxer.release()
            if (samples == 0) {
                Napier.w("${source.name} holds no audio frames")
                staging.delete()
                return false
            }
            Files.move(staging.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (e: Exception) {
            Napier.e("Could not wrap ${source.name} into ${destination.name}", e)
            muxer?.let { runCatching { it.release() } }
            staging.delete()
            false
        } finally {
            extractor.release()
        }
    }

    private fun audioTrack(extractor: MediaExtractor): Int? =
        (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }

    private fun copySamples(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        outputTrack: Int,
    ): Int {
        val buffer = ByteBuffer.allocate(SAMPLE_BUFFER_BYTES)
        val info = MediaCodec.BufferInfo()
        var samples = 0
        while (true) {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.set(0, size, extractor.sampleTime, extractor.sampleFlags)
            muxer.writeSampleData(outputTrack, buffer, info)
            samples++
            extractor.advance()
        }
        return samples
    }
}
