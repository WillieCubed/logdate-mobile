package studio.hypertext.atproto.lexicon

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.streams.asSequence

/**
 * Generates Kotlin models for lexicon documents.
 *
 * Arguments: `<inputs> <outputDir> [packageName] [--namespace-layout] [--record-index <ObjectName>]`.
 *
 * - `inputs` lists lexicon JSON files and directories, separated by the platform path separator.
 *   Directories are read recursively.
 * - Without `--namespace-layout`, every file goes in `packageName`. With it, each document goes in
 *   `packageName` followed by its NSID's namespace, under matching directories.
 * - `--record-index` also writes an object in `packageName` mapping each record type's collection
 *   NSID to its serializer.
 *
 * Every document is checked first, and generation stops on the first invalid one.
 */
public fun main(args: Array<String>) {
    val options = GenerateOptions.parse(args)
    options.inputs.forEach { input ->
        require(Files.exists(input)) { "Missing lexicon input: ${input.invariantSeparatorsPathString}" }
    }
    options.outputDir.createDirectories()

    val documents =
        options.inputs
            .flatMap(::lexiconFiles)
            .sortedBy(Path::nameWithoutExtension)
            .map { path -> LexiconParser.parse(path.readText()) }
    val registry = LexiconRegistry(documents)
    documents.forEach { document ->
        val validation = LexiconValidator.validate(document, registry)
        require(validation.isValid) {
            "Invalid lexicon ${document.id}: ${validation.issues.joinToString { issue -> "${issue.path}: ${issue.message}" }}"
        }
    }

    val layout =
        if (options.namespaceLayout) {
            LexiconPackageLayout.byNamespace(options.packageName)
        } else {
            LexiconPackageLayout.flat(options.packageName)
        }
    documents.forEach { document ->
        val documentName =
            document.id
                .toString()
                .substringAfterLast('.')
                .replaceFirstChar(Char::uppercaseChar)
        val directory = options.outputDir.resolve(layout.packageFor(document.id).packageDirectory(options))
        directory.createDirectories()
        directory.resolve("${documentName}Lexicon.kt").writeText(LexiconCodegen.generate(document, layout))
    }

    options.recordIndex?.let { objectName ->
        val directory = options.outputDir.resolve(options.packageName.packageDirectory(options))
        directory.createDirectories()
        directory
            .resolve("$objectName.kt")
            .writeText(LexiconCodegen.generateRecordIndex(documents, layout, options.packageName, objectName))
    }
}

private class GenerateOptions(
    val inputs: List<Path>,
    val outputDir: Path,
    val packageName: String,
    val namespaceLayout: Boolean,
    val recordIndex: String?,
) {
    companion object {
        fun parse(args: Array<String>): GenerateOptions {
            val positional = mutableListOf<String>()
            var namespaceLayout = false
            var recordIndex: String? = null
            var index = 0
            while (index < args.size) {
                when (val argument = args[index]) {
                    "--namespace-layout" -> namespaceLayout = true
                    "--record-index" -> recordIndex = requireNotNull(args.getOrNull(++index)) { "--record-index needs an object name" }
                    else -> positional += argument
                }
                index++
            }
            require(positional.size >= 2) { "Expected inputs and outputDir arguments" }
            return GenerateOptions(
                inputs = positional[0].split(File.pathSeparatorChar).filter(String::isNotBlank).map(Path::of),
                outputDir = Path.of(positional[1]),
                packageName = positional.getOrNull(2) ?: "studio.hypertext.atproto.lexicon.generated.logdate",
                namespaceLayout = namespaceLayout,
                recordIndex = recordIndex,
            )
        }
    }
}

// The flat layout keeps writing straight into the output directory, as the checked-in models
// expect; the namespace layout writes a directory per package, as Kotlin source sets expect.
private fun String.packageDirectory(options: GenerateOptions): String = if (options.namespaceLayout) replace('.', '/') else ""

private fun lexiconFiles(input: Path): List<Path> =
    if (Files.isDirectory(input)) {
        Files.walk(input).use { paths -> paths.asSequence().filter { Files.isRegularFile(it) && it.extension == "json" }.toList() }
    } else {
        listOf(input)
    }
