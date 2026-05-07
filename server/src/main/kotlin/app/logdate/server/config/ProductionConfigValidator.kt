package app.logdate.server.config

import java.net.URI

/**
 * Refuses to start the server in [RuntimeProfile.PRODUCTION] if required secrets are missing or
 * obviously unsafe (e.g. the historic `"your-secret-key-change-in-production"` placeholder, or the
 * out-of-the-box `"logdate"` database password).
 *
 * Run this before any Ktor modules are installed — a loud crash at boot is the whole point.
 */
object ProductionConfigValidator {
    private const val MIN_JWT_SECRET_LENGTH = 32

    private val INSECURE_JWT_SECRETS =
        setOf(
            "your-secret-key-change-in-production",
            "change-me",
            "changeme",
            "secret",
            "dev-secret",
        )

    private val INSECURE_DB_PASSWORDS =
        setOf(
            "logdate",
            "password",
            "postgres",
            "changeme",
        )

    class InsecureProductionConfigException(
        message: String,
    ) : IllegalStateException(message)

    fun validate(
        profile: RuntimeProfile = RuntimeProfile.fromEnvironment(),
        readEnv: (String) -> String? = System::getenv,
    ) {
        if (!profile.isProduction) return

        val failures = mutableListOf<String>()

        val jwtSecret = readEnv("JWT_SECRET")?.trim().orEmpty()
        when {
            jwtSecret.isEmpty() ->
                failures += "JWT_SECRET is required in production (generate with: openssl rand -base64 32)."
            jwtSecret.length < MIN_JWT_SECRET_LENGTH ->
                failures += "JWT_SECRET must be at least $MIN_JWT_SECRET_LENGTH characters (got ${jwtSecret.length})."
            jwtSecret.lowercase() in INSECURE_JWT_SECRETS ->
                failures += "JWT_SECRET is set to a known placeholder value — rotate it before deploying."
        }

        val dbPassword = readEnv("DATABASE_PASSWORD")?.trim().orEmpty()
        val dbUrlHasCredentials = readEnv("DATABASE_URL")?.contains("@") == true
        when {
            dbPassword.isEmpty() && !dbUrlHasCredentials ->
                failures += "DATABASE_PASSWORD is required in production (or embed credentials in DATABASE_URL)."
            dbPassword.lowercase() in INSECURE_DB_PASSWORDS ->
                failures += "DATABASE_PASSWORD is set to a known default — use a real secret."
        }

        validateWebAuthn(readEnv, failures)

        if (failures.isNotEmpty()) {
            val message =
                buildString {
                    appendLine("Refusing to start LogDate server: insecure production configuration.")
                    failures.forEach { appendLine("  - $it") }
                    append("Set LOGDATE_ENV to 'development' for local runs, or fix the values above.")
                }
            throw InsecureProductionConfigException(message)
        }
    }

    private fun validateWebAuthn(
        readEnv: (String) -> String?,
        failures: MutableList<String>,
    ) {
        // Passkey credentials are pinned to the relying-party ID at registration time, so a
        // production rollout that silently derives the RP ID from the request origin (the default
        // fallback in `WebAuthnConfig.fromEnvironment`) will quietly bind every new passkey to
        // whatever host first received traffic — typically a Cloud Run revision URL or a
        // preview subdomain. Those passkeys will not work against the canonical apex once DNS is
        // pointed at production. The fix is to require the operator to set both env vars
        // explicitly and to verify their relationship before we boot.
        val rpId = readEnv("WEBAUTHN_RP_ID")?.trim().orEmpty()
        val webauthnOrigin = readEnv("WEBAUTHN_ORIGIN")?.trim().orEmpty()

        if (rpId.isEmpty()) {
            failures +=
                "WEBAUTHN_RP_ID is required in production (set to the registrable apex you serve passkeys for, e.g. 'logdate.app'). " +
                "Without it the server falls back to deriving from request origin, which pins passkeys to whichever host happened to serve the registration."
        }
        if (webauthnOrigin.isEmpty()) {
            failures += "WEBAUTHN_ORIGIN is required in production (e.g. 'https://cloud.logdate.app')."
            return
        }
        if (!webauthnOrigin.startsWith("https://")) {
            failures += "WEBAUTHN_ORIGIN must use https:// scheme in production (got '$webauthnOrigin')."
            return
        }
        if (rpId.isEmpty()) return

        val originHost = runCatching { URI(webauthnOrigin).host?.lowercase()?.trim() }.getOrNull()
        if (originHost.isNullOrBlank()) {
            failures += "WEBAUTHN_ORIGIN '$webauthnOrigin' does not contain a parseable host."
            return
        }
        val rpIdLower = rpId.lowercase()
        val matchesExactly = originHost == rpIdLower
        val isSubdomain = originHost.endsWith(".$rpIdLower")
        if (!matchesExactly && !isSubdomain) {
            failures +=
                "WEBAUTHN_RP_ID '$rpId' must equal or be the registrable apex of WEBAUTHN_ORIGIN host '$originHost'. " +
                "Otherwise passkeys created against the origin will not be recognized when the user returns."
        }
    }
}
