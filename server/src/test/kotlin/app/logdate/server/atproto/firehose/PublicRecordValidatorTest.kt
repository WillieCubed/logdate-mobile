package app.logdate.server.atproto.firehose

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import studio.hypertext.atproto.syntax.Nsid
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A public record has to follow its lexicon before it is written, because other apps read it as that
 * lexicon says. These tests run the real lexicons, LogDate's and the Standard.site ones the server bundles.
 */
@OptIn(ExperimentalEncodingApi::class)
class PublicRecordValidatorTest {
    private val validator = PublicRecordValidator()
    private val profile = PublicRepoPolicy.PROFILE
    private val keyDeclaration = PublicRepoPolicy.KEY_DECLARATION
    private val publication = PublicRepoPolicy.PUBLICATION
    private val document = PublicRepoPolicy.DOCUMENT
    private val cid = "bafyreigdyrzt5sfp7udm7hu76uh7y26nf3efuylqabf3oclgtqy55fbzdi"

    private fun blob(
        mimeType: String = "image/png",
        size: Long = 1000,
    ): JsonObject =
        buildJsonObject {
            put("\$type", "blob")
            putJsonObject("ref") { put("\$link", "bafkreigh2akiscaildcqabsyg3dfr6chu3fgpregiymsck7e7aqa4s52zy") }
            put("mimeType", mimeType)
            put("size", size)
        }

    private fun bytes(size: Int): JsonObject = buildJsonObject { put("\$bytes", Base64.encode(ByteArray(size) { 1 })) }

    private fun validProfile(): Map<String, JsonElement> =
        mapOf(
            "\$type" to JsonPrimitive(profile.toString()),
            "displayName" to JsonPrimitive("Lena"),
            "description" to JsonPrimitive("Notes from the lake"),
            "avatar" to blob(),
            "createdAt" to JsonPrimitive("2026-10-23T12:00:00.000Z"),
        )

    private fun validKeyDeclaration(): Map<String, JsonElement> =
        mapOf(
            "\$type" to JsonPrimitive(keyDeclaration.toString()),
            "publicKey" to bytes(32),
            "algorithm" to JsonPrimitive("x25519"),
            "signature" to bytes(64),
            "createdAt" to JsonPrimitive("2026-10-23T12:00:00.000Z"),
        )

    private fun validPublication(): Map<String, JsonElement> =
        mapOf(
            "\$type" to JsonPrimitive(publication.toString()),
            "name" to JsonPrimitive("Lake days"),
            "url" to JsonPrimitive("https://lena.logdate.app"),
            "icon" to blob(),
            "basicTheme" to
                buildJsonObject {
                    listOf("accent", "accentForeground", "background", "foreground").forEach { name ->
                        putJsonObject(name) {
                            put("\$type", "site.standard.theme.color#rgb")
                            put("r", 10)
                            put("g", 20)
                            put("b", 30)
                        }
                    }
                },
            "preferences" to buildJsonObject { put("showInDiscover", true) },
        )

    private fun validDocument(): Map<String, JsonElement> =
        mapOf(
            "\$type" to JsonPrimitive(document.toString()),
            "site" to JsonPrimitive("at://did:web:lena.logdate.app/site.standard.publication/3kabc"),
            "title" to JsonPrimitive("A day by the lake"),
            "publishedAt" to JsonPrimitive("2026-10-23T12:00:00.000Z"),
            "path" to JsonPrimitive("/lake-day"),
            "tags" to buildJsonArray { add(JsonPrimitive("lake")) },
            "coverImage" to blob(),
            "content" to
                buildJsonObject {
                    put("\$type", "com.example.markdown")
                    put("text", "hello")
                },
            "bskyPostRef" to
                buildJsonObject {
                    put("uri", "at://did:plc:ewvi7nxzyoun6zhxrhs64oiz/app.bsky.feed.post/3kabc")
                    put("cid", cid)
                },
        )

    private fun JsonObject.with(
        name: String,
        value: JsonElement,
    ): JsonObject = JsonObject(this + (name to value))

    private fun Map<String, JsonElement>.record(): JsonObject = JsonObject(this)

    private fun Map<String, JsonElement>.without(name: String): JsonObject = JsonObject(this - name)

    private fun rejects(
        collection: Nsid,
        record: JsonObject,
        expectedMessage: String,
    ) {
        val failure = assertFailsWith<InvalidPublicRecordException>(record.toString()) { validator.validate(collection, record) }
        assertContains(failure.message.orEmpty(), expectedMessage)
    }

    @Test
    fun `every collection LogDate publishes has a schema that is enforced`() {
        listOf(profile, keyDeclaration, publication, document).forEach { collection ->
            assertTrue(validator.hasSchema(collection), "$collection needs a schema so its records are checked")
        }
    }

    @Test
    fun `complete valid records of every public collection are accepted`() {
        validator.validate(profile, validProfile().record())
        validator.validate(keyDeclaration, validKeyDeclaration().record())
        validator.validate(publication, validPublication().record())
        validator.validate(document, validDocument().record())
    }

    @Test
    fun `a record with only its required fields is accepted`() {
        validator.validate(profile, buildJsonObject { put("\$type", profile.toString()) })
        validator.validate(publication, validPublication().filterKeys { it in setOf("\$type", "name", "url") }.record())
        validator.validate(document, validDocument().filterKeys { it in setOf("\$type", "site", "title", "publishedAt") }.record())
    }

    @Test
    fun `a field the schema does not describe is accepted so newer records stay valid`() {
        validator.validate(profile, validProfile().record().with("pronouns", JsonPrimitive("she/her")))
    }

