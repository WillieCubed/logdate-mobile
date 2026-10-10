package app.logdate.server.config

/**
 * A secret with one job that production must set explicitly, never borrowing another secret's value.
 *
 * The value is used byte for byte. Production has no fallback: [ProductionConfigValidator] refuses to
 * start without it, and [resolve] throws. Development and tests use [developmentValue], which is
 * published in this source file and so only ever protects data on a developer's machine.
 */
abstract class DedicatedSecret(
    val envVar: String,
    val developmentValue: String,
) {
    fun resolve(
        readEnv: (String) -> String? = System::getenv,
        profile: RuntimeProfile = RuntimeProfile.fromEnvironment(readEnv),
    ): String {
        val configured = readEnv(envVar)
        if (!configured.isNullOrBlank()) return configured
        check(!profile.isProduction) { "$envVar is required in production." }
        return developmentValue
    }
}

/**
 * Encrypts hosted AT Protocol signing keys at rest. A deployment must keep exactly the value that
 * encrypted its stored keys: a different value leaves every stored key unreadable.
 */
object AtprotoSigningKeyKek : DedicatedSecret(
    envVar = "ATPROTO_SIGNING_KEY_KEK",
    developmentValue = "logdate-atproto-dev-signing-key",
)

/** Signs hosted AT Protocol session tokens. A different value signs out every AT Protocol session. */
object AtprotoSessionSecret : DedicatedSecret(
    envVar = "ATPROTO_SESSION_SECRET",
    developmentValue = "logdate-atproto-session-dev",
)
