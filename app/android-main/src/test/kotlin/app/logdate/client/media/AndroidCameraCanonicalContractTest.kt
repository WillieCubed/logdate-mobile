package app.logdate.client.media

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class AndroidCameraCanonicalContractTest {
    private val repoRoot = locateRepoRoot(Paths.get(System.getProperty("user.dir") ?: ".").toAbsolutePath())

    @Test
    fun `camera capture cannot publish directly to media store`() {
        val source =
            repoRoot
                .resolve(
                    "client/feature/editor/src/androidMain/kotlin/app/logdate/feature/editor/ui/camera/" +
                        "AndroidCameraCaptureManager.kt",
                ).readText()
        val violations =
            buildList {
                if ("MediaStore.Images.Media.EXTERNAL_CONTENT_URI" in source) {
                    add("photo capture writes directly to the external MediaStore collection")
                }
                if ("MediaStore.Video.Media.EXTERNAL_CONTENT_URI" in source || "MediaStoreOutputOptions" in source) {
                    add("video capture writes directly to the external MediaStore collection")
                }
                if ("MediaManager" !in source || "saveMediaFromFile" !in source) {
                    add("capture completion is not routed through canonical MediaManager persistence")
                }
            }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                separator = "\n",
                prefix = "Camera capture must be private and canonical before editor acceptance:\n",
            ),
        )
    }

    private fun locateRepoRoot(start: Path): Path {
        var current: Path? = start
        while (current != null) {
            if (current.resolve("settings.gradle.kts").exists()) {
                return current
            }
            current = current.parent
        }
        error("Could not locate repo root from $start")
    }
}
