package app.logdate.server.passkeys

import java.net.URI

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
            serverOrigin: String? = System.getenv("LOGDATE_PUBLIC_ORIGIN"),
        ): WebAuthnConfig {
            val resolvedOrigin = normalizeOrigin(origin, serverOrigin)
            val resolvedRpId = relyingPartyId?.trim().orEmpty().ifBlank { deriveRpId(resolvedOrigin) ?: "logdate.app" }
            return WebAuthnConfig(
                relyingPartyId = resolvedRpId,
                relyingPartyName = relyingPartyName?.trim().orEmpty().ifBlank { "LogDate" },
                origin = resolvedOrigin,
            )
        }

        private fun normalizeOrigin(
            explicitOrigin: String?,
            serverOrigin: String?,
        ): String =
            explicitOrigin
                ?.trim()
                .orEmpty()
                .ifBlank {
                    serverOrigin
                        ?.trim()
                        .orEmpty()
                        .ifBlank { "https://app.logdate.com" }
                }.removeSuffix("/")

        private fun deriveRpId(origin: String): String? = runCatching { URI(origin).host?.trim()?.ifBlank { null } }.getOrNull()
    }
}
