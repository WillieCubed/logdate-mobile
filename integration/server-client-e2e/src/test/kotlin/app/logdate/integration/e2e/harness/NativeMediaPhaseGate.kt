package app.logdate.integration.e2e.harness

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** Test-server boundary after entry media recovery and before the full worker has finished. */
internal class NativeMediaPhaseGate(
    private val control: Path,
) {
    suspend fun beforeRequest(path: String) {
        if (path.startsWith("/api/v1/media/") && Files.isRegularFile(control.resolve("media-gate-arm"))) {
            Files.writeString(control.resolve("media-request-paths.log"), path + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        }
        if (path != "/api/v1/drafts/changes" || !Files.isRegularFile(control.resolve("media-gate-arm"))) return
        try {
            Files.createFile(control.resolve("media-gate-held"))
        } catch (_: FileAlreadyExistsException) {
            return
        }
        withTimeout(45_000) {
            while (!Files.isRegularFile(control.resolve("media-gate-release"))) delay(50)
        }
    }
}
