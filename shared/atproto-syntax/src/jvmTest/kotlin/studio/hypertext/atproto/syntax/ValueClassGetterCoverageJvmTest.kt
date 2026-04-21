package studio.hypertext.atproto.syntax

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ensures that AT Protocol value classes correctly expose their backing properties on the JVM.
 *
 * This test uses reflection to verify that `getValue` methods are correctly generated
 * and accessible when identifiers are boxed, maintaining compatibility with
 * Java-interoperability and library-specific data mapping.
 */
class ValueClassGetterCoverageJvmTest {
    @Test
    fun `boxed value classes expose backing value getters on jvm`() {
        assertEquals("did:web:logdate.app", invokeGetter(Did.require("did:web:logdate.app")))
        assertEquals("alice.logdate.app", invokeGetter(Handle.require("Alice.LogDate.App")))
        assertEquals("com.example.fooBar", invokeGetter(Nsid.require("Com.Example.fooBar")))
        assertEquals("entry-1", invokeGetter(RecordKey.require("entry-1")))
        assertEquals("3jzfcijpj2z2a", invokeGetter(Tid.require("3jzfcijpj2z2a")))
        assertEquals(
            "at://alice.logdate.app/com.example.foo/entry-1",
            invokeGetter(AtUri.require("at://Alice.LogDate.App/com.example.foo/entry-1")),
        )
    }

    private fun invokeGetter(target: Any): Any? =
        target.javaClass
            .getDeclaredMethod("getValue")
            .apply { isAccessible = true }
            .invoke(target)
}
