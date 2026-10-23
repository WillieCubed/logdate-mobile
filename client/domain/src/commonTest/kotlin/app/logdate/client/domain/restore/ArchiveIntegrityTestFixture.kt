package app.logdate.client.domain.restore

import app.logdate.client.domain.export.ExportSchemaVersion
import app.logdate.client.domain.export.archive.ArchiveContent
import app.logdate.client.domain.export.archive.ArchiveJson
import app.logdate.client.domain.export.archive.ArchiveManifest
import app.logdate.client.domain.export.archive.ArchiveMediaFile
import app.logdate.client.domain.export.archive.ArchiveMediaInventoryEntry
import app.logdate.client.domain.export.archive.ArchivePath
import app.logdate.client.domain.export.archive.ArchiveRole
import app.logdate.client.domain.export.archive.support.ArchiveSamples
import okio.ByteString.Companion.toByteString

internal fun archiveIntegrityFixture(version: ExportSchemaVersion = ExportSchemaVersion.V2_1): MutableMap<String, ByteArray> {
    val contents =
        listOf(
            ArchiveContent(ArchiveRole.JOURNALS, ArchivePath.of("data/journals.json"), "application/json"),
            ArchiveContent(ArchiveRole.MEDIA_INVENTORY, ArchivePath.of("data/media.json"), "application/json"),
            ArchiveContent(ArchiveRole.CHECKSUMS, ArchivePath.of("SHA256SUMS"), "text/plain"),
        )
    val media = "abc".encodeToByteArray()
    val files =
        linkedMapOf(
            "manifest.json" to
                ArchiveJson.document
                    .encodeToString(
                        ArchiveManifest.serializer(),
                        ArchiveSamples.manifest.copy(
                            schemaVersion = version,
                            contents = contents,
                        ),
                    ).encodeToByteArray(),
            "data/journals.json" to "{\"journals\":[]}".encodeToByteArray(),
            "data/media.json" to
                ArchiveJson.document
                    .encodeToString(
                        ArchiveMediaFile.serializer(),
                        ArchiveMediaFile(
                            listOf(
                                ArchiveMediaInventoryEntry(
                                    ArchivePath.of("media/original.bin"),
                                    "application/octet-stream",
                                    3,
                                    media.toByteString().sha256().hex(),
                                ),
                            ),
                        ),
                    ).encodeToByteArray(),
            "media/original.bin" to media,
        )
    sealArchiveIntegrityFixture(files)
    return files
}

internal fun sealArchiveIntegrityFixture(files: MutableMap<String, ByteArray>) {
    files["SHA256SUMS"] =
        files
            .filterKeys { it != "SHA256SUMS" }
            .entries
            .sortedBy { it.key }
            .joinToString("") {
                "${it.value.toByteString().sha256().hex()}  ${it.key}\n"
            }.encodeToByteArray()
}
