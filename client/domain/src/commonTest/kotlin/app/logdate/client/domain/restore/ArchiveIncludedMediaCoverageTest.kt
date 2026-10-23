package app.logdate.client.domain.restore

import app.logdate.client.domain.export.archive.ArchiveBlockType
import app.logdate.client.domain.export.archive.ArchiveDraftFile
import app.logdate.client.domain.export.archive.ArchiveJson
import app.logdate.client.domain.export.archive.ArchiveMediaRef
import app.logdate.client.domain.export.archive.ArchiveMediaStatus
import app.logdate.client.domain.export.archive.ArchiveNoteFile
import app.logdate.client.domain.export.archive.ArchiveNoteType
import app.logdate.client.domain.export.archive.ArchivePath
import app.logdate.client.domain.export.archive.ArchiveRole
import app.logdate.client.domain.export.archive.support.ArchiveSamples
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ArchiveIncludedMediaCoverageTest {
    @Test
    fun `included note originals require inventory even if ZIP bytes are missing`() {
        for (type in listOf(ArchiveNoteType.IMAGE, ArchiveNoteType.VIDEO, ArchiveNoteType.AUDIO)) {
            for (present in listOf(false, true)) {
                val files = noteFiles(type)
                files["data/media.json"] = """{"files":[]}""".encodeToByteArray()
                sealArchiveIntegrityFixture(files)
                if (!present) files.remove("media/original.bin")
                assertFailsWith<IllegalArgumentException> { RestoreArchiveReader.readBytes(files.keys, files::get) }
            }
        }
    }

    @Test
    fun `included draft originals and independent video thumbnails require inventory`() {
        for (thumbnail in listOf(false, true)) {
            val files = draftFiles(thumbnail)
            files["data/media.json"] = """{"files":[]}""".encodeToByteArray()
            sealArchiveIntegrityFixture(files)
            files.remove("media/original.bin")
            assertFailsWith<IllegalArgumentException> { RestoreArchiveReader.readBytes(files.keys, files::get) }
        }
    }

    @Test
    fun `included media with inventory but missing checksum coverage is refused`() {
        val files = noteFiles(ArchiveNoteType.AUDIO)
        files["SHA256SUMS"] =
            files
                .getValue("SHA256SUMS")
                .decodeToString()
                .lineSequence()
                .filterNot { it.endsWith("  media/original.bin") }
                .joinToString("\n")
                .encodeToByteArray()
        assertFailsWith<IllegalArgumentException> { RestoreArchiveReader.readBytes(files.keys, files::get) }
    }

    @Test
    fun `included media fully described by inventory remains repairable when bytes are absent`() {
        for (files in listOf(noteFiles(ArchiveNoteType.AUDIO), draftFiles(false), draftFiles(true))) {
            files.remove("media/original.bin")
            assertIs<RestoreArchiveBundle.V2>(RestoreArchiveReader.readBytes(files.keys, files::get))
        }
    }

    private fun noteFiles(type: ArchiveNoteType): MutableMap<String, ByteArray> =
        archiveIntegrityFixture().apply {
            val note = ArchiveSamples.note.copy(type = type, media = included())
            addArchiveDocument(
                this,
                ArchiveRole.NOTES,
                "data/notes.json",
                ArchiveJson.document.encodeToString(
                    ArchiveNoteFile.serializer(),
                    ArchiveNoteFile(listOf(note)),
                ),
            )
            sealArchiveIntegrityFixture(this)
        }

    private fun draftFiles(thumbnail: Boolean): MutableMap<String, ByteArray> =
        archiveIntegrityFixture().apply {
            val original = ArchiveSamples.draftFile.drafts.first()
            val block =
                original.blocks.first().copy(
                    type = ArchiveBlockType.VIDEO,
                    media = if (thumbnail) null else included(),
                    thumbnail = if (thumbnail) included() else null,
                )
            addArchiveDocument(
                this,
                ArchiveRole.DRAFTS,
                "data/drafts.json",
                ArchiveJson.document.encodeToString(
                    ArchiveDraftFile.serializer(),
                    ArchiveDraftFile(listOf(original.copy(blocks = listOf(block)))),
                ),
            )
            sealArchiveIntegrityFixture(this)
        }

    private fun included() = ArchiveMediaRef(ArchiveMediaStatus.INCLUDED, ArchivePath.of("media/original.bin"), "application/octet-stream")
}
