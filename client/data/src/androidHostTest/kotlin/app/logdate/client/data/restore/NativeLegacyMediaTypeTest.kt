package app.logdate.client.data.restore

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NativeLegacyMediaTypeTest {
    @Test
    fun `extensionless png uses authenticated content signature`() =
        fixture { root ->
            val file =
                File(root, "original").apply {
                    writeBytes(
                        byteArrayOf(
                            0x89.toByte(),
                            0x50,
                            0x4e,
                            0x47,
                            0x0d,
                            0x0a,
                            0x1a,
                            0x0a,
                            0,
                            0,
                            0,
                            0,
                        ),
                    )
                }
            assertEquals("image/png", nativeLegacyMediaMimeType(file, "original"))
            assertEquals("image/png", nativeLegacyMediaMimeType(file, "wrong.jpg"))
        }

    @Test
    fun `misnamed private recording uses m4a brand rather than image extension`() =
        fixture { root ->
            val file =
                File(root, "recording").apply {
                    writeBytes(
                        byteArrayOf(0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70, 0x4d, 0x34, 0x41, 0x20, 0, 0, 0, 0),
                    )
                }
            assertEquals("audio/mp4", nativeLegacyMediaMimeType(file, "recording.jpg"))
        }

    @Test
    fun `unknown extensionless bytes and symlinks have no type authority`() =
        fixture { root ->
            val file = File(root, "unknown").apply { writeText("unrecognized") }
            assertNull(nativeLegacyMediaMimeType(file, "unknown"))
            assertNull(nativeLegacyMediaMimeType(file, "misleading.jpg"))
            val link = File(root, "link")
            Files.createSymbolicLink(link.toPath(), file.toPath())
            assertNull(nativeLegacyMediaMimeType(link, "photo.png"))
        }

    private fun fixture(block: (File) -> Unit) {
        val root = Files.createTempDirectory("legacy-mime-").toFile()
        try {
            block(root)
        } finally {
            check(root.deleteRecursively())
        }
    }
}
