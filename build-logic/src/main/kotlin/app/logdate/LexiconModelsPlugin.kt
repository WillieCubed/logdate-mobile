package app.logdate

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import java.io.File
import javax.inject.Inject

/**
 * Generates Kotlin models from lexicon schemas on every build, into a source set of a Kotlin
 * Multiplatform module. Nothing generated is checked in: a schema change reaches the code the
 * next time anything compiles, and an invalid schema fails the build.
 *
 * ```kotlin
 * plugins { id("app.logdate.lexicon-models") }
 *
 * lexiconModels {
 *     schemas.from(rootProject.file("lexicons/app/logdate"))
 *     basePackage = "app.logdate.lexicon"
 *     recordIndex = "LogDateRecords"
 * }
 * ```
 *
 * The generator is the JVM build of `:shared:atproto-lexicon`, which the plugin resolves for
 * itself; that module, which can't depend on itself, sets [LexiconModelsExtension.generator].
 */
class LexiconModelsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("lexiconModels", LexiconModelsExtension::class.java)
        extension.namespaceLayout.convention(true)
        extension.sourceSet.convention("commonMain")
        if (project.path != GENERATOR_PROJECT) extension.generator.from(generatorClasspath(project))

        val generate =
            project.tasks.register("generateLexiconModels", GenerateLexiconModels::class.java) {
                group = "codegen"
                description = "Generate Kotlin models from this module's lexicon schemas."
                schemas.from(extension.schemas)
                generator.from(extension.generator)
                basePackage.set(extension.basePackage)
                namespaceLayout.set(extension.namespaceLayout)
                recordIndex.set(extension.recordIndex)
                outputDirectory.set(project.layout.buildDirectory.dir("generated/lexicon/kotlin"))
            }

        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            // The source set is read once the build script has configured the extension.
            project.afterEvaluate {
                val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
                kotlin.sourceSets.getByName(extension.sourceSet.get()).kotlin.srcDir(generate.flatMap { it.outputDirectory })
            }
        }
    }

    private fun generatorClasspath(project: Project) =
        project.configurations.create("lexiconGenerator") {
            isCanBeConsumed = false
            isCanBeResolved = true
            attributes {
                attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
                attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
                attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category::class.java, Category.LIBRARY))
            }
            dependencies.add(project.dependencies.project(mapOf("path" to GENERATOR_PROJECT)))
        }

    companion object {
        /** The module whose JVM build is the generator. */
        const val GENERATOR_PROJECT = ":shared:atproto-lexicon"
    }
}

/** Configures [LexiconModelsPlugin]. */
abstract class LexiconModelsExtension {
    /** Lexicon JSON files, or directories read recursively. */
    abstract val schemas: ConfigurableFileCollection

    /**
     * The package for the generated code. With [namespaceLayout], each document goes in this
     * package followed by its NSID's namespace; without it, every document goes in this package.
     */
    abstract val basePackage: Property<String>

    /** Whether to give each NSID namespace its own package. Defaults to true. */
    abstract val namespaceLayout: Property<Boolean>

    /** When set, also writes an object of this name mapping each record type to its serializer. */
    abstract val recordIndex: Property<String>

    /** The Kotlin source set the generated code joins. Defaults to `commonMain`. */
    abstract val sourceSet: Property<String>

    /** The generator's runtime classpath. Set automatically outside the generator's own module. */
    abstract val generator: ConfigurableFileCollection
}

/** Runs the lexicon generator, replacing everything it wrote before. */
@CacheableTask
abstract class GenerateLexiconModels : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val schemas: ConfigurableFileCollection

    @get:Classpath
    abstract val generator: ConfigurableFileCollection

    @get:Input
    abstract val basePackage: Property<String>

    @get:Input
    abstract val namespaceLayout: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val recordIndex: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val exec: ExecOperations

    @get:Inject
    abstract val files: FileSystemOperations

    @TaskAction
    fun generate() {
        val output = outputDirectory.get().asFile
        // Start empty, so code for a removed schema doesn't linger.
        files.delete { delete(output) }
        output.mkdirs()
        exec.javaexec {
            classpath = generator
            mainClass.set(GENERATOR_MAIN_CLASS)
            args(
                lexiconGeneratorArguments(
                    schemas = schemas.files,
                    outputDirectory = output,
                    basePackage = basePackage.get(),
                    namespaceLayout = namespaceLayout.get(),
                    recordIndex = recordIndex.orNull,
                ),
            )
        }
    }

    private companion object {
        const val GENERATOR_MAIN_CLASS = "studio.hypertext.atproto.lexicon.GenerateLexiconsKt"
    }
}

/** The command-line arguments the generator's `main` takes. */
fun lexiconGeneratorArguments(
    schemas: Collection<File>,
    outputDirectory: File,
    basePackage: String,
    namespaceLayout: Boolean,
    recordIndex: String?,
): List<String> =
    buildList {
        add(schemas.sortedBy { it.path }.joinToString(File.pathSeparator) { it.absolutePath })
        add(outputDirectory.absolutePath)
        add(basePackage)
        if (namespaceLayout) add("--namespace-layout")
        recordIndex?.let {
            add("--record-index")
            add(it)
        }
    }
