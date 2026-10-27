package app.logdate.client.data.restore

import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.MediaFileSource
import app.logdate.client.media.storage.StoredMediaReferences
import app.logdate.client.media.storage.isRecoveredOriginal
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RetainedManagedMediaSourceTest {
    @Test
    fun `only bytes in the retained generation resolve for managed references`() {
        val root = Files.createTempDirectory("retained-source-media").toFile()
        try {
            val image =
                File(root, "files/media/objects/sha256/ab/photo.jpg").apply {
                    parentFile!!.mkdirs()
                    writeBytes(byteArrayOf(1, 2, 3))
                }
            val recording =
                File(root, "files/audio_notes/recording.m4a").apply {
                    parentFile!!.mkdirs()
                    writeBytes(byteArrayOf(4, 5))
                }
            val source =
                RetainedManagedMediaSource(
                    mapOf("files/media/objects/sha256/ab/photo.jpg" to image, "files/audio_notes/recording.m4a" to recording),
                )
            assertEquals(image.canonicalFile, source.resolve("logdate-media://library/objects/sha256/ab/photo.jpg")?.file)
            assertEquals("audio/mp4", source.resolve("logdate-media://recordings/recording.m4a")?.mimeType)
            assertEquals(
                "image/jpeg",
                source.resolve("file:///data/user/0/studio.hypertext.logdate/files/media/objects/sha256/ab/photo.jpg")?.mimeType,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `native debug file URI and its quarantine marker resolve retained originals`() {
        val root = Files.createTempDirectory("retained-debug-media").toFile()
        try {
            val payload = File(root, "sealed-original").apply { writeText("original bytes") }
            val digest = MessageDigest.getInstance("SHA-256").digest(payload.readBytes()).joinToString("") { "%02x".format(it) }
            val relative = "media/objects/sha256/${digest.take(2)}/$digest.png"
            val source = RetainedManagedMediaSource(mapOf("files/$relative" to payload))
            for (packageName in listOf("studio.hypertext.logdate", "studio.hypertext.logdate.debug", "co.reasonabletech.logdate")) {
                val reference = "file:/data/user/0/$packageName/files/$relative"
                assertEquals(payload.canonicalFile, source.resolve(reference)?.file)
                assertEquals(payload.canonicalFile, source.resolve(retainedUnavailableMediaReference(reference))?.file)
            }
            assertNull(source.resolve("file:/data/user/0/unrelated.app/files/$relative"))
            assertNull(source.resolve("file:/tmp/studio.hypertext.logdate.debug/files/$relative"))
        } finally {
            check(root.deleteRecursively())
        }
    }

    @Test
    fun `canonical private file URI recovers across cohorts only with its exact digest`() {
        val root = Files.createTempDirectory("retained-file-digest").toFile()
        try {
            val payload = File(root, "payload").apply { writeText("original bytes") }
            val digest = MessageDigest.getInstance("SHA-256").digest(payload.readBytes()).joinToString("") { "%02x".format(it) }
            val relative = "media/objects/sha256/${digest.take(2)}/$digest.png"
            val reference = "file:/data/user/0/studio.hypertext.logdate.debug/files/$relative"
            val source = RetainedManagedMediaSource(emptyMap(), listOf(mapOf("files/$relative" to payload)))
            assertEquals(digest, retainedCanonicalMediaDigest(reference))
            assertEquals(payload.canonicalFile, source.resolve(retainedUnavailableMediaReference(reference))?.file)
            payload.writeText("corrupted bytes")
            assertNull(source.resolve(reference))
            assertNull(RetainedManagedMediaSource(mapOf("files/$relative" to payload)).resolve(reference))
            assertNull(retainedCanonicalMediaDigest(reference.replace("studio.hypertext.logdate.debug", "unrelated.app")))
            assertNull(retainedCanonicalMediaDigest(reference.replace("/files/media/", "/files/media/../media/")))
        } finally {
            check(root.deleteRecursively())
        }
    }

    @Test
    fun `incoming opaque payload uses verified logical filename`() {
        val root = Files.createTempDirectory("retained-incoming-media").toFile()
        try {
            val payload = File(root, "payload").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val source = RetainedManagedMediaSource(mapOf("files/media/photo.png" to payload))
            val asset = source.resolve("logdate-media://library/photo.png")
            assertEquals(payload.canonicalFile, asset?.file)
            assertEquals("photo.png", asset?.fileName)
            assertEquals("image/png", asset?.mimeType)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `late media only cohort repairs canonical digest while legacy path cannot cross cohorts`() {
        val root = Files.createTempDirectory("retained-late-media").toFile()
        try {
            val bytes = byteArrayOf(1, 2, 3)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val key = "files/media/objects/sha256/${digest.take(2)}/$digest.png"
            val reference = "logdate-media://library/objects/sha256/${digest.take(2)}/$digest.png"
            val payload = File(root, "payload").apply { writeBytes(bytes) }
            val source = RetainedManagedMediaSource(emptyMap(), listOf(mapOf(key to payload)))
            assertEquals(payload.canonicalFile, source.resolve(reference)?.file)
            payload.writeBytes(byteArrayOf(9, 9, 9))
            assertNull(source.resolve(reference))
            assertNull(
                RetainedManagedMediaSource(
                    emptyMap(),
                    listOf(mapOf("files/media/old.png" to payload)),
                ).resolve("logdate-media://library/old.png"),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `retained collision variant resolves from a later device transfer only with matching bytes`() {
        val root = Files.createTempDirectory("retained-variant-media").toFile()
        try {
            val bytes = "incoming photo".encodeToByteArray()
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val relative = "objects/sha256/${digest.take(2)}/$digest.retained-1.jpg"
            val path = "files/media/$relative"
            val payload = File(root, "sealed-payload").apply { writeBytes(bytes) }
            val reference = "logdate-media://library/$relative"

            assertEquals(payload.canonicalFile, RetainedManagedMediaSource(mapOf(path to payload)).resolve(reference)?.file)
            payload.writeText("different bytes")
            assertNull(RetainedManagedMediaSource(mapOf(path to payload)).resolve(reference))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `active store verification only receives complete canonical digest references`() =
        runTest {
            val digest = "a".repeat(64)
            val canonical = "logdate-media://library/objects/sha256/aa/$digest.png"
            var checks = 0

            suspend fun verify(reference: String): Boolean {
                checks++
                return reference == canonical
            }
            assertEquals(canonical, verifiedCanonicalMediaReference(retainedUnavailableMediaReference(canonical), ::verify))
            assertNull(verifiedCanonicalMediaReference("logdate-media://library/old.png", ::verify))
            assertNull(verifiedCanonicalMediaReference("content://media/external/1", ::verify))
            assertNull(verifiedCanonicalMediaReference("logdate-media://library/objects/sha256/bb/$digest.png", ::verify))
            assertEquals(1, checks)
            assertNull(verifiedCanonicalMediaReference(canonical) { false })
        }

    @Test
    fun `consumed retained bytes reuse a verified live original without quarantining its legacy URI`() =
        runTest {
            val digest = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
            val relative = "objects/sha256/ba/$digest.png"
            val original = "file:/data/user/0/studio.hypertext.logdate.debug/files/media/$relative"
            val canonical = "logdate-media://library/$relative"
            var bytes = "abc"
            val checked = mutableListOf<String>()
            val media =
                object : InMemoryMediaManager() {
                    override suspend fun exists(mediaId: String): Boolean {
                        checked += mediaId
                        return mediaId == canonical
                    }

                    override suspend fun openMedia(uri: String): MediaFileSource {
                        assertEquals(canonical, uri)
                        return MediaFileSource("original.png", "image/png", bytes.length.toLong()) {
                            Buffer().apply { write(bytes.encodeToByteArray()) }
                        }
                    }
                }

            suspend fun verify(reference: String) = media.isRecoveredOriginal(reference, StoredMediaReferences.Unchanged)
            assertNull(RetainedManagedMediaSource(emptyMap()).resolve(original))
            assertEquals(original, verifiedCanonicalMediaReference(original, ::verify))
            assertEquals(original, verifiedCanonicalMediaReference(retainedUnavailableMediaReference(original), ::verify))
            assertEquals(listOf(canonical, canonical), checked)
            bytes = "abd"
            assertNull(verifiedCanonicalMediaReference(original, ::verify))
        }

    @Test
    fun `external content and traversal cannot resolve to retained managed files`() {
        val root = Files.createTempDirectory("retained-source-media").toFile()
        try {
            val image =
                File(root, "files/media/photo.jpg").apply {
                    parentFile!!.mkdirs()
                    writeBytes(byteArrayOf(1))
                }
            val source = RetainedManagedMediaSource(mapOf("files/media/photo.jpg" to image))
            assertNull(source.resolve("content://media/external/images/1"))
            assertNull(source.resolve("logdate-media://library/../photo.jpg"))
            assertNull(source.resolve("file:///tmp/not-owned/media/photo.jpg"))
        } finally {
            root.deleteRecursively()
        }
    }
}
