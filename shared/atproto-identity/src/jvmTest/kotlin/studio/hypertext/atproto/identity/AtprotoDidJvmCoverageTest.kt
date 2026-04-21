package studio.hypertext.atproto.identity

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * JVM-specific coverage tests for [AtprotoDid] to ensure compatibility with platform
 * reflection and interop requirements.
 *
 * This test specifically verifies that the underlying value of the DID is accessible
 * through standard Java-style getters, which is essential for certain serialization
 * and dependency injection frameworks on the JVM.
 */
class AtprotoDidJvmCoverageTest {
    @Test
    fun `boxed atproto did exposes backing value getter on jvm`() {
        val did = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
        val getter =
            did.javaClass.declaredMethods
                .single { method ->
                    method.name.startsWith("getValue") && method.parameterCount == 0
                }.apply { isAccessible = true }

        assertEquals("did:plc:ewvi7nxzyoun6zhxrhs64oiz", getter.invoke(did))
    }
}
