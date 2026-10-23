package app.logdate.client.domain.restore

import app.logdate.client.domain.export.archive.ArchiveContent
import app.logdate.client.domain.export.archive.ArchiveDraftFile
import app.logdate.client.domain.export.archive.ArchiveJson
import app.logdate.client.domain.export.archive.ArchiveLocationSample
import app.logdate.client.domain.export.archive.ArchiveManifest
import app.logdate.client.domain.export.archive.ArchiveNoteFile
import app.logdate.client.domain.export.archive.ArchiveNoteType
import app.logdate.client.domain.export.archive.ArchivePath
import app.logdate.client.domain.export.archive.ArchiveRole
import app.logdate.client.domain.export.archive.support.ArchiveSamples
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ArchiveAmbiguousDocumentsTest {
    @Test
    fun `self-consistent duplicate manifest fields are refused`() {
        for (key in listOf("format", "for\\u006dat")) {
            val files = archiveIntegrityFixture()
            val text = files.getValue("manifest.json").decodeToString()
            files["manifest.json"] = text.replaceFirst("{", "{\"$key\":\"logdate-export\",").encodeToByteArray()
            sealArchiveIntegrityFixture(files)
            assertFailsWith<IllegalArgumentException> { RestoreArchiveReader.readBytes(files.keys, files::get) }
        }
    }

    @Test
    fun `duplicate record wrappers nested fields and JSONL fields are refused`() {
        val note =
            ArchiveJson.document.encodeToString(
                ArchiveNoteFile.serializer(),
                ArchiveNoteFile(
                    listOf(
                        ArchiveSamples.note.copy(type = ArchiveNoteType.TEXT, media = null),
                    ),
                ),
            )
        val draft = ArchiveSamples.draftFile.drafts.first()
        val draftText =
            ArchiveJson.document.encodeToString(
                ArchiveDraftFile.serializer(),
                ArchiveDraftFile(
                    listOf(
                        draft.copy(blocks = listOf(draft.blocks.first().copy(media = null, thumbnail = null))),
                    ),
                ),
            )
        val location = ArchiveJson.line.encodeToString(ArchiveLocationSample.serializer(), ArchiveSamples.locationSample)
        val records =
            listOf(
                Triple(ArchiveRole.JOURNALS, "data/journals.json", """{"journals":[]}""" to """{"journals":[],"journals":[]}"""),
                Triple(ArchiveRole.NOTES, "data/notes.json", note to duplicateJsonField(note, "caption", "\"Sunset\"", "cap\\u0074ion")),
                Triple(ArchiveRole.DRAFTS, "data/drafts.json", draftText to duplicateJsonField(draftText, "caption", "\"Morning\"")),
                Triple(
                    ArchiveRole.LOCATION_HISTORY,
                    "data/locations.jsonl",
                    location to duplicateJsonField(location, "latitude", "39.7392", "lati\\u0074ude"),
                ),
            )
        for ((role, path, texts) in records) {
            val files = archiveIntegrityFixture()
            addArchiveDocument(files, role, path, texts.first)
            sealArchiveIntegrityFixture(files)
            assertIs<RestoreArchiveBundle.V2>(RestoreArchiveReader.readBytes(files.keys, files::get))
            addArchiveDocument(files, role, path, texts.second)
            sealArchiveIntegrityFixture(files)
            assertFailsWith<IllegalArgumentException> { RestoreArchiveReader.readBytes(files.keys, files::get) }
        }
    }

    @Test
    fun `self-consistent duplicate inventory fields are refused`() {
        for (key in listOf("bytes", "by\\u0074es")) {
            val files = archiveIntegrityFixture()
            val text = files.getValue("data/media.json").decodeToString()
            files["data/media.json"] = duplicateJsonField(text, "bytes", "3", key).encodeToByteArray()
            sealArchiveIntegrityFixture(files)
            assertFailsWith<IllegalArgumentException> { RestoreArchiveReader.readBytes(files.keys, files::get) }
        }
    }
}

internal fun addArchiveDocument(
    files: MutableMap<String, ByteArray>,
    role: ArchiveRole,
    path: String,
    text: String,
) {
    val manifest = ArchiveJson.document.decodeFromString(ArchiveManifest.serializer(), files.getValue("manifest.json").decodeToString())
    val contents =
        manifest.contents.filterNot { it.role == role } +
            ArchiveContent(
                role,
                ArchivePath.of(path),
                if (role == ArchiveRole.LOCATION_HISTORY) "application/x-ndjson" else "application/json",
            )
    files["manifest.json"] =
        ArchiveJson.document.encodeToString(ArchiveManifest.serializer(), manifest.copy(contents = contents)).encodeToByteArray()
    files[path] = text.encodeToByteArray()
}

private fun duplicateJsonField(
    text: String,
    key: String,
    value: String,
    duplicateKey: String = key,
): String {
    val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*${Regex.escape(value)}")
    val match = requireNotNull(pattern.find(text)) { "Synthetic duplicate-field target changed" }
    return text.replaceRange(match.range, "${match.value}, \"$duplicateKey\": $value")
}
