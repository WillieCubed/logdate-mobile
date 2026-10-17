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
 * Regenerates checked-in Kotlin models for lexicon documents.
 *
 * The first argument lists the inputs, separated by the platform path separator: each is a
 * lexicon JSON file, or a directory whose JSON files (not its subdirectories) are all read.
 */
public fun main(args: Array<String>) {
    require(args.size >= 2) { "Expected inputs and outputDir arguments" }

    val inputs = args[0].split(File.pathSeparatorChar).filter(String::isNotBlank).map(Path::of)
    val outputDir = Path.of(args[1])
    val packageName = args.getOrNull(2) ?: "studio.hypertext.atproto.lexicon.generated.logdate"

    inputs.forEach { input ->
        require(Files.exists(input)) { "Missing lexicon input: ${input.invariantSeparatorsPathString}" }
    }
    outputDir.createDirectories()

    val documents =
        inputs
            .flatMap(::lexiconFiles)
            .sortedBy(Path::nameWithoutExtension)
            .map { path -> path to LexiconParser.parse(path.readText()) }
    val registry = LexiconRegistry(documents.map { it.second })
    documents.forEach { (_, document) ->
        val validation = LexiconValidator.validate(document, registry)
        require(validation.isValid) {
            "Invalid lexicon ${document.id}: ${validation.issues.joinToString { issue -> "${issue.path}: ${issue.message}" }}"
        }
    }

    documents.forEach { (_, document) ->
        val documentName =
            document.id
                .toString()
                .substringAfterLast('.')
                .replaceFirstChar(Char::uppercaseChar)
        val outputFile = outputDir.resolve("${documentName}Lexicon.kt")
        outputFile.writeText(LexiconCodegen.generate(document, packageName))
    }
}

private fun lexiconFiles(input: Path): List<Path> =
    if (Files.isDirectory(input)) {
        Files.list(input).use { paths -> paths.asSequence().filter { it.extension == "json" }.toList() }
    } else {
        listOf(input)
    }
