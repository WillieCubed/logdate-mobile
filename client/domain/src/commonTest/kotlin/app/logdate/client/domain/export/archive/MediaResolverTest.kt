package app.logdate.client.domain.export.archive

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import okio.Buffer
import okio.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class MediaResolverTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(40)
    private val isomAudio = byteArrayOf(0, 0, 0, 0x18) + "ftypisom".encodeToByteArray() + ByteArray(30)
    private val capture = Instant.parse("2026-09-18T03:30:05Z")
    private val denver = TimeZone.of("America/Denver")

    private class FakeOpener(
        private val files: Map<String, ByteArray>,
        private val failing: Map<String, Throwable> = emptyMap(),
    ) : MediaSourceOpener {
        val opened = mutableListOf<String>()

        override suspend fun open(reference: String): Source? {
            opened += reference
            failing[reference]?.let { throw it }
            return files[reference]?.let { Buffer().write(it) }
        }
    }

    private fun resolver(opener: MediaSourceOpener) = MediaResolver(opener, MediaFileNamer(ArchivePathAllocator()))

    private fun request(
        reference: String,
        kind: MediaKind = MediaKind.PHOTO,
        at: Instant = capture,
    ) = MediaRequest(reference, kind, at, denver)

    @Test
    fun `a readable file is filed under a name made from its capture time and its real type`() =
        runTest {
            val resolution =
                resolver(FakeOpener(mapOf("content://media/external/images/media/1000025292" to jpeg))).resolve(
                    listOf(request("content://media/external/images/media/1000025292")),
                )

            val included = assertIs<ResolvedMedia.Included>(resolution["content://media/external/images/media/1000025292"])
            assertEquals("media/photos/2026/2026-09-17_21-30-05.jpg", included.path.value)
            assertEquals("image/jpeg", included.type.mimeType)
        }

    @Test
    fun `a file that cannot be found is omitted as unreadable`() =
        runTest {
            val resolution = resolver(FakeOpener(emptyMap())).resolve(listOf(request("/data/user/0/app/files/gone.jpg")))

            assertEquals(ResolvedMedia.Omitted(ArchiveOmissionReason.UNREADABLE), resolution["/data/user/0/app/files/gone.jpg"])
            assertTrue(resolution.files.isEmpty())
        }

    @Test
    fun `a file that fails to open is omitted instead of failing the export`() =
        runTest {
            val opener = FakeOpener(emptyMap(), failing = mapOf("broken" to IllegalStateException("disk error")))

            val resolution = resolver(opener).resolve(listOf(request("broken")))

            assertEquals(ResolvedMedia.Omitted(ArchiveOmissionReason.UNREADABLE), resolution["broken"])
        }

    @Test
    fun `cancellation is not swallowed as an unreadable file`() =
        runTest {
            val opener = FakeOpener(emptyMap(), failing = mapOf("a" to CancellationException("stop")))

            assertFailsWith<CancellationException> { resolver(opener).resolve(listOf(request("a"))) }
        }

    @Test
    fun `the same reference used twice becomes one file`() =
        runTest {
            val opener = FakeOpener(mapOf("shared" to jpeg))

            val later = capture + 1.hours

            val resolution = resolver(opener).resolve(listOf(request("shared"), request("shared", at = later)))

            assertEquals(1, resolution.files.size)
            assertEquals(1, opener.opened.size, "a shared file is only opened once")
        }

    @Test
    fun `different files taken in the same second get numbered names`() =
        runTest {
            val opener = FakeOpener(mapOf("a" to jpeg, "b" to jpeg))

            val resolution = resolver(opener).resolve(listOf(request("a"), request("b")))

            assertEquals(
                listOf("media/photos/2026/2026-09-17_21-30-05.jpg", "media/photos/2026/2026-09-17_21-30-05_2.jpg"),
                resolution.files.map { it.path.value },
            )
        }

    @Test
    fun `an mpeg-4 container that holds an audio note is filed as m4a`() =
        runTest {
            val resolution = resolver(FakeOpener(mapOf("voice" to isomAudio))).resolve(listOf(request("voice", MediaKind.AUDIO)))

            val included = assertIs<ResolvedMedia.Included>(resolution["voice"])
            assertEquals("media/audio/2026/2026-09-17_21-30-05.m4a", included.path.value)
        }

    @Test
    fun `a capture requested as a photo that is really a video is filed with the videos`() =
        runTest {
            val mp4 = byteArrayOf(0, 0, 0, 0x18) + "ftypmp42".encodeToByteArray() + ByteArray(30)

            val resolution = resolver(FakeOpener(mapOf("capture" to mp4))).resolve(listOf(request("capture", MediaKind.PHOTO)))

            val included = assertIs<ResolvedMedia.Included>(resolution["capture"])
            assertEquals("media/videos/2026/2026-09-17_21-30-05.mp4", included.path.value)
        }

    @Test
    fun `no resolved path or type ever contains the device reference`() =
        runTest {
            val reference = "content://media/external/images/media/1000025292"

            val resolution = resolver(FakeOpener(mapOf(reference to jpeg))).resolve(listOf(request(reference)))

            val included = assertIs<ResolvedMedia.Included>(resolution[reference])
            assertTrue("1000025292" !in included.path.value)
            assertTrue("content:" !in included.path.value)
        }

    @Test
    fun `an unrecognised file with a known extension keeps that extension`() =
        runTest {
            val resolution =
                resolver(
                    FakeOpener(mapOf("/files/recording.m4a" to ByteArray(40))),
                ).resolve(listOf(request("/files/recording.m4a", MediaKind.AUDIO)))

            val included = assertIs<ResolvedMedia.Included>(resolution["/files/recording.m4a"])
            assertEquals("m4a", included.path.fileName.substringAfterLast('.'))
        }
}
