package app.logdate.server.config

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
}
