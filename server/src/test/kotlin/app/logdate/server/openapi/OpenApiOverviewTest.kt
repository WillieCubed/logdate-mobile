package app.logdate.server.openapi

import app.logdate.server.routes.CLOUD_TRANSCRIPTION_SESSION_LIMIT
import app.logdate.server.routes.SIGNIN_RATE_LIMIT
import app.logdate.server.routes.SIGNUP_RATE_LIMIT
import app.logdate.server.routes.sync.BACKUP_UPLOAD_RATE_LIMIT
import app.logdate.server.routes.sync.MEDIA_UPLOAD_RATE_LIMIT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The overview is prose, but every number in it must be the one the server enforces. */
class OpenApiOverviewTest {
    private val overview = OpenApiOverview.markdown

    @Test
    fun `overview walks a newcomer from first request to sync`() {
        listOf(
            "## Your first request",
            "## Your first sync",
            "## Concepts",
            "## Base URLs and self-hosting",
            "## Authentication in depth",
            "## Errors",
            "## Rate limits",
            "## Quotas",
            "## Sync model in depth",
            "## Conventions",
            "## Status",
        ).forEach { heading -> assertTrue(heading in overview, "overview is missing the section '$heading'") }
    }

    @Test
    fun `overview quotes the limits the server enforces`() {
        listOf(SIGNUP_RATE_LIMIT, SIGNIN_RATE_LIMIT, MEDIA_UPLOAD_RATE_LIMIT, BACKUP_UPLOAD_RATE_LIMIT, CLOUD_TRANSCRIPTION_SESSION_LIMIT)
            .map(ApiLimits::describe)
            .forEach { limit -> assertTrue(limit in overview, "overview does not mention '$limit'") }
        assertTrue("{{" !in overview, "an overview placeholder was left unrendered")
    }

    @Test
    fun `overview explains the error codes a client must handle`() {
        listOf("INVALID_TOKEN", "RATE_LIMIT_EXCEEDED", "QUOTA_EXCEEDED", "CONFLICT", "NOT_FOUND", "REFRESH_TOKEN_REVOKED")
            .forEach { code -> assertTrue(code in overview, "overview does not explain $code") }
    }

    @Test
    fun `rate limit descriptions read naturally`() {
        assertEquals("5 requests per hour", ApiLimits.describe(SIGNUP_RATE_LIMIT))
        assertEquals("10 requests per minute", ApiLimits.describe(SIGNIN_RATE_LIMIT))
    }
}
