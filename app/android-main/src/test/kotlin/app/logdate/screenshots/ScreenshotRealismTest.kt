package app.logdate.screenshots

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Static analysis tests to enforce visual fidelity and realism in the application's
 * screenshot testing suite.
 *
 * This test scans preview and screenshot source files to ensure they avoid synthetic
 * "placeholder" states, blank scaffolding, or fake data labels. By enforcing the use of
 * realistic content in tests, it ensures that visual regressions are caught against
 * production-like UI states rather than idealized or simplified models.
 */
class ScreenshotRealismTest {
    private val repoRoot = locateRepoRoot(Paths.get(System.getProperty("user.dir") ?: ".").toAbsolutePath())
    private val screenshotsDir =
        repoRoot.resolve("app/android-main/src/screenshotTest/kotlin/app/logdate/screenshots")

    @Test
    fun flowScreenshotsAvoidSyntheticScaffoldingAndDebtStates() {
        val violations =
            collectViolations(screenshotsDir.resolve("flows")) { file, contents ->
                buildList {
                    if (file.name.contains("Placeholder") || file.name.contains("Blank")) {
                        add("$file uses placeholder or blank in its file name")
                    }
                    if ("PlaceholderRouteFrame" in contents) {
                        add("$file uses PlaceholderRouteFrame")
                    }
                    if ("Current route is intentionally blank." in contents) {
                        add("$file contains intentionally blank copy")
                    }
                    if ("Toolbar placeholder" in contents) {
                        add("$file contains fake toolbar copy")
                    }
                    if ("Text(\"Timeline\")" in contents || "text = \"Timeline\"" in contents) {
                        add("$file contains a fake timeline label instead of real content")
                    }
                    if (flowPreviewPlaceholderRegex.containsMatchIn(contents)) {
                        add("$file has flow preview names containing Placeholder or Blank")
                    }
                    if ("MediaPlaceholder(" in contents) {
                        add("$file still uses MediaPlaceholder")
                    }
                }
            }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                separator = "\n",
                prefix = "Screenshot flows must stay ecologically valid:\n",
            ),
        )
    }

    @Test
    fun componentScreenshotsAvoidFakeShellCopy() {
        val violations =
            collectViolations(screenshotsDir.resolve("components")) { file, contents ->
                buildList {
                    if (file.name.contains("Placeholder") || file.name.contains("Blank")) {
                        add("$file uses placeholder or blank in its file name")
                    }
                    if ("Toolbar placeholder" in contents) {
                        add("$file contains fake toolbar copy")
                    }
                    if ("Current route is intentionally blank." in contents) {
                        add("$file contains intentionally blank copy")
                    }
                    if ("PlaceholderRouteFrame" in contents) {
                        add("$file uses PlaceholderRouteFrame")
                    }
                    if (componentPreviewPlaceholderRegex.containsMatchIn(contents)) {
                        add("$file has component preview names containing Placeholder or Blank")
                    }
                }
            }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                separator = "\n",
                prefix = "Screenshot components must stay ecologically valid:\n",
            ),
        )
    }

    private fun collectViolations(
        root: Path,
        inspect: (Path, String) -> List<String>,
    ): List<String> {
        assertTrue(root.exists(), "Missing screenshot directory: $root")

        return Files
            .walk(root)
            .use { paths ->
                paths
                    .filter { it.extension == "kt" }
                    .sorted()
                    .flatMap { file ->
                        inspect(file, file.readText()).stream()
                    }.toList()
            }
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

    private companion object {
        val flowPreviewPlaceholderRegex = Regex("""fun\s+S\d{2}_[A-Za-z0-9]*(Placeholder|Blank)\s*\(""")
        val componentPreviewPlaceholderRegex = Regex("""fun\s+[A-Za-z0-9_]*(Placeholder|Blank)\s*\(""")
    }
}
