package app.logdate.client.domain.restore

import app.logdate.client.domain.export.ExportFileStructure
import app.logdate.client.domain.export.archive.ArchiveJson
import app.logdate.client.domain.export.archive.ArchiveLayout
import app.logdate.client.domain.export.archive.ArchiveManifest
import app.logdate.client.domain.export.archive.ArchiveRole

sealed interface RestoreArchiveBundle {
    val root: String

    data class V1(
        override val root: String,
        val bundle: RestoreBundle,
    ) : RestoreArchiveBundle

    data class V2(
        override val root: String,
        val bundle: V2RestoreBundle,
    ) : RestoreArchiveBundle
}

/** Shared format detection and file routing for every platform ZIP implementation. */
object RestoreArchiveReader {
    fun previewJson(
        entryNames: Iterable<String>,
        readText: (String) -> String?,
    ): String? {
        val names = entryNames.toList()
        val v2Root = ArchiveRoot.find(names, ArchiveLayout.MANIFEST.value)
        if (v2Root != null) return readText(v2Root + ArchiveLayout.MANIFEST.value)
        val v1Root = ArchiveRoot.find(names, ExportFileStructure.METADATA_FILE) ?: return null
        return readText(v1Root + ExportFileStructure.METADATA_FILE)
    }

    fun read(
        entryNames: Iterable<String>,
        readText: (String) -> String?,
    ): RestoreArchiveBundle {
        val names = entryNames.toList()
        val v2Root = ArchiveRoot.find(names, ArchiveLayout.MANIFEST.value)
        if (v2Root != null) return readV2(v2Root, readText)
        val v1Root =
            ArchiveRoot.find(names, ExportFileStructure.METADATA_FILE)
                ?: throw IllegalArgumentException("Archive has no LogDate manifest or metadata")
        return readV1(v1Root, readText)
    }

    private fun readV2(
        root: String,
        readText: (String) -> String?,
    ): RestoreArchiveBundle.V2 {
        val manifestJson = required(readText, root + ArchiveLayout.MANIFEST.value)
        val manifest = ArchiveJson.document.decodeFromString(ArchiveManifest.serializer(), manifestJson)
        val contents =
            manifest.contents.associate { content ->
                content.role to required(readText, root + content.path.value)
            }

        fun role(role: ArchiveRole): String? = contents[role]
        return RestoreArchiveBundle.V2(
            root,
            V2RestoreBundle(
                manifestJson = manifestJson,
                journalsJson = role(ArchiveRole.JOURNALS),
                notesJson = role(ArchiveRole.NOTES),
                draftsJson = role(ArchiveRole.DRAFTS),
                profileJson = role(ArchiveRole.PROFILE),
                placesJson = role(ArchiveRole.PLACES),
                locationHistoryJsonLines = role(ArchiveRole.LOCATION_HISTORY),
                mediaInventoryJson = role(ArchiveRole.MEDIA_INVENTORY),
            ),
        )
    }

    private fun readV1(
        root: String,
        readText: (String) -> String?,
    ): RestoreArchiveBundle.V1 =
        RestoreArchiveBundle.V1(
            root,
            RestoreBundle(
                metadataJson = required(readText, root + ExportFileStructure.METADATA_FILE),
                journalsJson = required(readText, root + ExportFileStructure.JOURNALS_FILE),
                notesJson = required(readText, root + ExportFileStructure.NOTES_FILE),
                journalNotesJson = required(readText, root + ExportFileStructure.JOURNAL_NOTES_FILE),
                draftsJson = required(readText, root + ExportFileStructure.DRAFTS_FILE),
                profileJson = readText(root + ExportFileStructure.PROFILE_FILE),
                placesJson = readText(root + ExportFileStructure.PLACES_FILE),
                locationHistoryJson = readText(root + ExportFileStructure.LOCATION_HISTORY_FILE),
                mediaManifestJson = readText(root + ExportFileStructure.MEDIA_MANIFEST_FILE),
            ),
        )

    private fun required(
        readText: (String) -> String?,
        path: String,
    ): String = readText(path) ?: throw IllegalStateException("Missing required file: $path")
}
