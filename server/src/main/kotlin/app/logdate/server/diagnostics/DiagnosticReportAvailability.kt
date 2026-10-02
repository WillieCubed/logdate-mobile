package app.logdate.server.diagnostics

import app.logdate.server.crypto.EnvironmentKeyring

/** Configured servers advertise reports only when encrypted persistence is explicitly enabled. */
data class DiagnosticReportAvailability(
    val enabled: Boolean,
    val keyring: EnvironmentKeyring? = null,
) {
    companion object {
        fun fromEnvironment(
            isDatabaseAvailable: Boolean,
            readEnv: (String) -> String? = System::getenv,
        ): DiagnosticReportAvailability {
            if (!isDatabaseAvailable) return DiagnosticReportAvailability(false)
            if (readEnv("LOGDATE_DIAGNOSTIC_REPORTS_ENABLED")?.toBooleanStrictOrNull() != true) {
                return DiagnosticReportAvailability(false)
            }
            val keyring = runCatching { EnvironmentKeyring(readEnv) }.getOrNull()
            return DiagnosticReportAvailability(keyring != null, keyring)
        }
    }
}
