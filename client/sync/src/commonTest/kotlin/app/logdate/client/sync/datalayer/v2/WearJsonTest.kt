package app.logdate.client.sync.datalayer.v2

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * Tests [WearJson], which every version 2 message goes through: unknown keys are ignored, a newer
 * schema is reported instead of guessed at, and a broken message never echoes its contents.
 */
class WearJsonTest {
    @Serializable
    private data class Probe(
        val schema: Int = 1,
        val name: String,
    )

    @Serializable
    private data class WithOptional(
        val schema: Int = 1,
        val note: String? = null,
        val count: Int = 0,
    )

    @Test
    fun `a supported document decodes`() {
        assertEquals(Decoded.Supported(Probe(name = "a")), WearJson.decode("""{"schema":1,"name":"a"}""", 1, Probe.serializer()))
    }

    @Test
    fun `keys a newer device adds are ignored`() {
        assertEquals(
            Decoded.Supported(Probe(name = "a")),
            WearJson.decode("""{"schema":1,"name":"a","later":{"x":1}}""", 1, Probe.serializer()),
        )
    }

    @Test
    fun `a newer schema is unsupported rather than guessed at`() {
        val decoded = WearJson.decode("""{"schema":2,"renamed":"a"}""", 1, Probe.serializer())

        assertEquals(2, assertIs<Decoded.Unsupported>(decoded).schema)
    }

    @Test
    fun `a document without a schema is read as schema 1`() {
        assertEquals(Decoded.Supported(Probe(name = "a")), WearJson.decode("""{"name":"a"}""", 1, Probe.serializer()))
    }

    @Test
    fun `text that is not a JSON object is malformed`() {
        assertIs<Decoded.Malformed>(WearJson.decode("not json", 1, Probe.serializer()))
        assertIs<Decoded.Malformed>(WearJson.decode("[1]", 1, Probe.serializer()))
    }

    @Test
    fun `a schema that is not a number is malformed`() {
        assertIs<Decoded.Malformed>(WearJson.decode("""{"schema":"two","name":"a"}""", 1, Probe.serializer()))
    }

    @Test
    fun `a malformed reason never repeats the input`() {
        val decoded = WearJson.decode("""{"schema":1,"name":{"secret":"my diary"}}""", 1, Probe.serializer())

        assertFalse(assertIs<Decoded.Malformed>(decoded).reason.contains("my diary"))
    }

    @Test
    fun `encoding writes defaults and leaves out nulls`() {
        assertEquals("""{"schema":1,"count":0}""", WearJson.encode(WithOptional.serializer(), WithOptional()))
    }

    @Test
    fun `bytes decode like text`() {
        val bytes = WearJson.encodeToBytes(Probe.serializer(), Probe(name = "b"))

        assertEquals(Decoded.Supported(Probe(name = "b")), WearJson.decode(bytes, 1, Probe.serializer()))
    }
}
