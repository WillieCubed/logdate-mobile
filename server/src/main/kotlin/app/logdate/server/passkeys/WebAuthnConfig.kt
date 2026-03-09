package app.logdate.server.passkeys

/**
 * Server-side configuration for WebAuthn relying-party metadata.
 */
data class WebAuthnConfig(
    val relyingPartyId: String,
    val relyingPartyName: String,
    val origin: String,
) {
    init {
        require(relyingPartyId.isNotBlank()) { "relyingPartyId must not be blank" }
        require(relyingPartyName.isNotBlank()) { "relyingPartyName must not be blank" }
        require(origin.startsWith("https://")) { "origin must use https" }
    }

    companion object {
        fun fromEnvironment(
            relyingPartyId: String? = System.getenv("WEBAUTHN_RP_ID"),
            relyingPartyName: String? = System.getenv("WEBAUTHN_RP_NAME"),
            origin: String? = System.getenv("WEBAUTHN_ORIGIN"),
        ): WebAuthnConfig {
            val resolvedRpId = relyingPartyId?.trim().orEmpty().ifBlank { "logdate.app" }
            return WebAuthnConfig(
                relyingPartyId = resolvedRpId,
                relyingPartyName = relyingPartyName?.trim().orEmpty().ifBlank { "LogDate" },
                origin = origin?.trim().orEmpty().ifBlank { "https://app.logdate.com" },
            )
        }
    }
}
