package app.logdate.server.atproto.firehose

import app.logdate.lexicon.LogDateLexicons
import kotlinx.serialization.json.JsonObject
import studio.hypertext.atproto.lexicon.LexiconParser
import studio.hypertext.atproto.lexicon.LexiconRecordValidator
import studio.hypertext.atproto.lexicon.LexiconReference
import studio.hypertext.atproto.lexicon.LexiconRegistry
import studio.hypertext.atproto.syntax.Nsid

/** A public record that does not follow its lexicon; [message] lists every problem with the path of the value it concerns. */
class InvalidPublicRecordException(
    message: String,
) : IllegalArgumentException(message)

/**
 * Checks a record against its lexicon before it is written to the public repository.
 *
 * LogDate's own schemas (the profile and the sharing key) come from the lexicons built into the
 * server. The Standard.site schemas for publications and documents are copies kept under
 * `lexicons/site/standard/` in the server's resources, with the `com.atproto.repo.strongRef` they
 * refer to. Update them from the upstream publication when Standard.site changes a schema.
 *
 * A collection with no schema here, one that was opened to apps, is accepted as it is, the way a
 * reference PDS treats a record whose lexicon it does not know. Every collection LogDate publishes
 * has a schema, which [hasSchema] reports and a test checks.
 */
class PublicRecordValidator(
    private val registry: LexiconRegistry = defaultRegistry(),
) {
    private val validator = LexiconRecordValidator(registry)

    /** True when [collection] has a record schema that [validate] enforces. */
    fun hasSchema(collection: Nsid): Boolean = registry.resolve(LexiconReference(collection, MAIN_DEFINITION)) != null

    /**
     * Checks [record] against the schema of [collection].
     *
     * @throws InvalidPublicRecordException listing each problem, when the record does not follow it.
     */
    fun validate(
        collection: Nsid,
        record: JsonObject,
    ) {
        if (!hasSchema(collection)) return
        val issues = validator.validateRecord(collection, record).issues
        if (issues.isEmpty()) return
        val problems = issues.joinToString("; ") { issue -> if (issue.path.isEmpty()) issue.message else "${issue.path}: ${issue.message}" }
        throw InvalidPublicRecordException("The record does not follow the $collection lexicon: $problems")
    }

    companion object {
        private const val MAIN_DEFINITION = "main"

        private val bundledSchemas =
            listOf(
                "com/atproto/repo/strongRef.json",
                "site/standard/publication.json",
                "site/standard/document.json",
                "site/standard/theme/basic.json",
                "site/standard/theme/color.json",
            )

        /** LogDate's schemas plus the bundled Standard.site ones. */
        fun defaultRegistry(): LexiconRegistry =
            LexiconRegistry(LogDateLexicons.registry.documents() + bundledSchemas.map { LexiconParser.parse(readSchema(it)) })

        private fun readSchema(path: String): String {
            val stream =
                checkNotNull(PublicRecordValidator::class.java.classLoader.getResourceAsStream("lexicons/$path")) {
                    "The bundled lexicon lexicons/$path is missing from the server"
                }
            return stream.use { it.readBytes().decodeToString() }
        }
    }
}
