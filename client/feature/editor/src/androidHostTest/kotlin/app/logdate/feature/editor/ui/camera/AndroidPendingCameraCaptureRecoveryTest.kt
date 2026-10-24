package app.logdate.feature.editor.ui.camera

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidPendingCameraCaptureRecoveryTest {
    @Test
    fun `recovered draft uses transferred original under current installation path`() =
        runTest {
            fixture { files ->
                val capture = CameraCaptureStagingStore(File(files, "camera-captures")).create("LOGDATE_20261024_120000.jpg")
                capture.writeBytes(byteArrayOf(1, 2, 3))
                var selected: String? = null
                val recovery =
                    AndroidPendingCameraCaptureRecovery(
                        files,
                        { path, _, _ ->
                            selected = path
                            "logdate-media://library/recovered.jpg"
                        },
                        complete = { _, _ -> true },
                    )
                val source = "/data/user/10/studio.hypertext.logdate/files/camera-captures/${capture.name}"

                val recovered = recovery.recover(source)

                assertEquals(capture.absolutePath, selected)
                assertEquals("logdate-media://library/recovered.jpg", recovered?.uri)
                assertEquals(CapturedMediaType.PHOTO, recovered?.type)
                assertEquals(true, capture.exists())
            }
        }

    @Test
    fun `missing or foreign capture never becomes a draft claim`() =
        runTest {
            fixture { files ->
                var calls = 0
                val recovery =
                    AndroidPendingCameraCaptureRecovery(
                        files,
                        save = { _, _, _ ->
                            calls++
                            "should-not-publish"
                        },
                    )
                assertNull(recovery.recover("/data/user/0/other.app/files/camera-captures/photo.jpg"))
                assertNull(recovery.recover("/data/user/0/studio.hypertext.logdate/files/camera-captures/missing.jpg.tmp"))
                assertEquals(0, calls)
            }
        }

    private suspend fun fixture(block: suspend (File) -> Unit) {
        val files = Files.createTempDirectory("camera-recovery-").toFile()
        try {
            block(files)
        } finally {
            files.deleteRecursively()
        }
    }
}
