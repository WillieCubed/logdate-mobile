package app.logdate.client.domain.export

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExportSchemaVersionTest {
    @Test
    fun `parse valid version string`() {
        val version = ExportSchemaVersion.parse("1.1")
        assertEquals(1, version.major)
        assertEquals(1, version.minor)
    }

    @Test
    fun `parse invalid format throws`() {
        assertFailsWith<IllegalArgumentException> {
            ExportSchemaVersion.parse("1")
        }
        assertFailsWith<IllegalArgumentException> {
            ExportSchemaVersion.parse("1.2.3")
        }
    }

    @Test
    fun `comparison ordering works`() {
        assertTrue(ExportSchemaVersion.V1_0 < ExportSchemaVersion.V1_1)
        assertTrue(ExportSchemaVersion.V1_1 > ExportSchemaVersion.V1_0)
        assertTrue(ExportSchemaVersion.V1_1 == ExportSchemaVersion(1, 1))
        assertTrue(ExportSchemaVersion(1, 1) < ExportSchemaVersion(2, 0))
        assertTrue(ExportSchemaVersion(2, 0) > ExportSchemaVersion(1, 9))
    }

    @Test
    fun `toString produces dotted format`() {
        assertEquals("1.0", ExportSchemaVersion.V1_0.toString())
        assertEquals("1.1", ExportSchemaVersion.V1_1.toString())
    }

    @Test
    fun `serialization round-trip produces same string`() {
        val json = Json { ignoreUnknownKeys = true }
        val original = ExportSchemaVersion.V1_1
        val encoded = json.encodeToString(original)
        assertEquals("\"1.1\"", encoded)

        val decoded = json.decodeFromString<ExportSchemaVersion>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `deserialization handles legacy 1_0 string`() {
        val jsonStr =
            """
            {
                "version": "1.0",
                "exportDate": "2026-03-20T10:00:00Z",
                "userId": "test",
                "deviceId": "test",
                "appVersion": "1.0.0",
                "stats": {"journalCount": 0, "noteCount": 0, "draftCount": 0, "mediaCount": 0}
            }
            """.trimIndent()
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<ExportMetadata>(jsonStr)
        assertEquals(ExportSchemaVersion.V1_0, decoded.version)
    }
}
