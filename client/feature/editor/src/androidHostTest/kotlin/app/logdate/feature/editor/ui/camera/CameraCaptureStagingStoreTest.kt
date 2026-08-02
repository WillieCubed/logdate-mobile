package app.logdate.feature.editor.ui.camera

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CameraCaptureStagingStoreTest {
    @Test
    fun `creates and recovers durable photo and video captures`() {
        val root = createTempDirectory("logdate-camera-staging-").toFile()
        try {
            val store = CameraCaptureStagingStore(root)

            val photo = store.create("LOGDATE_20260802_120000.jpg")
            val video = store.create("LOGDATE_20260802_120001.mp4")
            photo.writeBytes(byteArrayOf(1))
            video.writeBytes(byteArrayOf(2))

            assertEquals(
                listOf("image/jpeg", "video/mp4"),
                store.recoverableFiles().map { it.mimeType },
            )
            assertEquals(
                listOf("LOGDATE_20260802_120000.jpg", "LOGDATE_20260802_120001.mp4"),
                store.recoverableFiles().map { it.fileName },
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `does not recover malformed or unsupported files`() {
        val root = createTempDirectory("logdate-camera-staging-").toFile()
        try {
            root.resolve("not-a-capture.tmp").writeBytes(byteArrayOf(1))
            root.resolve("LOGDATE_20260802_120000.txt.00000000-0000-0000-0000-000000000000.tmp")
                .writeBytes(byteArrayOf(1))
            root.resolve("LOGDATE_20260802_120000.jpg.00000000-0000-0000-0000-000000000000.tmp")
                .writeBytes(byteArrayOf(1))

            val recovered = CameraCaptureStagingStore(root).recoverableFiles()

            assertEquals(1, recovered.size)
            assertTrue(recovered.single().fileName.endsWith(".jpg"))
        } finally {
            root.deleteRecursively()
        }
    }
}
