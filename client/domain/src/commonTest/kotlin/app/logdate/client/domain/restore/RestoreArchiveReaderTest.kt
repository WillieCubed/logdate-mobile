package app.logdate.client.domain.restore

import app.logdate.client.domain.export.archive.ArchiveContent
import app.logdate.client.domain.export.archive.ArchiveJson
import app.logdate.client.domain.export.archive.ArchiveManifest
import app.logdate.client.domain.export.archive.ArchivePath
import app.logdate.client.domain.export.archive.ArchiveRole
import app.logdate.client.domain.export.archive.support.ArchiveSamples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class RestoreArchiveReaderTest {
    @Test
    fun `v2 archive resolves data files by manifest role`() {
        val journalsPath = ArchivePath.of("data/custom-journals.json")
        val manifest =
            ArchiveSamples.manifest.copy(
                contents =
                    listOf(
                        ArchiveContent(
                            role = ArchiveRole.JOURNALS,
                            path = journalsPath,
                            mediaType = "application/json",
                        ),
                    ),
            )
        val manifestJson = ArchiveJson.document.encodeToString(ArchiveManifest.serializer(), manifest)
        val files =
            mapOf(
                "wrapped/manifest.json" to manifestJson,
                "wrapped/${journalsPath.value}" to "{\"journals\":[]}",
            )

        val archive = RestoreArchiveReader.read(files.keys, files::get)

        val v2 = assertIs<RestoreArchiveBundle.V2>(archive)
        assertEquals(manifestJson, v2.bundle.manifestJson)
        assertEquals("{\"journals\":[]}", v2.bundle.journalsJson)
        assertEquals(manifestJson, RestoreArchiveReader.previewJson(files.keys, files::get))
    }

    @Test
    fun `v1 archive remains readable`() {
        val files =
            mapOf(
                "metadata.json" to "metadata",
                "journals.json" to "journals",
                "notes.json" to "notes",
                "journal_notes.json" to "links",
                "drafts.json" to "drafts",
            )

        val archive = RestoreArchiveReader.read(files.keys, files::get)

        val v1 = assertIs<RestoreArchiveBundle.V1>(archive)
        assertEquals("metadata", v1.bundle.metadataJson)
        assertEquals("notes", v1.bundle.notesJson)
        assertEquals("metadata", RestoreArchiveReader.previewJson(files.keys, files::get))
    }

    @Test
    fun `v2 archive rejects a manifest-listed file that is missing`() {
        val journalsPath = ArchivePath.of("data/custom-journals.json")
        val manifest =
            ArchiveSamples.manifest.copy(
                contents =
                    listOf(
                        ArchiveContent(
                            role = ArchiveRole.JOURNALS,
                            path = journalsPath,
                            mediaType = "application/json",
                        ),
                    ),
            )
        val manifestJson = ArchiveJson.document.encodeToString(ArchiveManifest.serializer(), manifest)

        val failure =
            assertFailsWith<IllegalStateException> {
                RestoreArchiveReader.read(mapOf("manifest.json" to manifestJson).keys) { path ->
                    if (path == "manifest.json") manifestJson else null
                }
            }

        assertEquals("Missing required file: ${journalsPath.value}", failure.message)
    }

    @Test
    fun `v2 archive validates manifest content even when restore does not consume its role`() {
        val readmePath = ArchivePath.of("guides/start-here.txt")
        val manifest =
            ArchiveSamples.manifest.copy(
                contents =
                    listOf(
                        ArchiveContent(
                            role = ArchiveRole.README,
                            path = readmePath,
                            mediaType = "text/plain",
                        ),
                    ),
            )
        val manifestJson = ArchiveJson.document.encodeToString(ArchiveManifest.serializer(), manifest)

        val failure =
            assertFailsWith<IllegalStateException> {
                RestoreArchiveReader.read(listOf("manifest.json")) { path ->
                    if (path == "manifest.json") manifestJson else null
                }
            }

        assertEquals("Missing required file: ${readmePath.value}", failure.message)
    }
}
