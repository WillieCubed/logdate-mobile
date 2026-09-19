package app.logdate.client.domain.export.archive

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ArchivePathTest {
    @Test
    fun `a relative forward slash path is accepted as written`() {
        val path = ArchivePath.parse("media/photos/2026/2026-09-17_21-30-05.jpg")

        assertEquals("media/photos/2026/2026-09-17_21-30-05.jpg", path?.value)
    }

    @Test
    fun `paths that point outside the archive or at a device are rejected`() {
        val rejected =
            listOf(
                "",
                "/etc/passwd",
                "../secrets.txt",
                "media/../../secrets.txt",
                "media/./a.jpg",
                "media//a.jpg",
                "media/a.jpg/",
                "C:\\Users\\me\\a.jpg",
                "C:/Users/me/a.jpg",
                "media\\a.jpg",
                "content://media/external/images/media/1000025292",
                "file:///data/user/0/app.logdate/files/a.jpg",
                "ph://ABC-123/L0/001",
            )

        rejected.forEach { raw -> assertNull(ArchivePath.parse(raw), "should reject '$raw'") }
    }

    @Test
    fun `control characters are rejected`() {
        assertNull(ArchivePath.parse("media/a\u0000.jpg"))
        assertNull(ArchivePath.parse("media/a\tb.jpg"))
        assertNull(ArchivePath.parse("media/a\nb.jpg"))
        assertNull(ArchivePath.parse("media/a\u007Fb.jpg"))
    }

    @Test
    fun `paths and segments over the length budget are rejected`() {
        val longSegment = "a".repeat(ArchivePath.MAX_SEGMENT_LENGTH + 1)
        val longPath = List(10) { "d".repeat(15) }.joinToString("/") + "/f.jpg"

        assertNull(ArchivePath.parse("media/$longSegment.jpg"))
        assertNull(ArchivePath.parse(longPath))
        assertNotNull(ArchivePath.parse("a".repeat(ArchivePath.MAX_SEGMENT_LENGTH)))
    }

    @Test
    fun `of throws for a path that parse would reject`() {
        assertFailsWith<IllegalArgumentException> { ArchivePath.of("../x") }
    }

    @Test
    fun `a path is written to json as a plain string`() {
        val json = Json.encodeToString(ArchivePath.serializer(), ArchivePath.of("data/notes.json"))

        assertEquals("\"data/notes.json\"", json)
    }

    @Test
    fun `reading json that holds an unsafe path fails`() {
        assertFailsWith<SerializationException> {
            Json.decodeFromString(ArchivePath.serializer(), "\"../../etc/passwd\"")
        }
        assertFailsWith<SerializationException> {
            Json.decodeFromString(ArchivePath.serializer(), "\"content://media/1\"")
        }
    }

    @Test
    fun `the parent directory and file name are available`() {
        val path = ArchivePath.of("media/photos/2026/a.jpg")

        assertEquals("media/photos/2026", path.directory)
        assertEquals("a.jpg", path.fileName)
        assertEquals("", ArchivePath.of("README.txt").directory)
    }
}
