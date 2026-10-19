package app.logdate.lexicon

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import studio.hypertext.atproto.lexicon.LexiconParser
import studio.hypertext.atproto.lexicon.LexiconRecordValidator
import studio.hypertext.atproto.lexicon.LexiconRegistry
import studio.hypertext.atproto.lexicon.LexiconValidationIssue
import studio.hypertext.atproto.lexicon.data.LexiconData
import studio.hypertext.atproto.repo.Cid
import studio.hypertext.atproto.repo.DagCborCodec
import studio.hypertext.atproto.syntax.Nsid
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.streams.asSequence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shared client conformance suite in `conformance/records/`: valid and invalid example records
 * for every `app.logdate.*` record type, generated from the lexicons by
 * `tools/lexicon-check/conformance.mjs`. The Mac and Linux apps run the same files, so what passes
 * here is what every LogDate client must accept and refuse.
 */
class RecordConformanceJvmTest {
    private val validator =
        LexiconRecordValidator(
            LexiconRegistry(
                listOf("lexicons/app/logdate", "lexicons/community")
                    .flatMap { jsonFiles(repositoryRoot().resolve(it)) }
                    .map { LexiconParser.parse(it.readText()) },
            ),
        )

    @Test
    fun `the suite covers every LogDate record type`() {
        val manifest = readJson(conformanceRoot().resolve("manifest.json"))
        val listed = manifest.getValue("recordTypes").jsonArray.map { it.jsonPrimitive.content }
        val generated = LogDateRecords.records.keys.filter { it.startsWith("app.logdate.") }

        assertEquals(generated.sorted(), listed.sorted())
        assertEquals(
            listed.map { "records/$it.json" }.sorted(),
            manifest
                .getValue("files")
                .jsonArray
                .map { it.jsonPrimitive.content }
                .filter { it.startsWith("records/") },
        )
    }

    @Test
    fun `every valid record validates and encodes to the known DAG-CBOR and CID`() {
        forEachSuite { collection, suite ->
            suite.cases("valid").forEach { case ->
                val label = "$collection ${case.name}"
                val record = case.record
                assertEquals(emptyList(), validator.issuesFor(collection, record).map { "${it.path}: ${it.message}" }, "$label: validation")

                val bytes = DagCborCodec.encode(record)
                assertEquals(case.string("dagCbor"), bytes.toHex(), "$label: DAG-CBOR")
                assertEquals(case.string("cid"), Cid.sha256(DAG_CBOR_CODEC, bytes).value, "$label: CID")
                assertEquals(record, DagCborCodec.decode(Hex.decode(case.string("dagCbor"))), "$label: decoding the DAG-CBOR")
            }
        }
    }

    @Test
    fun `every valid record round-trips through its generated model`() {
        forEachSuite { collection, suite ->
            suite.cases("valid").filter { it.boolean("modelRoundTrip") ?: true }.forEach { case ->
                @Suppress("UNCHECKED_CAST")
                val serializer = requireNotNull(LogDateRecords.records[collection.toString()]) as KSerializer<Any>
                val decoded = LexiconData.json.decodeFromJsonElement(serializer, case.record)
                val label = "$collection ${case.name}"

                assertEquals(case.record, LexiconData.json.encodeToJsonElement(serializer, decoded), "$label: JSON")
                assertEquals(case.string("dagCbor"), LexiconData.encodeToDagCbor(serializer, decoded).toHex(), "$label: DAG-CBOR")
            }
        }
    }

    @Test
    fun `every invalid record fails with its labeled failure`() {
        forEachSuite { collection, suite ->
            suite.cases("invalid").forEach { case ->
                val failure = case.value.getValue("failure").jsonObject
                val expected = Failure(failure.getValue("kind").jsonPrimitive.content, failure.getValue("path").jsonPrimitive.content)
                val actual = validator.issuesFor(collection, case.record).map { classify(it) }

                assertTrue(expected in actual, "$collection ${case.name}: expected $expected, the validator reported $actual")
            }
        }
    }

    private fun forEachSuite(check: (Nsid, Suite) -> Unit) {
        val files = jsonFiles(conformanceRoot().resolve("records"))
        assertTrue(files.size > 30, "Expected a suite per record type, found ${files.size}")
        files.forEach { path ->
            val suite = Suite(readJson(path))
            val collection =
                suite.value
                    .getValue("collection")
                    .jsonPrimitive.content
            check(Nsid.require(collection), suite)
        }
    }

    private fun LexiconRecordValidator.issuesFor(
        collection: Nsid,
        record: JsonObject,
    ): List<LexiconValidationIssue> = validateRecord(collection, record).issues

    private class Suite(
        val value: JsonObject,
    ) {
        fun cases(kind: String): List<Case> = value.getValue(kind).jsonArray.map { Case(it.jsonObject) }
    }

    private class Case(
        val value: JsonObject,
    ) {
        val name: String get() = string("name")
        val record: JsonObject get() = value.getValue("record").jsonObject

        fun string(key: String): String = value.getValue(key).jsonPrimitive.content

        fun boolean(key: String): Boolean? = value[key]?.jsonPrimitive?.boolean
    }

    private data class Failure(
        val kind: String,
        val path: String,
    )

    private companion object {
        const val DAG_CBOR_CODEC = 0x71

        fun classify(issue: LexiconValidationIssue): Failure = Failure(kindOf(issue), pointer(issue.path))

        /** `blocks[0].text` as the JSON Pointer `/blocks/0/text`. */
        fun pointer(path: String): String =
            path
                .split('.', '[', ']')
                .filter { it.isNotEmpty() }
                .joinToString("") { "/$it" }

        fun kindOf(issue: LexiconValidationIssue): String {
            val message = issue.message
            return when {
                issue.path == "\$type" -> "wrong-record-type"
                message == "required field is missing" -> "missing-required"
                message.startsWith("expected ") ||
                    message.startsWith("not a valid blob") ||
                    message == "not valid bytes" ||
                    message == "not a valid content link" -> "wrong-type"
                message.startsWith("a union member must have") -> "union-member-missing-type"
                message.contains("in this closed union") -> "union-member-not-allowed"
                message.startsWith("longer than") || Regex("^more than \\d+ items$").matches(message) -> "too-long"
                message.startsWith("shorter than") || message.startsWith("fewer than") -> "too-short"
                message.contains("the minimum of") || message.contains("the maximum of") -> "out-of-range"
                message.startsWith("not a valid ") -> "invalid-format"
                message.contains("allowed values") || message.startsWith("must be ") -> "not-allowed-value"
                else -> "unclassified: $message"
            }
        }

        fun readJson(path: Path): JsonObject = Json.parseToJsonElement(path.readText()).jsonObject

        fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

        fun jsonFiles(directory: Path): List<Path> =
            Files.walk(directory).use { paths ->
                paths
                    .asSequence()
                    .filter { it.extension == "json" }
                    .sortedBy { it.name }
                    .toList()
            }

        fun repositoryRoot(): Path =
            generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.isDirectory(it.resolve("conformance/records")) }

        fun conformanceRoot(): Path = repositoryRoot().resolve("conformance")
    }

    private object Hex {
        fun decode(text: String): ByteArray = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
