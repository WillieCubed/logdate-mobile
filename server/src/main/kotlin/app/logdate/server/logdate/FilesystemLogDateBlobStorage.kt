package app.logdate.server.logdate

import io.github.aakira.napier.Napier
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readBytes

/**
 * On-disk [LogDateBlobStorage] for deployments that don't have GCS — the self-host path.
 *
 * Files live under `<rootPath>/<namespace>/<ownerId>/<blobId>`. The layout mirrors the GCS object
 * naming the first-party cloud uses so that signed-URL or path-shape assumptions hold everywhere.
 *
 * This is deliberately simple: no retention, no deduplication, no streaming. A small operator
 * running this against a local disk gets correct behavior for media and backups without needing
 * to stand up Google Cloud Storage. Larger deployments should still use GCS.
 *
 * Signed download URLs aren't meaningful here — there's no external object store to sign against —
 * so `getSignedDownloadUrl` returns the same scheme the media handler uses when no signer is
 * configured: a relative path the client can fetch through the authenticated `/media/.../binary`
 * endpoint.
 */
class FilesystemLogDateBlobStorage(
    private val rootPath: Path,
) : LogDateBlobStorage {
    init {
        rootPath.createDirectories()
    }

    override fun putBlob(request: LogDateBlobWriteRequest): String {
        val relative = relativePath(request.namespace, request.ownerId.toString(), request.blobId)
        val absolute = rootPath.resolve(relative)
        absolute.parent.createDirectories()
        Files.write(absolute, request.bytes)
        return relative
    }

    override fun getBlob(storagePath: String): ByteArray? =
        try {
            rootPath.resolve(storagePath).readBytes()
        } catch (_: NoSuchFileException) {
            null
        } catch (e: Exception) {
            Napier.w("Filesystem blob read failed at $storagePath", e)
            null
        }

    override fun deleteBlob(storagePath: String): Boolean =
        try {
            rootPath.resolve(storagePath).deleteIfExists()
        } catch (e: Exception) {
            Napier.w("Filesystem blob delete failed at $storagePath", e)
            false
        }

    override fun getSignedDownloadUrl(storagePath: String, expirationHours: Long): String = storagePath

    companion object {
        const val ENV_VAR: String = "LOGDATE_BLOB_STORAGE_DIR"

        /**
         * Builds a storage rooted at the path in [ENV_VAR], or returns `null` if the env var is
         * unset so the caller can decide whether to substitute another implementation or fail.
         */
        fun fromEnvironment(readEnv: (String) -> String? = System::getenv): FilesystemLogDateBlobStorage? {
            val raw = readEnv(ENV_VAR)?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val rebuiltPath =
                try {
                    Path.of(raw)
                } catch (e: Exception) {
                    Napier.w("Invalid $ENV_VAR path: $raw", e)
                    return null
                }
            return FilesystemLogDateBlobStorage(rebuiltPath)
        }
    }
}

/** Canonical layout: `<namespace-lower>/<ownerId>/<blobId>`, matching the GCS object naming. */
private fun relativePath(namespace: LogDateBlobNamespace, ownerId: String, blobId: String): String =
    "${namespace.name.lowercase()}/$ownerId/$blobId"
