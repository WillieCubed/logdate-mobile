package studio.hypertext.atproto.repo

import kotlin.test.Test
import kotlin.test.assertEquals

class RepoJvmInteropTest {
    @Test
    fun `jvm consumers can read boxed cid values`() {
        val cid = Cid.sha256(codec = DAG_CBOR_CODEC, bytes = "hello".encodeToByteArray())

        assertEquals(cid.toString(), invokeJvmGetter(cid))
    }

    private fun invokeJvmGetter(target: Any): Any? =
        target.javaClass
            .declaredMethods
            .single { method -> method.name == "getValue" && method.parameterCount == 0 }
            .apply { isAccessible = true }
            .invoke(target)
}
