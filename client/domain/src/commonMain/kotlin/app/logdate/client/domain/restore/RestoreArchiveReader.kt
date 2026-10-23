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

    /** Compatibility for callers already holding exact UTF-8 documents; ZIP readers use [readBytes]. */
    fun read(
        entryNames: Iterable<String>,
        readText: (String) -> String?,
    ): RestoreArchiveBundle = readBytes(entryNames) { readText(it)?.encodeToByteArray() }

    /** Reads the original uncompressed entry bytes before decoding text. */
    fun readBytes(
        entryNames: Iterable<String>,
        readEntry: (String) -> ByteArray?,
    ): RestoreArchiveBundle {
        val names = entryNames.toList()
        require(names.filterNot { it.endsWith('/') }.distinct().size == names.count { !it.endsWith('/') }) {
            "Repeated archive entry name"
        }
        val v2Root = ArchiveRoot.find(names, ArchiveLayout.MANIFEST.value)
        if (v2Root != null) {
            requireCanonicalV2EntryNames(names)
            return readV2(v2Root, readEntry)
        }
        val v1Root =
            ArchiveRoot.find(names, ExportFileStructure.METADATA_FILE)
                ?: throw IllegalArgumentException("Archive has no LogDate manifest or metadata")
        return readV1(v1Root) { readEntry(it)?.archiveText() }
    }

    private fun requireCanonicalV2EntryNames(names: List<String>) {
        val identities = mutableSetOf<String>()
        for (name in names) {
            val identity = name.removeSuffix("/")
            require(
                identity.isNotEmpty() &&
                    !identity.startsWith('/') &&
                    '\\' !in identity &&
                    identity.none { it.isISOControl() || it in ":*?\"<>|" } &&
                    identity.split('/').all { it.isNotEmpty() && it != "." && it != ".." && !it.endsWith('.') && !it.endsWith(' ') },
            ) {
                "Noncanonical V2 archive entry name"
            }
            require(identities.add(identity)) { "Ambiguous V2 archive entry name" }
        }
    }

    private fun readV2(
        root: String,
        readEntry: (String) -> ByteArray?,
    ): RestoreArchiveBundle.V2 {
        val manifestBytes = requireNotNull(readEntry(root + ArchiveLayout.MANIFEST.value)) { "Missing archive manifest" }
        val manifestJson = manifestBytes.archiveText()
        ArchiveJsonKeyValidation.requireUnique(manifestJson)
        val manifest = ArchiveJson.document.decodeFromString(ArchiveManifest.serializer(), manifestJson)
        val rawContents =
            manifest.contents.associate { content ->
                val bytes =
                    readEntry(root + content.path.value)
                        ?: if (content.role == ArchiveRole.CHECKSUMS) {
                            throw IllegalArgumentException("Missing archive checksum manifest")
                        } else {
                            throw IllegalStateException("Missing required file: ${root + content.path.value}")
                        }
                content.role to bytes
            }
        val integrity = ArchiveIntegrityAdmission.admit(manifestBytes, manifest, rawContents)

        fun role(role: ArchiveRole): String? = rawContents[role]?.archiveText()
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
                mediaIntegrity = integrity,
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
