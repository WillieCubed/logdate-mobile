package app.logdate.server.diagnostics

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DiagnosticReportAvailabilityTest {
    private val validKey = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })

    @Test
    fun `configured servers require database explicit flag and real key`() {
        fun availability(
            database: Boolean = true,
            enabled: String? = "true",
            key: String? = validKey,
        ) = DiagnosticReportAvailability.fromEnvironment(database) { name ->
            when (name) {
                "LOGDATE_DIAGNOSTIC_REPORTS_ENABLED" -> enabled
                "SERVER_ENCRYPTION_KEY" -> key
                "SERVER_ENCRYPTION_KEY_ID" -> "reports"
                else -> null
            }
        }

        assertFalse(availability(database = false).enabled)
        assertFalse(availability(enabled = null).enabled)
        assertFalse(availability(key = null).enabled)
        assertFalse(availability(key = "not-base64").enabled)
        assertTrue(availability().enabled)
        assertNotNull(availability().keyring)
    }
}
