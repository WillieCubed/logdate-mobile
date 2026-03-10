package studio.hypertext.atproto.lexicon

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
 * Regenerates checked-in Kotlin models for a lexicon resource directory.
 */
public fun main(args: Array<String>) {
    require(args.size >= 2) { "Expected inputDir and outputDir arguments" }

    val inputDir = Path.of(args[0])
    val outputDir = Path.of(args[1])
    val packageName = args.getOrNull(2) ?: "studio.hypertext.atproto.lexicon.generated.logdate"

    require(Files.isDirectory(inputDir)) { "Missing lexicon input directory: ${inputDir.invariantSeparatorsPathString}" }
    outputDir.createDirectories()

    val documents =
        Files
            .list(inputDir)
            .use { paths ->
                paths
                    .asSequence()
                    .filter { path -> path.extension == "json" }
                    .sortedBy(Path::nameWithoutExtension)
                    .map { path -> path to LexiconParser.parse(path.readText()) }
                    .toList()
            }
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
