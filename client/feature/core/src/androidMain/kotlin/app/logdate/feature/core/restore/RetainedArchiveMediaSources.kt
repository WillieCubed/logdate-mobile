package app.logdate.feature.core.restore

import app.logdate.client.domain.restore.ArchiveMediaIntegrity
import app.logdate.client.domain.restore.ArchiveRecoveryMediaIndex
import app.logdate.client.domain.restore.MediaImporter
import app.logdate.client.domain.restore.RestoreArchiveReader
import app.logdate.client.domain.restore.UnavailableMediaAsset
import app.logdate.client.domain.restore.mediaIntegrity
import io.github.aakira.napier.Napier
import java.io.Closeable
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Searches retained archives by attachment identity, without replaying any entry metadata. */
internal class RetainedArchiveMediaSources(
    store: ArchiveRecoveryStore,
    scope: ArchiveRecoveryScope,
    private val recoveryId: String,
    createImporter: (ZipFile, String, Map<String, ArchiveMediaIntegrity>?) -> MediaImporter,
) : Closeable {
    private data class Source(
        val id: String,
        val zip: ZipFile,
        val root: String,
        val index: ArchiveRecoveryMediaIndex,
        val importer: MediaImporter,
    )

    private val sources = mutableListOf<Source>()
    private val imports = mutableMapOf<String, Pair<MediaImporter, String>>()

    init {
        for (record in store.pending(scope).asReversed()) {
            val file = store.source(record.id)
            if (!file.isFile) continue
            var opened: ZipFile? = null
            try {
                val zip = ZipFile(file).also { opened = it }
                val archive =
                    RestoreArchiveReader.readBytes(zip.entries().toList().map { it.name }) { name ->
                        zip.getEntry(name)?.let { zip.getInputStream(it).use { input -> input.readBytes() } }
                    }
                sources +=
                    Source(
                        record.id,
                        zip,
                        archive.root,
                        ArchiveRecoveryMediaIndex(archive),
                        createImporter(zip, archive.root, archive.mediaIntegrity),
                    )
            } catch (error: Exception) {
                opened?.close()
                Napier.w("Retained archive could not be read; other recovery sources remain available")
            }
        }
        sources.sortBy { it.id != recoveryId }
    }

    fun path(asset: UnavailableMediaAsset): String? {
        for (source in sources) {
            if (source.id != recoveryId && asset.expectedSha256 == null) continue
            val path = source.index.path(asset) ?: continue
            if (source.importer.containsMedia(path) != true) continue
            val expectedChecksum = asset.expectedSha256
            if (expectedChecksum != null && !matchesChecksum(source, path, expectedChecksum)) continue
            val token = "${source.id}:$path"
            imports[token] = source.importer to path
            return token
        }
        return null
    }

    private fun matchesChecksum(
        source: Source,
        path: String,
        expected: String,
    ): Boolean =
        runCatching {
            val entry = source.zip.getEntry(source.root + path.trimStart('/')) ?: return@runCatching false
            val digest = MessageDigest.getInstance("SHA-256")
            source.zip.getInputStream(entry).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }.equals(expected, ignoreCase = true)
        }.getOrDefault(false)

    suspend fun importMedia(token: String): String? = imports[token]?.let { (importer, path) -> importer.importMedia(path) }

    override fun close() {
        sources.forEach { it.zip.close() }
    }
}
