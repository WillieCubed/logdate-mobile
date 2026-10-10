package app.logdate

import org.gradle.api.Plugin
import org.gradle.api.Project

/** Registers `checkOsIntegrationScreenshotCoverage` on the root project. */
class ScreenshotCoveragePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("checkOsIntegrationScreenshotCoverage", CheckOsIntegrationScreenshotCoverage::class.java) {
            group = "verification"
            description = "Checks that OS integration features have every required screenshot and that no reference is stale."
            val root = project.layout.projectDirectory
            registry.set(root.file("config/screenshots/os-integration-coverage.txt"))
            previewMatrixSource.set(
                root.file("app/android-main/src/screenshotTest/kotlin/app/logdate/screenshots/common/ScreenshotPreviewMatrix.kt"),
            )
            repoRoot.set(root)
            androidSources.set(root.dir("app/android-main/src/screenshotTest/kotlin"))
            androidReferences.set(root.dir("app/android-main/src/screenshotTestDebug/reference"))
            desktopReferences.set(root.dir("app/compose-main/src/desktopTest/reference"))
            osIntegrationPackage.set("app/logdate/screenshots/flows/flow20_os_integration")
        }
        project.tasks.register("buildScreenshotComparisonSheet", BuildScreenshotComparisonSheet::class.java) {
            group = "verification"
            description = "Writes a labeled comparison sheet of each OS integration feature's screenshots (-Pfeature=<name> for one)."
            val root = project.layout.projectDirectory
            registry.set(root.file("config/screenshots/os-integration-coverage.txt"))
            repoRoot.set(root)
            androidReferences.set(root.dir("app/android-main/src/screenshotTestDebug/reference"))
            desktopReferences.set(root.dir("app/compose-main/src/desktopTest/reference"))
            outputDirectory.set(project.layout.buildDirectory.dir("reports/screenshot-sheets"))
            feature.set(project.providers.gradleProperty("feature"))
        }
    }
}
