package studio.hypertext.atproto.plc

import kotlinx.serialization.json.JsonPrimitive
import java.lang.reflect.InvocationTargetException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * JVM-specific coverage tests for low-level PLC encoding and data handling utilities.
 *
 * This suite uses reflection to exercise private encoding helpers within [PlcEncoding],
 * ensuring that the custom CBOR-like binary format correctly handles various data types,
 * numeric ranges, and encoding schemes (such as Base32). It also verifies the behavior
 * of default methods on common PLC interfaces within the JVM environment.
 */
class PlcEncodingJvmCoverageTest {
    @Test
    fun `private encoding helpers cover numeric and primitive branches on jvm`() {
        assertContentEquals(byteArrayOf(0x18, 0x18), invokeByteArrayMethod("encodeHead", 0, 24L))
        assertContentEquals(byteArrayOf(0x19, 0x01, 0x00), invokeByteArrayMethod("encodeHead", 0, 256L))
        assertContentEquals(byteArrayOf(0x1a, 0x00, 0x01, 0x00, 0x00), invokeByteArrayMethod("encodeHead", 0, 65_536L))
        assertContentEquals(
            byteArrayOf(0x1b, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00),
            invokeByteArrayMethod("encodeHead", 0, 4_294_967_296L),
        )
        assertContentEquals(byteArrayOf(0x01, 0x02), invokeByteArrayMethod("encodeBigEndian", 0x0102L, 2))
        assertContentEquals(byteArrayOf(0x0a), invokeByteArrayMethod("encodeLong", 10L))
        assertContentEquals(byteArrayOf(0x24), invokeByteArrayMethod("encodeLong", -5L))
        assertContentEquals(byteArrayOf(0x63, 0x61, 0x62, 0x63), invokeByteArrayMethod("encodePrimitive", JsonPrimitive("abc")))
        assertContentEquals(byteArrayOf(0xf5.toByte()), invokeByteArrayMethod("encodePrimitive", JsonPrimitive(true)))
        assertContentEquals(byteArrayOf(0x0a), invokeByteArrayMethod("encodePrimitive", JsonPrimitive(10)))
        assertFailsWith<IllegalStateException> { invokeByteArrayMethod("encodePrimitive", JsonPrimitive(1.5)) }
        assertFailsWith<IllegalArgumentException> { invokeByteArrayMethod("encodeHead", 0, -1L) }
        assertEquals("", invokeStringMethod("encodeBase32", byteArrayOf()))
        assertTrue(invokeStringMethod("encodeBase32", byteArrayOf(0x01)).isNotBlank())
    }

    @Test
    fun `plc log entry default impl evaluates signature presence on jvm`() {
        val defaultImpls = Class.forName("studio.hypertext.atproto.plc.PlcLogEntry\$DefaultImpls")
        val method = defaultImpls.getDeclaredMethod("isSigned", PlcLogEntry::class.java)
        val unsignedEntry: PlcLogEntry =
            PlcOperation(
                services = mapOf("atproto_pds" to PlcService("AtprotoPersonalDataServer", "https://logdate.app")),
                alsoKnownAs = listOf("at://alice.logdate.app"),
                rotationKeys = listOf("did:key:zRotation"),
                verificationMethods = mapOf("atproto" to "did:key:zSigning"),
            )

        assertEquals(false, method.invoke(null, unsignedEntry))
    }

    private fun invokeByteArrayMethod(
        name: String,
        vararg args: Any,
    ): ByteArray {
        val method =
            PlcEncoding::class.java.declaredMethods.single { candidate ->
                candidate.name == name && candidate.parameterTypes.size == args.size
            }
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return try {
            method.invoke(PlcEncoding, *args) as ByteArray
        } catch (exception: InvocationTargetException) {
            throw exception.targetException
        }
    }

    private fun invokeStringMethod(
        name: String,
        vararg args: Any,
    ): String {
        val method =
            PlcEncoding::class.java.declaredMethods.single { candidate ->
                candidate.name == name && candidate.parameterTypes.size == args.size
            }
        method.isAccessible = true
        return try {
            method.invoke(PlcEncoding, *args) as String
        } catch (exception: InvocationTargetException) {
            throw exception.targetException
        }
    }
}
