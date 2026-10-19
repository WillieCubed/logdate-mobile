package app.logdate.server.logging

import ch.qos.logback.classic.spi.LoggingEvent
import io.github.aakira.napier.LogLevel
import studio.hypertext.atproto.plc.PlcDirectoryException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IdentityPublishingNoticesTest {
    @Test
    fun `publishing outcomes keep their own codes in production logs`() {
        mapOf(
            IdentityPublishingNotices.PLC_OPERATION_PUBLISHED to "SERVER_PLC_OPERATION_PUBLISHED",
            IdentityPublishingNotices.PLC_OPERATION_REJECTED to "SERVER_PLC_OPERATION_REJECTED",
            IdentityPublishingNotices.PLC_DIRECTORY_RATE_LIMITED to "SERVER_PLC_DIRECTORY_RATE_LIMITED",
            IdentityPublishingNotices.PLC_DIRECTORY_UNREACHABLE to "SERVER_PLC_DIRECTORY_UNREACHABLE",
            IdentityPublishingNotices.PLC_OPERATION_UNCONFIRMED to "SERVER_PLC_OPERATION_UNCONFIRMED",
            IdentityPublishingNotices.RUN_STARTED to "SERVER_IDENTITY_PUBLISHING_STARTED",
            IdentityPublishingNotices.RUN_COMPLETE to "SERVER_IDENTITY_PUBLISHING_COMPLETE",
            IdentityPublishingNotices.RUN_STOPPED to "SERVER_IDENTITY_PUBLISHING_STOPPED",
            IdentityPublishingNotices.REFUSED_USAGE to "SERVER_IDENTITY_PUBLISHING_REFUSED_USAGE",
            IdentityPublishingNotices.REFUSED_PUBLISHING_OFF to "SERVER_IDENTITY_PUBLISHING_REFUSED_PUBLISHING_OFF",
            IdentityPublishingNotices.REFUSED_DIRECTORY_UNCONFIRMED to "SERVER_IDENTITY_PUBLISHING_REFUSED_DIRECTORY_UNCONFIRMED",
            IdentityPublishingNotices.REFUSED_NOT_PLC to "SERVER_IDENTITY_PUBLISHING_REFUSED_NOT_PLC",
            IdentityPublishingNotices.RUN_FAILED to "SERVER_IDENTITY_PUBLISHING_FAILED",
            IdentityPublishingNotices.ACCOUNT_FAILED to "SERVER_IDENTITY_PUBLISH_FAILED",
        ).forEach { (message, code) ->
            assertEquals(code, safeServerMessage(LogLevel.ERROR, tag = null, message = message))
        }
    }

    @Test
    fun `a summary of counts passes through and anything else in its place does not`() {
        val summary = IdentityPublishingNotices.summary(mapOf("published" to 3, "failed" to 0))

        assertEquals("SERVER_IDENTITY_PUBLISHING_SUMMARY published=3 failed=0", summary)
        assertEquals(summary, safeServerMessage(LogLevel.INFO, tag = null, message = summary))
        val event =
            LoggingEvent().apply {
                loggerName = SERVER_LOG_NAME
                message = summary
            }
        assertEquals(summary, SafeServerMessageConverter().convert(event))
        assertEquals(
            "SERVER_NOTICE",
            safeServerMessage(LogLevel.INFO, tag = null, message = "SERVER_IDENTITY_PUBLISHING_SUMMARY did=did:plc:abc"),
        )
        assertEquals(
            "SERVER_NOTICE",
            safeServerMessage(LogLevel.INFO, tag = null, message = "SERVER_IDENTITY_PUBLISHING_SUMMARY published=3 alice"),
        )
        assertFailsWith<IllegalArgumentException> { IdentityPublishingNotices.summary(mapOf("Bad Name" to 1)) }
        assertFailsWith<IllegalArgumentException> { IdentityPublishingNotices.summary(mapOf("failed" to -1)) }
    }

    @Test
    fun `submit failures are told apart by what the directory answered`() {
        assertEquals(
            IdentityPublishingNotices.PLC_OPERATION_REJECTED,
            IdentityPublishingNotices.forSubmitFailure(PlcDirectoryException("rejected", statusCode = 400)),
        )
        assertEquals(
            IdentityPublishingNotices.PLC_DIRECTORY_RATE_LIMITED,
            IdentityPublishingNotices.forSubmitFailure(PlcDirectoryException("slow down", statusCode = 429)),
        )
        assertEquals(
            IdentityPublishingNotices.PLC_DIRECTORY_UNREACHABLE,
            IdentityPublishingNotices.forSubmitFailure(PlcDirectoryException("bad gateway", statusCode = 502)),
        )
        assertEquals(
            IdentityPublishingNotices.PLC_DIRECTORY_UNREACHABLE,
            IdentityPublishingNotices.forSubmitFailure(java.io.IOException("connection refused")),
        )
    }
}
