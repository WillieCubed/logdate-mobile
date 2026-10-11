package app.logdate.server.logging

import io.github.aakira.napier.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyCustodyNoticesTest {
    @Test
    fun `key custody outcomes keep their own codes in production logs`() {
        mapOf(
            KeyCustodyNotices.SIGNING_KEYS_REWRAPPED to "SERVER_SIGNING_KEYS_REWRAPPED",
            KeyCustodyNotices.SIGNING_KEYS_CURRENT to "SERVER_SIGNING_KEYS_CURRENT",
            KeyCustodyNotices.SIGNING_KEYS_UNREADABLE to "SERVER_SIGNING_KEYS_UNREADABLE",
            KeyCustodyNotices.SIGNING_KEYS_REWRAP_FAILED to "SERVER_SIGNING_KEYS_REWRAP_FAILED",
            KeyCustodyNotices.OAUTH_KEY_STAGED to "SERVER_OAUTH_KEY_STAGED",
            KeyCustodyNotices.OAUTH_KEY_PROMOTED to "SERVER_OAUTH_KEY_PROMOTED",
            KeyCustodyNotices.OAUTH_KEY_RETIRED to "SERVER_OAUTH_KEY_RETIRED",
            KeyCustodyNotices.OAUTH_KEY_ROTATION_FAILED to "SERVER_OAUTH_KEY_ROTATION_FAILED",
        ).forEach { (message, code) ->
            assertEquals(code, safeServerMessage(LogLevel.ERROR, tag = null, message = message))
        }
    }
}
