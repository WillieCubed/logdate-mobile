package app.logdate.server.database

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class UuidExtensionsTest {
    @Test
    fun `uuid conversion helpers round-trip and support nullable variants`() {
        val kotlinUuid = Uuid.random()
        val javaUuid = kotlinUuid.toJavaUUID()

        assertEquals(kotlinUuid, javaUuid.toKotlinUuid())
        assertEquals(javaUuid, kotlinUuid.toJavaUUIDOrNull())
        assertEquals(kotlinUuid, javaUuid.toKotlinUuidOrNull())

        val nullKotlin: Uuid? = null
        val nullJava: UUID? = null
        assertNull(nullKotlin.toJavaUUIDOrNull())
        assertNull(nullJava.toKotlinUuidOrNull())
    }
}
