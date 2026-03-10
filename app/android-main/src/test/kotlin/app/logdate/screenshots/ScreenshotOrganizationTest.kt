package app.logdate.screenshots

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreenshotOrganizationTest {
    private val repoRoot = locateRepoRoot(Paths.get(System.getProperty("user.dir")).toAbsolutePath())
    private val screenshotsDir =
        repoRoot.resolve("app/android-main/src/screenshotTest/kotlin/app/logdate/screenshots")

    @Test
    fun screenshotTreeUsesApprovedTopLevelBuckets() {
        assertTrue(screenshotsDir.exists(), "Missing screenshot source directory: $screenshotsDir")

        val topLevelDirectories =
            Files
                .list(screenshotsDir)
                .use { paths ->
                    paths
                        .filter { it.isDirectory() }
                        .map(Path::name)
                        .sorted()
                        .toList()
                }

        assertEquals(
            listOf("audit", "common", "components", "flows"),
            topLevelDirectories,
        )
    }

    @Test
    fun flowScreenshotFilesUseOrderedNamesAndPreviewPrefixes() {
        val flowDirectories =
            Files
                .walk(screenshotsDir.resolve("flows"), 1)
                .use { paths ->
                    paths
                        .filter { it != screenshotsDir.resolve("flows") && it.isDirectory() }
                        .sorted()
                        .toList()
                }

        assertTrue(flowDirectories.isNotEmpty(), "Expected at least one flow screenshot directory")

        flowDirectories.forEach { flowDirectory ->
            assertTrue(
                flowDirectory.name.matches(Regex("""flow\d{2}_[a-z0-9_]+""")),
                "Flow directory must be ordered and readable: $flowDirectory",
            )

            Files.list(flowDirectory).use { paths ->
                paths
                    .filter { it.extension == "kt" }
                    .sorted()
                    .forEach { file ->
                        assertTrue(
                            file.name.matches(Regex("""Flow\d{2}_\d{2}_[A-Za-z0-9]+Screenshots\.kt""")),
                            "Flow screenshot file must use FlowNN_MM_NameScreenshots.kt: $file",
                        )

                        val previewNames =
                            Regex("""fun\s+(S\d{2}_[A-Za-z0-9]+)\s*\(""")
                                .findAll(file.readText())
                                .map { it.groupValues[1] }
                                .toList()

                        assertTrue(previewNames.isNotEmpty(), "Expected preview functions in $file")
                        assertTrue(
                            previewNames == previewNames.sorted(),
                            "Flow preview functions must sort in review order: $file",
                        )
                    }
            }
        }
    }

    @Test
    fun auditScreenshotsUseAdaptiveBucketAndOrderedPreviewPrefixes() {
        val auditDirectory = screenshotsDir.resolve("audit/adaptive")
        assertTrue(auditDirectory.exists(), "Missing adaptive audit directory: $auditDirectory")

        Files.list(auditDirectory).use { paths ->
            paths
                .filter { it.extension == "kt" }
                .sorted()
                .forEach { file ->
                    val previewNames =
                        Regex("""fun\s+(A\d{2}_[A-Za-z0-9]+)\s*\(""")
                            .findAll(file.readText())
                            .map { it.groupValues[1] }
                            .toList()

                    assertTrue(previewNames.isNotEmpty(), "Expected adaptive audit previews in $file")
                    assertTrue(
                        previewNames == previewNames.sorted(),
                        "Adaptive audit previews must sort in review order: $file",
                    )
                }
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
}
