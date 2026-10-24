package app.logdate.feature.core.restore

import android.content.Context
import android.net.Uri
import app.logdate.client.domain.restore.RestoreArchiveReader
import app.logdate.client.domain.restore.ZipSignature
import io.github.aakira.napier.Napier
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipFile
import kotlin.coroutines.cancellation.CancellationException

/** Reads archive previews without owning picker requests or publishing UI state. */
internal class ArchiveMetadataReader(
    private val context: Context,
) {
    /**
     * Reads only the v2 manifest or v1 metadata entry from the archive for preview.
     *
     * The ZIP is read through its central directory, so the entries stored before
     * the preview entry are never read, and archives whose entries use data descriptors (as the
     * iOS writer's do) stay readable. Returns `null` if the entry is missing or the archive
     * cannot be read.
     */
    suspend fun read(uri: Uri): String? =
        try {
            withRandomAccessZip(uri) { zip ->
                val entryNames = zip.entries().toList().map { it.name }
                RestoreArchiveReader.previewJson(entryNames) { entryName ->
                    val entry = zip.getEntry(entryName) ?: return@previewJson null
                    zip.getInputStream(entry).use { it.bufferedReader(Charsets.UTF_8).readText() }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Napier.e("Failed to extract metadata from archive")
            null
        }

    /**
     * Runs [block] on [uri] opened as a [ZipFile], which needs random access.
     *
     * Providers backed by local files hand out a seekable descriptor, opened in place through
     * `/proc/self/fd` so a multi-gigabyte archive is not copied. Streaming providers such as
     * cloud drives return a pipe that cannot be seeked; the archive is copied to the cache for
     * the duration of [block] instead, unless it does not start like a zip at all.
     */
    private suspend fun <T> withRandomAccessZip(
        uri: Uri,
        block: (ZipFile) -> T?,
    ): T? {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            val inPlace = runCatching { ZipFile(File("/proc/self/fd/${descriptor.fd}")) }.getOrNull()
            if (inPlace != null) return inPlace.use(block)
        }

        val cached = File.createTempFile("logdate_preview", ".zip", context.cacheDir)
        try {
            val copied = context.contentResolver.openInputStream(uri)?.use { copyIfZip(it, cached) } ?: return null
            if (!copied) return null
            return ZipFile(cached).use(block)
        } finally {
            cached.delete()
        }
    }

    /** Copies [input] to [target] and returns true, or returns false without copying when it does not start like a zip. */
    private suspend fun copyIfZip(
        input: InputStream,
        target: File,
    ): Boolean {
        val header = ByteArray(ZipSignature.LENGTH)
        var filled = 0
        while (filled < header.size) {
            val read = input.read(header, filled, header.size - filled)
            if (read == -1) break
            filled += read
        }
        if (!ZipSignature.isZip(header.copyOf(filled))) return false

        FileOutputStream(target).use { output ->
            output.write(header, 0, filled)
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
            }
        }
        return true
    }

    private companion object {
        const val COPY_BUFFER_BYTES = 64 * 1024
    }
}
