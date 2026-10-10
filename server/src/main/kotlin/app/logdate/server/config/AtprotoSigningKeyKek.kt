package app.logdate.server.config

/**
 * Supplies the secret that encrypts hosted AT Protocol signing keys at rest.
 *
 * The value is used byte for byte, so a deployment must keep exactly the value that encrypted its
 * stored keys: a different value leaves every stored key unreadable. Production has no fallback;
 * [ProductionConfigValidator] refuses to start without the secret, and [resolve] throws.
 */
object AtprotoSigningKeyKek {
    const val ENV_VAR: String = "ATPROTO_SIGNING_KEY_KEK"

    /** Published in this source file, so it only ever protects keys on a developer's machine. */
    const val DEVELOPMENT_VALUE: String = "logdate-atproto-dev-signing-key"

    fun resolve(
        readEnv: (String) -> String? = System::getenv,
        profile: RuntimeProfile = RuntimeProfile.fromEnvironment(readEnv),
    ): String {
        val configured = readEnv(ENV_VAR)
        if (!configured.isNullOrBlank()) return configured
        check(!profile.isProduction) { "$ENV_VAR is required in production." }
        return DEVELOPMENT_VALUE
    }
}
