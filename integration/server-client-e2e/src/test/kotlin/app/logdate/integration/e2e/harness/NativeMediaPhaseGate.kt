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
        if (path != "/api/v1/drafts/changes") return
        for (prefix in listOf("media", "media-upload")) {
            if (!Files.isRegularFile(control.resolve("$prefix-gate-arm"))) continue
            try {
                Files.createFile(control.resolve("$prefix-gate-held"))
            } catch (_: FileAlreadyExistsException) {
                continue
            }
            // The host has 90s to acknowledge policy changes; the app then has 30s to react.
            withTimeout(150_000) {
                while (!Files.isRegularFile(control.resolve("$prefix-gate-release"))) delay(50)
            }
        }
    }
}
