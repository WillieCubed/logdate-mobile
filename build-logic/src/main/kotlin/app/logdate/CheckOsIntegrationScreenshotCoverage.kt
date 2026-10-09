package app.logdate

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * Fails when an OS integration feature is missing a screenshot its registry entry requires, when an
 * OS integration preview is not registered, or when a phone screenshot reference has no test.
 *
 * See [ScreenshotCoverage] for the registry format and `docs/testing/screenshot-tests.md` for the
 * workflow.
 */
abstract class CheckOsIntegrationScreenshotCoverage : DefaultTask() {
    @get:Internal abstract val registry: RegularFileProperty

    @get:Internal abstract val previewMatrixSource: RegularFileProperty

    @get:Internal abstract val repoRoot: DirectoryProperty

    @get:Internal abstract val androidSources: DirectoryProperty

    @get:Internal abstract val androidReferences: DirectoryProperty

    @get:Internal abstract val desktopReferences: DirectoryProperty

    /** The phone screenshot package whose previews must all be registered. */
    @get:Input abstract val osIntegrationPackage: Property<String>

    init {
        doNotTrackState("Reads every reference image named in the registry, which can change outside Gradle")
    }

    @TaskAction fun check() {
        val roots =
            ScreenshotCoverage.Roots(
                repoRoot = repoRoot.get().asFile,
                androidSources = androidSources.get().asFile,
                androidReferences = androidReferences.get().asFile,
                desktopReferences = desktopReferences.get().asFile,
            )
        val requirements = ScreenshotCoverage.parseRegistry(registry.get().asFile.readLines())
        val matrixSource = previewMatrixSource.get().asFile.readText()

        val problems =
            ScreenshotCoverage.verify(requirements, roots, matrixSource) +
                ScreenshotCoverage
                    .unregisteredOsIntegrationPreviews(requirements, roots, osIntegrationPackage.get())
                    .map { "$it uses @OsIntegrationPreviewMatrix but is not in the registry" } +
                ScreenshotCoverage
                    .staleAndroidReferences(roots)
                    .map { "$it has no matching screenshot test; delete it or restore its test" }

        if (problems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("OS integration screenshot coverage failed (${problems.size} problems):")
                    problems.forEach { appendLine("  - $it") }
                    append("Registry: ${registry.get().asFile.relativeTo(roots.repoRoot)}")
                },
            )
        }
        logger.lifecycle("OS integration screenshot coverage: ${requirements.size} requirements met, no stale references")
    }
}
