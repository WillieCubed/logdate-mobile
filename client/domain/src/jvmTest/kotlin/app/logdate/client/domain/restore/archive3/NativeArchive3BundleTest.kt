package app.logdate.client.domain.restore.archive3

import app.logdate.client.domain.export.archive.ArchiveContainer
import app.logdate.client.domain.export.archive.ArchivePath
import app.logdate.client.domain.export.archive.StagingDirectoryArchiveContainer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.Sink
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NativeArchive3BundleTest {
    @Test
    fun `validated bundle reexports exact source documents and original bytes`() {
        val files = fixture()
        val bundle = read(files)
        val emitted = linkedMapOf<String, ByteArray>()
        bundle.write({ path -> Buffer().write(files.getValue(path)) }, container { path, bytes -> emitted[path] = bytes })
        assertEquals(files.keys, emitted.keys)
        files.forEach { (path, bytes) -> assertContentEquals(bytes, emitted[path], path) }
    }

    @Test
    fun `validated bundle stages a receiver-compatible directory`() {
        val files = populatedFixture()
        val parent = Files.createTempDirectory("logdate-native-archive3-directory").toFile()
        try {
            val root = parent.resolve("archive").absolutePath.toPath()
            read(files).write(
                { path -> Buffer().write(files.getValue(path)) },
                StagingDirectoryArchiveContainer(FileSystem.SYSTEM, root),
            )
            val staged =
                NativeArchive3Bundle.read(
                    files.keys,
                    { path -> FileSystem.SYSTEM.source(root / path) },
                )
            assertEquals(read(files).manifestSha256, staged.manifestSha256)
            assertEquals(read(files).checksumsSha256, staged.checksumsSha256)
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun `unknown versions unsafe paths undeclared resources and corrupt bytes fail admission`() {
        val files = fixture()
        for (version in listOf("2.1", "3.1", "4.0")) {
            val changed = files.toMutableMap()
            changed["manifest.json"] =
                changed
                    .getValue("manifest.json")
                    .decodeToString()
                    .replace("3.0", version)
                    .encodeToByteArray()
            seal(changed)
            assertFailsWith<IllegalArgumentException> { read(changed) }
        }
        for (path in listOf("../escape", "/absolute", "media/a/../x", "media/x.", "media/CON/x", "media\\x")) {
            val changed = files.toMutableMap()
            changed[path] = byteArrayOf(1)
            seal(changed)
            assertFailsWith<IllegalArgumentException>(path) { read(changed) }
        }
        val corrupt = files.toMutableMap()
        corrupt["README.txt"] = byteArrayOf(2)
        assertFailsWith<IllegalArgumentException> { read(corrupt) }
        val extra = files.toMutableMap()
        extra["extra"] = byteArrayOf(1)
        seal(extra)
        assertFailsWith<IllegalArgumentException> { read(extra) }
    }

    @Test
    fun `relationships duplicates unknown fields and source timestamp ambiguity fail before publication`() {
        val files = fixture()
        val journal = Json.parseToJsonElement(files.getValue("data/journals.json").decodeToString()).jsonObject
        val changes =
            listOf(
                "data/journals.json" to journal.toString().replace("[]", "[{},{}]"),
                "data/notes.json" to """{"notes":[{"id":"a"}]}""",
                "data/drafts.json" to """{"drafts":[],"future":true}""",
                "data/media.json" to """{"files":[],"files":[]}""",
            )
        for ((path, text) in changes) {
            val changed = files.toMutableMap()
            changed[path] = text.encodeToByteArray()
            seal(changed)
            assertFailsWith<IllegalArgumentException>(path) { read(changed) }
        }
    }

    @Test
    fun `metadata resource count and available byte budgets are enforced`() {
        val files = fixture()
        assertFailsWith<IllegalArgumentException> {
            NativeArchive3Bundle.read(files.keys, { Buffer().write(files.getValue(it)) }, availableBytes = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            NativeArchive3Bundle.read(files.keys.toList() + "manifest.json", { Buffer().write(files.getValue(it)) })
        }
        val changed = files.toMutableMap()
        changed["manifest.json"] = ByteArray(1_048_577) { 32 }
        seal(changed)
        assertFailsWith<IllegalArgumentException> { read(changed) }
    }

    @Test
    fun `source mutation after admission prevents export before any output`() {
        val files = fixture().toMutableMap()
        val admitted = read(files)
        files["data/notes.json"] = """{"notes":[{}]}""".encodeToByteArray()
        var published = false
        assertFailsWith<IllegalArgumentException> {
            admitted.write({ Buffer().write(files.getValue(it)) }, container { _, _ -> published = true })
        }
        assertEquals(false, published)
    }

    @Test
    fun `original media is streamed and covered by inventory before export`() {
        val files = populatedFixture()
        val admitted = read(files)
        val emitted = linkedMapOf<String, ByteArray>()
        admitted.write({ Buffer().write(files.getValue(it)) }, container { path, bytes -> emitted[path] = bytes })
        files.forEach { (path, bytes) -> assertContentEquals(bytes, emitted[path], path) }
        val corrupt = files.toMutableMap()
        corrupt[MEDIA_PATH] = byteArrayOf(9)
        assertFailsWith<IllegalArgumentException> { read(corrupt) }
        seal(corrupt)
        assertFailsWith<IllegalArgumentException> { read(corrupt) }
    }

    @Test
    fun `dangling membership duplicate identity and unreferenced media are rejected`() {
        for (change in listOf<(MutableMap<String, ByteArray>) -> Unit>(
            {
                it["data/notes.json"] =
                    it
                        .getValue(
                            "data/notes.json",
                        ).decodeToString()
                        .replace(
                            "\"journalIds\":[]",
                            "\"journalIds\":[\"11111111-1111-1111-1111-111111111111\"]",
                        ).encodeToByteArray()
            },
            { it["data/notes.json"] = """{"notes":[]}""".encodeToByteArray() },
            {
                val document = it.getValue("data/notes.json").decodeToString()
                val note = document.substringAfter("[").substringBeforeLast("]")
                it["data/notes.json"] =
                    """{"notes":[$note,$note]}""".encodeToByteArray()
            },
            { it["data/media.json"] = """{"files":[]}""".encodeToByteArray() },
        )) {
            val changed = populatedFixture()
            change(changed)
            seal(changed)
            assertFailsWith<IllegalArgumentException> { read(changed) }
        }
    }

    @Test
    fun `unexpected EOF and write failures never report export success`() {
        val files = populatedFixture()
        val admitted = read(files)
        assertFailsWith<IllegalArgumentException> {
            admitted.write({ path ->
                Buffer().write(
                    if (path ==
                        MEDIA_PATH
                    ) {
                        byteArrayOf()
                    } else {
                        files.getValue(path)
                    },
                )
            }, container { _, _ -> })
        }
        assertFailsWith<IllegalStateException> {
            admitted.write({ Buffer().write(files.getValue(it)) }, container { _, _ -> error("storage full") })
        }
    }

    @Test
    fun `entry collection stops at its declared resource limit`() {
        var produced = 0
        val names =
            Iterable {
                generateSequence {
                    produced++
                    "media/resource-$produced"
                }.iterator()
            }
        assertFailsWith<IllegalArgumentException> { NativeArchive3Bundle.read(names, { error("must not read") }) }
        assertEquals(100001, produced)
    }

    @Test
    fun `source media provenance remains independent from transferred payload inventory`() {
        val files = populatedFixture()
        val text =
            files
                .getValue(
                    "data/notes.json",
                ).decodeToString()
                .replace(
                    "\"caption\":",
                    "\"nativeSourceMetadataPresent\":true," +
                        "\"sourceMedia\":{\"status\":\"included\",\"path\":\"media/old/source.png\"," +
                        "\"mediaType\":\"image/png\"},\"caption\":",
                )
        files["data/notes.json"] = text.encodeToByteArray()
        seal(files)
        assertEquals(1, read(files).notes.size)
    }

    @Test
    fun `derived native timestamps must agree with the retained binary64 observation`() {
        val files = populatedFixture()
        files["data/notes.json"] =
            files
                .getValue("data/notes.json")
                .decodeToString()
                .replace("2026-10-23T00:00:00.123456789-07:00", "1970-01-01T00:00:00.000000000Z")
                .replace(
                    "\"caption\":",
                    "\"nativeSourceMetadataPresent\":false,\"nativeDateSeconds\":0.0," +
                        "\"nativeUpdatedAt\":null,\"nativeText\":\"Original caption\",\"caption\":",
                ).encodeToByteArray()
        seal(files)
        assertEquals(1, read(files).notes.size)
        files["data/notes.json"] =
            files
                .getValue("data/notes.json")
                .decodeToString()
                .replace("1970-01-01T", "1970-01-02T")
                .encodeToByteArray()
        seal(files)
        assertFailsWith<IllegalArgumentException> { read(files) }
    }

    private fun populatedFixture(): MutableMap<String, ByteArray> {
        val files = fixture()
        files[MEDIA_PATH] = ByteArray(32769) { (it % 251).toByte() }
        val hash =
            files
                .getValue(MEDIA_PATH)
                .toByteString()
                .sha256()
                .hex()
        files["data/media.json"] =
            """{"files":[{"path":"$MEDIA_PATH","mediaType":"image/png","bytes":32769,"sha256":"$hash"}]}""".encodeToByteArray()
        files["data/notes.json"] =
            """
            {"notes":[{"id":"22222222-2222-2222-2222-222222222222","type":"image",
            "createdAt":"2026-10-23T00:00:00.123456789-07:00","updatedAt":null,"caption":"Original caption",
            "journalIds":[],"nativePrimaryJournalId":null,
            "media":{"status":"included","path":"$MEDIA_PATH","mediaType":"image/png"}}]}
            """.trimIndent().encodeToByteArray()
        files["manifest.json"] =
            files
                .getValue(
                    "manifest.json",
                ).decodeToString()
                .replace("\"notes\":0", "\"notes\":1")
                .replace("\"media\":0", "\"media\":1")
                .encodeToByteArray()
        seal(files)
        return files
    }

    private companion object {
        const val MEDIA_PATH = "media/notes/22222222-2222-2222-2222-222222222222/original"
    }

    private fun container(receive: (String, ByteArray) -> Unit) =
        object : ArchiveContainer {
            override fun entry(
                path: ArchivePath,
                compress: Boolean,
                write: (Sink) -> Unit,
            ) {
                val buffer = Buffer()
                write(buffer)
                receive(path.value, buffer.readByteArray())
            }
        }

    private fun read(files: Map<String, ByteArray>) = NativeArchive3Bundle.read(files.keys, { Buffer().write(files.getValue(it)) })

    private fun fixture(): MutableMap<String, ByteArray> {
        val files =
            NativeArchive3Schemas.documents
                .mapKeys {
                    "schema/${it.key}.schema.json"
                }.mapValues { it.value.encodeToByteArray() }
                .toMutableMap()
        files["README.txt"] = "Native archive3 fixture\n".encodeToByteArray()
        files["data/journals.json"] = """{"journals":[]}""".encodeToByteArray()
        files["data/notes.json"] = """{"notes":[]}""".encodeToByteArray()
        files["data/drafts.json"] = """{"drafts":[]}""".encodeToByteArray()
        files["data/media.json"] = """{"files":[]}""".encodeToByteArray()
        val contents =
            listOf(
                "readme" to "README.txt",
                "journals" to "data/journals.json",
                "notes" to "data/notes.json",
                "drafts" to "data/drafts.json",
                "mediaInventory" to "data/media.json",
                "checksums" to "SHA256SUMS",
            ).joinToString(",") { (role, path) ->
                val schema =
                    if (path.startsWith(
                            "data/",
                        )
                    ) {
                        ",\"schema\":\"schema/${path.substringAfterLast('/').substringBefore('.')}.schema.json\""
                    } else {
                        ""
                    }
                """{"role":"$role","path":"$path","mediaType":"${if (path.endsWith(
                        "json",
                    )
                ) {
                    "application/json"
                } else {
                    "text/plain"
                }}"$schema}"""
            }
        files["manifest.json"] =
            """
            {"format":"logdate-export","schemaVersion":"3.0","exportedAt":"2026-10-23T00:00:00Z","exportTimeZone":"UTC",
            "generator":{"name":"LogDate","version":"3.0"},"owner":{},"scope":{"complete":true,"omitted":[]},
            "counts":{"journals":0,"notes":0,"drafts":0,"media":0,"places":0,"locationSamples":0,"hasProfile":false},
            "contents":[$contents]}
            """.trimIndent().encodeToByteArray()
        seal(files)
        return files
    }

    private fun seal(files: MutableMap<String, ByteArray>) {
        files["SHA256SUMS"] =
            files
                .filterKeys { it != "SHA256SUMS" }
                .toSortedMap()
                .map { (path, bytes) ->
                    "${bytes.toByteString().sha256().hex()}  $path\n"
                }.joinToString("")
                .encodeToByteArray()
    }
}
