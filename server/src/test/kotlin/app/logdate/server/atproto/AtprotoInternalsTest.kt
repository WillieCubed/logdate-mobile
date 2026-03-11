package app.logdate.server.atproto

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.lang.reflect.Method
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AtprotoInternalsTest {
    @Test
    fun `invalid swap exception exposes expected and provided cids`() {
        val exception = InvalidSwapException(expectedCid = "bafk-expected", providedCid = "bafk-provided")

        assertEquals("bafk-expected", exception.expectedCid)
        assertEquals("bafk-provided", exception.providedCid)
        assertEquals("Invalid swapRecord", exception.message)
    }

    @Test
    fun `synthetic cid helpers encode larger varints and empty base32 payloads`() {
        val instance = Class.forName("app.logdate.server.atproto.SyntheticCid").getDeclaredField("INSTANCE").get(null)
        val encodeVarint = syntheticCidMethod("encodeVarint", Int::class.javaPrimitiveType!!)
        val encodeBase32 = syntheticCidMethod("encodeBase32", ByteArray::class.java)

        assertContentEquals(byteArrayOf(0x80.toByte(), 0x01), encodeVarint.invoke(instance, 128) as ByteArray)
        assertEquals("", encodeBase32.invoke(instance, byteArrayOf()) as String)
    }

    @Test
    fun `synthetic cid generation is deterministic`() {
        val instance = Class.forName("app.logdate.server.atproto.SyntheticCid").getDeclaredField("INSTANCE").get(null)
        val fromRecord =
            Class
                .forName("app.logdate.server.atproto.SyntheticCid")
                .getDeclaredMethod("fromRecord", String::class.java, kotlinx.serialization.json.JsonObject::class.java)
        val value =
            buildJsonObject {
                put("\$type", "studio.hypertext.logdate.content")
                put("content", "hello")
            }

        val first = fromRecord.invoke(instance, "at://did:web:alice.logdate.app/studio.hypertext.logdate.content/entry-1", value) as String
        val second = fromRecord.invoke(instance, "at://did:web:alice.logdate.app/studio.hypertext.logdate.content/entry-1", value) as String

        assertTrue(first.startsWith("b"))
        assertEquals(first, second)
    }

    @Test
    fun `fixed width big integer helper pads and truncates as needed`() {
        val helper = Class.forName("studio.hypertext.atproto.crypto.EcKeySupport")
        val instance = helper.getDeclaredField("INSTANCE").get(null)
        val method =
            helper
                .getDeclaredMethod("toFixedWidth", BigInteger::class.java, Int::class.javaPrimitiveType)
                .apply { isAccessible = true }

        assertContentEquals(byteArrayOf(0, 0, 1), method.invoke(instance, BigInteger.ONE, 3) as ByteArray)
        assertContentEquals(byteArrayOf(1, 2, 3), method.invoke(instance, BigInteger("010203", 16), 3) as ByteArray)
        assertContentEquals(byteArrayOf(2, 3), method.invoke(instance, BigInteger("010203", 16), 2) as ByteArray)
    }

    private fun syntheticCidMethod(
        name: String,
        vararg parameterTypes: Class<*>,
    ): Method =
        Class
            .forName("app.logdate.server.atproto.SyntheticCid")
            .getDeclaredMethod(name, *parameterTypes)
            .apply { isAccessible = true }
}
