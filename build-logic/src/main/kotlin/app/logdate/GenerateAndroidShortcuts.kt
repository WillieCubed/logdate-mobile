package app.logdate

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Shortcut intent packages are literal strings; Android does not resolve resource references. */
@CacheableTask
abstract class GenerateAndroidShortcuts : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val template: RegularFileProperty

    @get:Input
    abstract val applicationId: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val xml = androidShortcutXml(template.get().asFile.readText(), applicationId.get())
        val output = outputDirectory.get().asFile.resolve("xml/shortcuts.xml")
        output.parentFile.mkdirs()
        output.writeText(xml)
    }
}

internal fun androidShortcutXml(
    template: String,
    applicationId: String,
): String {
    require(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(applicationId)) {
        "Shortcut targets require a literal application ID"
    }
    require(template.contains("@APPLICATION_ID@")) { "Shortcut template has no application ID placeholder" }
    return template.replace("@APPLICATION_ID@", applicationId)
}

internal fun configureAndroidShortcuts(project: Project) {
    val inputTemplate = project.layout.projectDirectory.file("src/main/shortcut-templates/shortcuts.xml")
    if (!inputTemplate.asFile.isFile) return
    project.pluginManager.withPlugin("com.android.application") {
        val components = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
        components.onVariants(components.selector().all()) { variant ->
            val task =
                project.tasks.register(
                    "generate${variant.name.replaceFirstChar(Char::uppercase)}NativeShortcuts",
                    GenerateAndroidShortcuts::class.java,
                ) {
                    template.set(inputTemplate)
                    applicationId.set(variant.applicationId)
                    outputDirectory.set(project.layout.buildDirectory.dir("generated/nativeShortcuts/${variant.name}/res"))
                }
            variant.sources.res?.addGeneratedSourceDirectory(task, GenerateAndroidShortcuts::outputDirectory)
        }
    }
}
