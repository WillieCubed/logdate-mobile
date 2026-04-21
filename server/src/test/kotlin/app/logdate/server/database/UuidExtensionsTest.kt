package app.logdate.server.database

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Unit tests for UUID interoperability extension functions.
 *
 * Validates the bidirectional conversion between [java.util.UUID] and the Kotlin-native
 * [kotlin.uuid.Uuid] (Experimental). These tests ensure that round-tripping maintains
 * identity and that nullable variants are handled correctly without unexpected failures.
 */
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
