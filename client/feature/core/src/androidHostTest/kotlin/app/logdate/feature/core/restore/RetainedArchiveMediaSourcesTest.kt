package app.logdate.feature.core.restore

import app.logdate.client.domain.restore.MediaImporter
import app.logdate.client.domain.restore.UnavailableMediaAsset
import app.logdate.client.domain.restore.UnavailableMediaReason
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class RetainedArchiveMediaSourcesTest {
    @Test
    fun `a repeated archive path cannot prove an older original is the expected attachment`() = checkSource(null, false)

    @Test
    fun `another archive can repair a missing original when the bytes match its saved checksum`() = checkSource(hash(byteArrayOf(1)), true)

    @Test
    fun `a declared matching archive path with different bytes is rejected`() = checkSource(hash(byteArrayOf(2)), false)

    private fun checkSource(
        checksum: String?,
        available: Boolean,
    ) {
        val root = Files.createTempDirectory("retained-archive-identity-").toFile()
        try {
            val scope = ArchiveRecoveryScope("owner", "https://example.test")
            val store = ArchiveRecoveryStore(root.resolve("recovery"), root.resolve("cache"))
            val note = Uuid.random().toString()
            val current = Uuid.random().toString()
            val other = Uuid.random().toString()
            val earlier = root.resolve("earlier.zip")
            val latest = root.resolve("latest.zip")
            archive(earlier, note, true)
            archive(latest, note, false)
            store.begin(other, scope, earlier, ImportOptions(), "Earlier")
            store.begin(current, scope, latest, ImportOptions(), "Current")
            val asset =
                UnavailableMediaAsset(
                    note,
                    reference = "missing",
                    reason = UnavailableMediaReason.NOT_IN_ARCHIVE,
                    sourceReference = "media/same-name.png",
                    kind = "image",
                    expectedSha256 = checksum,
                )
            RetainedArchiveMediaSources(store, scope, current) { zip, prefix ->
                object : MediaImporter {
                    override suspend fun importMedia(exportPath: String): String? = error("Indexing must not extract files")

                    override fun containsMedia(exportPath: String) = zip.getEntry(prefix + exportPath) != null
                }
            }.use { sources ->
                if (available) assertNotNull(sources.path(asset)) else assertNull(sources.path(asset))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun archive(
        file: java.io.File,
        id: String,
        includeOriginal: Boolean,
    ) {
        val files =
            mapOf(
                "metadata.json" to "{}",
                "journals.json" to "{\"journals\":[]}",
                "notes.json" to
                    """{"notes":[{"id":"$id","type":"image","mediaPath":"media/same-name.png","createdAt":"2026-01-02T03:04:05Z","updatedAt":"2026-01-02T03:04:05Z"}]}""",
                "drafts.json" to "{\"drafts\":[]}",
                "journal_notes.json" to "{\"journal_notes\":[]}",
            )
        ZipOutputStream(file.outputStream()).use { zip ->
            files.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.encodeToByteArray())
                zip.closeEntry()
            }
            if (includeOriginal) {
                zip.putNextEntry(ZipEntry("media/same-name.png"))
                zip.write(byteArrayOf(1))
                zip.closeEntry()
            }
        }
    }

    private fun hash(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            (it.toInt() and 255).toString(16).padStart(2, '0')
        }
}