    @Test
    fun `a profile with the wrong kind of value is rejected with the path of the value`() {
        rejects(profile, validProfile().record().with("displayName", JsonPrimitive(7)), "displayName: expected a string")
        rejects(profile, validProfile().record().with("avatar", JsonPrimitive("a photo")), "avatar: not a valid blob")
        rejects(profile, validProfile().record().with("createdAt", JsonPrimitive("yesterday")), "createdAt: not a valid datetime")
    }

    @Test
    fun `a profile whose text or photo is over the limit is rejected`() {
        rejects(
            profile,
            validProfile().record().with("displayName", JsonPrimitive("x".repeat(65))),
            "displayName: longer than 64 graphemes",
        )
        rejects(
            profile,
            validProfile().record().with("description", JsonPrimitive("é".repeat(1300))),
            "description: longer than 2560 bytes",
        )
        rejects(profile, validProfile().record().with("avatar", blob(size = 1_000_001)), "avatar: 1000001 bytes is more than the maximum")
        rejects(profile, validProfile().record().with("avatar", blob(mimeType = "image/gif")), "avatar: image/gif isn't an accepted type")
    }

    @Test
    fun `a sharing key missing a field or carrying oversized bytes is rejected`() {
        rejects(keyDeclaration, validKeyDeclaration().without("publicKey"), "publicKey: required field is missing")
        rejects(keyDeclaration, validKeyDeclaration().without("signature"), "signature: required field is missing")
        rejects(keyDeclaration, validKeyDeclaration().without("createdAt"), "createdAt: required field is missing")
        rejects(keyDeclaration, validKeyDeclaration().record().with("publicKey", bytes(257)), "publicKey: longer than 256 bytes")
        rejects(keyDeclaration, validKeyDeclaration().record().with("publicKey", JsonPrimitive("not bytes")), "publicKey: not valid bytes")
        rejects(
            keyDeclaration,
            validKeyDeclaration().record().with("algorithm", JsonPrimitive("a".repeat(65))),
            "algorithm: longer than 64 bytes",
        )
    }

    @Test
    fun `a story missing a required field or carrying a malformed one is rejected`() {
        rejects(document, validDocument().without("title"), "title: required field is missing")
        rejects(document, validDocument().without("site"), "site: required field is missing")
        rejects(document, validDocument().without("publishedAt"), "publishedAt: required field is missing")
        rejects(document, validDocument().record().with("publishedAt", JsonPrimitive("2026-13-45")), "publishedAt: not a valid datetime")
        rejects(document, validDocument().record().with("site", JsonPrimitive("not a uri")), "site: not a valid uri")
        rejects(document, validDocument().record().with("title", JsonPrimitive("t".repeat(501))), "title: longer than 500 graphemes")
        rejects(
            document,
            validDocument().record().with("coverImage", blob(size = 2_000_000)),
            "coverImage: 2000000 bytes is more than the maximum",
        )
        rejects(document, validDocument().record().with("tags", JsonArray(listOf(JsonPrimitive(1)))), "tags[0]: expected a string")
        rejects(
            document,
            validDocument().record().with(
                "tags",
                buildJsonArray {
                    add(JsonPrimitive("t".repeat(129)))
                },
            ),
            "tags[0]: longer than 128 graphemes",
        )
        rejects(document, validDocument().record().with("content", JsonPrimitive("plain text")), "content: expected an object")
        rejects(
            document,
            validDocument().record().with(
                "content",
                buildJsonObject {
                    put("text", "no type")
                },
            ),
            "content: a union member must have a \$type",
        )
        rejects(
            document,
            validDocument().record().with(
                "bskyPostRef",
                buildJsonObject {
                    put("uri", "at://did:web:a.b/c.d.e/f")
                    put("cid", "!")
                },
            ),
            "bskyPostRef.cid: not a valid cid",
        )
    }

    @Test
    fun `a publication missing a required field or carrying a malformed one is rejected`() {
        rejects(publication, validPublication().without("name"), "name: required field is missing")
        rejects(publication, validPublication().without("url"), "url: required field is missing")
        rejects(
            publication,
            validPublication().record().with("icon", blob(mimeType = "text/plain")),
            "icon: text/plain isn't an accepted type",
        )
        rejects(
            publication,
            validPublication().record().with(
                "preferences",
                buildJsonObject {
                    put("showInDiscover", "yes")
                },
            ),
            "preferences.showInDiscover: expected a boolean",
        )
        rejects(
            publication,
            validPublication().record().with(
                "basicTheme",
                buildJsonObject {
                    putJsonObject("accent") {
                        put("\$type", "site.standard.theme.color#rgb")
                        put("r", 300)
                        put("g", 0)
                        put("b", 0)
                    }
                },
            ),
            "basicTheme.accent.r: more than the maximum of 255",
        )
    }

    @Test
    fun `every problem in a record is reported at once`() {
        val failure =
            assertFailsWith<InvalidPublicRecordException> {
                validator.validate(document, validDocument().without("title").record().with("publishedAt", JsonPrimitive("never")))
            }

        assertContains(failure.message.orEmpty(), "title: required field is missing")
        assertContains(failure.message.orEmpty(), "publishedAt: not a valid datetime")
    }

    @Test
    fun `a record whose type names another collection is rejected`() {
        rejects(profile, validProfile().record().with("\$type", JsonPrimitive(document.toString())), "doesn't match the collection")
    }

    @Test
    fun `a collection without a schema is accepted as it is`() {
        val openToApps = Nsid.require("com.example.note")

        validator.validate(openToApps, buildJsonObject { put("anything", 1) })
        assertTrue(!validator.hasSchema(openToApps))
    }
}
