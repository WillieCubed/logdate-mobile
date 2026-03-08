package studio.hypertext.atproto.identity

import kotlin.test.Test
import kotlin.test.assertEquals

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
