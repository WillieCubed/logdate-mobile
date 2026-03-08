package app.logdate.server.identity

/**
 * Server-side configuration for AT Protocol identity surfaces.
 */
enum class HostedAccountDidMethod {
    PLC,
    WEB,
}

/**
 * Server-side configuration for AT Protocol identity surfaces.
 */
data class AtprotoIdentityConfig(
    val handleDomain: String = "logdate.app",
    val pdsServiceEndpoint: String = "https://logdate.app",
    val hostedAccountDidMethod: HostedAccountDidMethod = HostedAccountDidMethod.PLC,
    val publishHostedPlcOperations: Boolean = false,
    val plcDirectoryUrl: String = "https://plc.directory",
) {
    init {
        require(handleDomain.isNotBlank()) { "handleDomain must not be blank" }
        require(pdsServiceEndpoint.startsWith("https://")) { "pdsServiceEndpoint must use https" }
        require(plcDirectoryUrl.startsWith("https://")) { "plcDirectoryUrl must use https" }
    }

    val normalizedHandleDomain: String = handleDomain.trim().trim('.').lowercase()
    val serverDid: String = "did:web:$normalizedHandleDomain"
    val normalizedPlcDirectoryUrl: String = plcDirectoryUrl.trim().removeSuffix("/")

    companion object {
        fun fromEnvironment(
            handleDomain: String? = System.getenv("ATPROTO_HANDLE_DOMAIN"),
            pdsServiceEndpoint: String? = System.getenv("ATPROTO_PDS_SERVICE_URL") ?: System.getenv("ATPROTO_PDS_SERVICE_ENDPOINT"),
            hostedAccountDidMethod: String? = System.getenv("ATPROTO_HOSTED_DID_METHOD"),
            publishHostedPlcOperations: String? = System.getenv("ATPROTO_PLC_PUBLISH_ENABLED"),
            plcDirectoryUrl: String? = System.getenv("ATPROTO_PLC_DIRECTORY_URL"),
        ): AtprotoIdentityConfig {
            val normalizedHandleDomain =
                handleDomain
                    ?.trim()
                    ?.trim('.')
                    ?.lowercase()
                    .orEmpty()
                    .ifBlank { "logdate.app" }
            val resolvedEndpoint = pdsServiceEndpoint?.trim().orEmpty().ifBlank { "https://$normalizedHandleDomain" }
            val resolvedHostedDidMethod =
                when (hostedAccountDidMethod?.trim()?.lowercase()) {
                    null, "" -> HostedAccountDidMethod.PLC
                    "plc" -> HostedAccountDidMethod.PLC
                    "web" -> HostedAccountDidMethod.WEB
                    else -> throw IllegalArgumentException("Unsupported hosted DID method: $hostedAccountDidMethod")
                }
            return AtprotoIdentityConfig(
                handleDomain = normalizedHandleDomain,
                pdsServiceEndpoint = resolvedEndpoint.removeSuffix("/"),
                hostedAccountDidMethod = resolvedHostedDidMethod,
                publishHostedPlcOperations = publishHostedPlcOperations?.trim()?.equals("true", ignoreCase = true) == true,
                plcDirectoryUrl =
                    plcDirectoryUrl
                        ?.trim()
                        .orEmpty()
                        .ifBlank { "https://plc.directory" }
                        .removeSuffix("/"),
            )
        }
    }
}
