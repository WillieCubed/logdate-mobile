package app.logdate.client.data.restore

import app.logdate.client.media.storage.AndroidCanonicalMediaStore
import kotlinx.coroutines.test.runTest
import java.io.File
import java.net.URI
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CanonicalMediaRestoreRoundTripTest {
    @Test
    fun `every canonical media extension survives retained source import`() =
        runTest {
            val root = Files.createTempDirectory("canonical-restore-round-trip").toFile()
            try {
                val senderRoot = File(root, "sender")
                val receiverRoot = File(root, "receiver")
                val sender = AndroidCanonicalMediaStore(senderRoot)
                val receiver = AndroidCanonicalMediaStore(receiverRoot)
                canonicalMimeTypes.forEachIndexed { index, mimeType ->
                    val bytes = "retained original $index $mimeType".encodeToByteArray()
                    val original = File(URI(sender.store(bytes.inputStream(), mimeType, bytes.size.toLong())))
                    val logicalPath = original.relativeTo(senderRoot).path.replace(File.separatorChar, '/')
                    val canonical = "logdate-media://library/" + logicalPath.removePrefix("media/")
                    val asset =
                        assertNotNull(
                            RetainedManagedMediaSource(mapOf("files/$logicalPath" to original)).resolve(canonical),
                            "Unrecognized original from $mimeType: $logicalPath",
                        )
                    val importedUri = asset.file.inputStream().use { input -> receiver.store(input, asset.mimeType, bytes.size.toLong()) }
                    val imported = File(URI(importedUri))
                    assertEquals(
                        original.relativeTo(senderRoot).path,
                        imported.relativeTo(receiverRoot).path,
                        "Canonical path changed for $mimeType",
                    )
                    assertContentEquals(bytes, imported.readBytes(), "Original bytes changed for $mimeType")
                    if (mimeType == "audio/opus") assertEquals("audio/opus", asset.mimeType)
                    // Both media kinds use these extensions. The file digest and path, rather
                    // than an inferred MIME subtype, preserve the exact original on transfer.
                    if (mimeType == "audio/3gpp") assertEquals("video/3gpp", asset.mimeType)
                    if (mimeType == "audio/3gpp2") assertEquals("video/3gpp2", asset.mimeType)
                }
            } finally {
                check(root.deleteRecursively())
            }
        }

    private companion object {
        // Mirrors AndroidCanonicalMediaStore.extensionFor. Aliases share a canonical extension.
        val canonicalMimeTypes =
            listOf(
                "image/jpeg",
                "image/heic",
                "image/heic-sequence",
                "image/heif",
                "image/heif-sequence",
                "image/avif",
                "image/bmp",
                "image/png",
                "image/webp",
                "image/gif",
                "video/mp4",
                "video/mpeg",
                "video/ogg",
                "video/webm",
                "video/quicktime",
                "video/3gpp",
                "video/3gpp2",
                "video/x-matroska",
                "video/x-msvideo",
                "video/x-ms-wmv",
                "audio/mp4",
                "audio/mpeg",
                "audio/ogg",
                "audio/aac",
                "audio/amr",
                "audio/flac",
                "audio/midi",
                "audio/x-midi",
                "audio/opus",
                "audio/3gpp",
                "audio/3gpp2",
                "audio/webm",
                "audio/wav",
                "audio/x-wav",
            )
    }
}
