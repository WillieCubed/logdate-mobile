package app.logdate.server.logging

import io.sentry.Breadcrumb
import io.sentry.SentryEvent
import io.sentry.protocol.Message
import io.sentry.protocol.Request
import io.sentry.protocol.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryPrivacyTest {
    @Test
    fun `trusted deployment context survives without copying attacker supplied context`() {
        val event =
            SentryEvent().apply {
                release = "private.example.invalid"
                environment = "private-value"
            }
        val safe = sanitizedServerCrash(event, "logdate-server@abc1234", "production")
        assertEquals("logdate-server@abc1234", safe.release)
        assertEquals("production", safe.environment)
    }

    @Test
    fun `crash envelope excludes content credentials identities and nested exceptions`() {
        val marker = "PRIVATE_JOURNAL_AUTH_PATH_SENTINEL"
        val input =
            SentryEvent(IllegalStateException(marker, RuntimeException(marker))).apply {
                message = Message().apply { formatted = marker }
                request =
                    Request().apply {
                        url = "https://private.invalid/$marker"
                        data = marker
                        headers = mapOf("Authorization" to marker)
                        cookies = marker
                        queryString = marker
                    }
                user =
                    User().apply {
                        id = marker
                        email = marker
                    }
                serverName = marker
                transaction = marker
                logger = marker
                setExtra(marker, mapOf(marker to marker))
                setTag(marker, marker)
                addBreadcrumb(Breadcrumb().apply { message = marker })
                contexts[marker] = marker
            }
        val safe = sanitizedServerCrash(input)
        assertNull(safe.throwable)
        assertNull(safe.exceptions)
        assertNull(safe.request)
        assertNull(safe.user)
        assertNull(safe.serverName)
        assertNull(safe.transaction)
        assertTrue(safe.extras.isNullOrEmpty())
        assertTrue(safe.tags.isNullOrEmpty())
        assertTrue(safe.breadcrumbs.isNullOrEmpty())
        assertTrue(safe.contexts.isEmpty())
        assertEquals("SERVER_UNEXPECTED_FAILURE", safe.message?.formatted)
    }
}
