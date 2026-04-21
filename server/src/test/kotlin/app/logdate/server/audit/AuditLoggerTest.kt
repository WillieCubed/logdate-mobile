package app.logdate.server.audit

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Unit tests for [AuditLogger], focusing on the JSON-based rendering of audit events.
 *
 * These tests ensure that audit payloads are valid JSON, handle null values
 * gracefully, escape special characters correctly, and maintain deterministic
 * field ordering for predictable logging.
 */
class AuditLoggerTest {
    @Test
    fun `renders category and fields as single-line JSON`() {
        val json = AuditLogger.renderJson("account.deleted", mapOf("accountId" to "abc", "n" to "2"))
        assertTrue(json.startsWith("{"))
        assertTrue(json.endsWith("}"))
        assertTrue(json.contains("\"category\":\"account.deleted\""))
        assertTrue(json.contains("\"accountId\":\"abc\""))
        assertTrue(json.contains("\"n\":\"2\""))
        assertTrue(json.contains("\"ts\":\""))
    }

    @Test
    fun `skips null field values`() {
        val json = AuditLogger.renderJson("cat", mapOf("set" to "yes", "absent" to null))
        assertTrue(json.contains("\"set\":\"yes\""))
        assertTrue(!json.contains("\"absent\""))
    }

    @Test
    fun `escapes quotes backslashes and control characters`() {
        val json = AuditLogger.renderJson("cat", mapOf("msg" to "a\"b\\c\nd"))
        assertTrue(json.contains("\"msg\":\"a\\\"b\\\\c\\nd\""))
    }

    @Test
    fun `fields sort deterministically for readable diffs`() {
        val json = AuditLogger.renderJson("cat", mapOf("b" to "2", "a" to "1"))
        val aIndex = json.indexOf("\"a\":")
        val bIndex = json.indexOf("\"b\":")
        assertTrue(aIndex in 0..<bIndex, "expected a before b in $json")
    }
}
