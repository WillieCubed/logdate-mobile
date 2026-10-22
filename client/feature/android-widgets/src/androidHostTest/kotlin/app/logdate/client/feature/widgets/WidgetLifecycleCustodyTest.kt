package app.logdate.client.feature.widgets

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WidgetLifecycleCustodyTest {
    @Test
    fun `rejected wake retains the inbox but leaves setup pending for retry`() {
        var retained = false
        assertFalse(
            retainWidgetLifecycleCustody(
                persist = { retained = true },
                schedule = { false },
                persistedFallbackSupported = true,
                onFailure = { throw it },
            ),
        )
        assertTrue(retained)
    }

    @Test
    fun `failed local persistence requires an accepted persisted OS payload before success`() {
        var queuedPayload = false
        assertTrue(
            retainWidgetLifecycleCustody(
                persist = { throw IOException("No space for inbox") },
                schedule = { includePayload ->
                    queuedPayload = includePayload
                    true
                },
                persistedFallbackSupported = true,
                onFailure = {},
            ),
        )
        assertTrue(queuedPayload)
    }

    @Test
    fun `nonpersistent or rejected OS fallback leaves setup unaccepted for retry`() {
        for ((supported, scheduled) in listOf(false to true, true to false)) {
            assertFalse(
                retainWidgetLifecycleCustody(
                    persist = { throw IOException("No space for inbox") },
                    schedule = { scheduled },
                    persistedFallbackSupported = supported,
                    onFailure = {},
                ),
            )
        }
    }
}
