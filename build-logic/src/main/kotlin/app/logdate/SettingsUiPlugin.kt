package app.logdate

import org.gradle.api.Plugin
import org.gradle.api.Project

class SettingsUiPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val parser = project.configurations.create("settingsUiPsiCompiler") { isCanBeConsumed = false }
        project.dependencies.add(parser.name, "org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.21")
        val check = project.tasks.register("checkSettingsUiContract", CheckSettingsUiContract::class.java) {
            group = "verification"
            description = "Checks that settings sections preserve shared row spacing."
            parserClasspath.from(parser)
            sources.from(
                project.rootProject.fileTree("client") {
                    include("**/src/commonMain/**/*.kt")
                },
            )
        }
        project.tasks.matching { it.name == "check" || it.name == "ktlintCheck" }.configureEach { dependsOn(check) }
    }
}
