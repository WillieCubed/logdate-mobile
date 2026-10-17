package studio.hypertext.atproto.lexicon

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.streams.asSequence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The full published `com.atproto` and `app.bsky` schema sets are the widest real-world input
 * available: every one must parse, pass the checker and generate code.
 */
class OfficialLexiconFixturesJvmTest {
    private val documents: List<LexiconDocument> by lazy {
        Files.walk(fixtureRoot()).use { paths ->
            paths
                .asSequence()
                .filter { it.extension == "json" }
                .sorted()
                .map { LexiconParser.parse(it.readText()) }
                .toList()
        }
    }

    @Test
    fun `every official schema parses and passes the checker`() {
        val registry = LexiconRegistry(documents)

        val issues =
            documents.flatMap { document ->
                LexiconValidator.validate(document, registry).issues.map { "${it.path}: ${it.message}" }
            }

        assertTrue(documents.size > 250, "Expected the full official sets, found ${documents.size} schemas")
        assertEquals(emptyList(), issues)
    }

    @Test
    fun `every official schema generates code`() {
        val failures =
            documents.mapNotNull { document ->
                runCatching { LexiconCodegen.generate(document) }.exceptionOrNull()?.let { "${document.id}: ${it.message}" }
            }

        assertEquals(emptyList(), failures)
    }

    @Test
    fun `a Bluesky post is a record with an open embed union and image blobs`() {
        val post = document("app.bsky.feed.post").definitions.getValue("main")
        val embed = post.record!!.properties.getValue("embed")
        val image =
            document("app.bsky.embed.images")
                .definitions
                .getValue("image")
                .properties
                .getValue("image")
        val generated = LexiconCodegen.generate(document("app.bsky.feed.post"))

        assertEquals(LexiconRecordKey.Tid, post.recordKey)
        assertEquals(LexiconType.UNION, embed.type)
        assertTrue(!embed.closed && "app.bsky.embed.images" in embed.refs.map { it.documentId.toString() })
        assertEquals(LexiconType.BLOB, image.type)
        assertEquals(listOf("image/*"), image.accept)
        assertTrue(generated.contains("public sealed interface PostEmbed"))
        assertTrue(generated.contains("public data class Unknown("))
        assertTrue(generated.contains("public const val TYPE: String = \"app.bsky.feed.post\""))
    }

    private fun document(id: String): LexiconDocument = documents.single { it.id.toString() == id }

    private fun fixtureRoot(): Path =
        Path.of(requireNotNull(javaClass.classLoader.getResource("official-lexicons/README.md")).toURI()).parent
}
