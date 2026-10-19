package app.logdate.server.space

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SealedRecordCheckTest {
    private fun envelope(
        type: String = "app.logdate.crypto.sealed",
        version: Int = 1,
        keyId: String = "abcdefghijklm",
        nonce: Int = 12,
        ciphertext: Int = 528,
    ) = buildJsonObject {
        put("\$type", type)
        put("version", version)
        put("keyId", keyId)
        putJsonObject("nonce") { put("\$bytes", Base64.encode(ByteArray(nonce))) }
        putJsonObject("ciphertext") { put("\$bytes", Base64.encode(ByteArray(ciphertext))) }
    }

    @Test
    fun `accepts a padded envelope`() {
        assertNull(checkSealedRecord(envelope()))
    }

    @Test
    fun `rejects a record that isn't an envelope`() {
        assertEquals("Not an app.logdate.crypto.sealed record", checkSealedRecord(buildJsonObject { put("text", "hello") }))
    }

    @Test
    fun `rejects an envelope with extra fields`() {
        val extra = JsonObject(envelope() + ("createdAt" to JsonPrimitive("2026-10-18T00:00:00Z")))
        assertEquals("Not an app.logdate.crypto.sealed record", checkSealedRecord(extra))
    }

    @Test
    fun `rejects another record type`() {
        assertEquals(
            "Only app.logdate.crypto.sealed records can be stored in a space",
            checkSealedRecord(envelope(type = "app.logdate.journal.entry")),
        )
    }

    @Test
    fun `rejects an envelope without a type`() {
        val untyped = JsonObject(envelope() - "\$type")
        assertEquals("Only app.logdate.crypto.sealed records can be stored in a space", checkSealedRecord(untyped))
    }

    @Test
    fun `rejects an unknown envelope version`() {
        assertEquals("Envelope version 2 isn't supported", checkSealedRecord(envelope(version = 2)))
    }

    @Test
    fun `rejects an empty or long key identifier`() {
        assertEquals("keyId must be 1 to 128 characters", checkSealedRecord(envelope(keyId = "")))
        assertEquals("keyId must be 1 to 128 characters", checkSealedRecord(envelope(keyId = "k".repeat(129))))
    }

    @Test
    fun `rejects a nonce that isn't 12 bytes`() {
        assertEquals("nonce must be 12 bytes", checkSealedRecord(envelope(nonce = 16)))
    }

    @Test
    fun `rejects unpadded ciphertext`() {
        assertEquals("ciphertext isn't padded to a size bucket", checkSealedRecord(envelope(ciphertext = 527)))
        assertEquals("ciphertext isn't padded to a size bucket", checkSealedRecord(envelope(ciphertext = 16)))
    }
}
