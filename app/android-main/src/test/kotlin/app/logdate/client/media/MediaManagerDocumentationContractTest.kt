package app.logdate.client.media

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse

class MediaManagerDocumentationContractTest {
    private val repoRoot = locateRepoRoot(Paths.get(System.getProperty("user.dir") ?: ".").toAbsolutePath())

    @Test
    fun saveMediaFromFileCancellationGuaranteeIsNotGlobal() {
        val commonContract =
            repoRoot
                .resolve("client/media/src/commonMain/kotlin/app/logdate/client/media/MediaManager.kt")
                .readText()

        assertFalse(
            "Cancellation before this function returns must not leave a destination asset behind." in
                commonContract,
            "The shared contract cannot promise cancellation cleanup that is only verified on Android",
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
