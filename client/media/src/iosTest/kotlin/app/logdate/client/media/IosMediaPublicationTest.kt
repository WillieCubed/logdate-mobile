@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.logdate.client.media

import app.logdate.client.media.storage.MediaCollection
import app.logdate.client.media.storage.MediaDirectories
import app.logdate.client.media.storage.MediaFileResolver
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class IosMediaPublicationTest {
    private val root = "${NSTemporaryDirectory().trimEnd('/')}/logdate-publication-${Uuid.random()}"
    private val library = "$root/library"
    private val fileManager = NSFileManager.defaultManager
    private val mediaFiles =
        MediaFileResolver(
            object : MediaDirectories {
                override fun directory(collection: MediaCollection): String =
                    if (collection ==
                        MediaCollection.Library
                    ) {
                        library
                    } else {
                        "$root/recordings"
                    }
            },
        )

    @AfterTest
    fun removeOwnedRoot() {
        fileManager.removeItemAtPath(root, error = null)
    }

    @Test
    fun `missing copy source throws without returning an accepted media destination`() =
        runTest {
            createRoot()
            assertFailsWith<IOException> {
                IosMediaManager(mediaFiles).saveMediaFromFile("$root/missing.bin", "restored.bin", "application/octet-stream")
            }
            assertTrue(fileManager.contentsOfDirectoryAtPath(library, error = null).orEmpty().isEmpty())
        }

    @Test
    fun `failed copy preserves source and obstructing bytes without accepted destination`() =
        runTest {
            createRoot()
            val bytes = byteArrayOf(1, 3, 5, 7)
            write("$root/source.bin", bytes)
            write(library, byteArrayOf(9))
            assertFailsWith<IOException> {
                IosMediaManager(mediaFiles).saveMediaFromFile("$root/source.bin", "restored.bin", "application/octet-stream")
            }
            assertContentEquals(bytes, read("$root/source.bin"))
            assertContentEquals(byteArrayOf(9), read(library))
            assertEquals(setOf("source.bin", "library"), fileManager.contentsOfDirectoryAtPath(root, error = null).orEmpty().toSet())
        }

    @Test
    fun `failed payload write throws and preserves obstructing destination root`() =
        runTest {
            createRoot()
            write(library, byteArrayOf(8))
            val payload = MediaPayload("restored.bin", "application/octet-stream", 3, byteArrayOf(2, 4, 6))
            assertFailsWith<IOException> { IosMediaManager(mediaFiles).saveMedia(payload) }
            assertContentEquals(byteArrayOf(8), read(library))
            assertEquals(setOf("library"), fileManager.contentsOfDirectoryAtPath(root, error = null).orEmpty().toSet())
        }

    @Test
    fun `valid copy and payload publication retain exact bytes and copy source`() =
        runTest {
            createRoot()
            val bytes = byteArrayOf(0, 1, 2, 3, -1)
            write("$root/source.bin", bytes)
            val manager = IosMediaManager(mediaFiles)
            val copied = manager.saveMediaFromFile("$root/source.bin", "restored.bin", "application/octet-stream")
            val written = manager.saveMedia(MediaPayload("payload.bin", "application/octet-stream", bytes.size.toLong(), bytes))
            assertTrue(manager.exists(copied))
            assertTrue(manager.exists(written))
            assertContentEquals(bytes, manager.readMedia(copied).data)
            assertContentEquals(bytes, manager.readMedia(written).data)
            assertContentEquals(bytes, read("$root/source.bin"))
            assertEquals(2, fileManager.contentsOfDirectoryAtPath(library, error = null).orEmpty().size)
        }

    @Test
    fun `failed partial publication removes only its unique destination`() {
        createRoot()
        write("$root/source.bin", byteArrayOf(1, 2))
        val destination = "$root/owned-${Uuid.random()}.partial"
        assertFailsWith<IOException> {
            publishIosMediaFile(destination) {
                write(destination, byteArrayOf(1))
                false
            }
        }
        assertFalse(fileManager.fileExistsAtPath(destination))
        assertContentEquals(byteArrayOf(1, 2), read("$root/source.bin"))
    }

    @Test
    fun `preexisting destination is preserved and write operation is never invoked`() {
        createRoot()
        val destination = "$root/existing.bin"
        write(destination, byteArrayOf(7))
        var invoked = false
        assertFailsWith<IOException> {
            publishIosMediaFile(destination) {
                invoked = true
                false
            }
        }
        assertFalse(invoked)
        assertContentEquals(byteArrayOf(7), read(destination))
    }

    private fun createRoot() {
        assertTrue(fileManager.createDirectoryAtPath(root, withIntermediateDirectories = true, attributes = null, error = null))
    }

    private fun write(
        path: String,
        bytes: ByteArray,
    ) {
        SystemFileSystem.sink(Path(path)).buffered().use { it.write(bytes) }
    }

    private fun read(path: String): ByteArray = SystemFileSystem.source(Path(path)).buffered().use { it.readByteArray() }
}
