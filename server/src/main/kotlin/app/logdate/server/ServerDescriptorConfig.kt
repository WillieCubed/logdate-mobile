package app.logdate.server

import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.shared.model.DeploymentKind
import app.logdate.shared.model.ServerCapability
import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.ServerPasskeyConfig

data class ServerDescriptorConfig(
    val deploymentKind: DeploymentKind = DeploymentKind.SELF_HOSTED,
    val displayName: String = defaultDisplayName(DeploymentKind.SELF_HOSTED),
) {
    fun toDescriptor(
        identityConfig: AtprotoIdentityConfig,
        webAuthnRpId: String,
        webAuthnRpName: String,
    ): ServerDescriptor {
        val serverOrigin = identityConfig.pdsServiceEndpoint.trimEnd('/')
        val capabilities =
            buildList {
                add(ServerCapability.AUTH_PASSKEY)
                add(ServerCapability.SYNC_CONTENT)
                add(ServerCapability.SYNC_MEDIA)
                add(ServerCapability.ATPROTO_IDENTITY)
                add(ServerCapability.ATPROTO_OAUTH)
                if (deploymentKind == DeploymentKind.FIRST_PARTY) {
                    add(ServerCapability.BILLING_SUBSCRIPTIONS)
                    add(ServerCapability.MANAGED_QUOTA)
                }
            }

        return ServerDescriptor(
            serverOrigin = serverOrigin,
            apiBaseUrl = "$serverOrigin/api/v1",
            apiVersion = "v1",
            deploymentKind = deploymentKind,
            displayName = displayName,
            handleDomain = identityConfig.normalizedHandleDomain,
            passkey = ServerPasskeyConfig(rpId = webAuthnRpId, rpName = webAuthnRpName),
            capabilities = capabilities,
        )
    }

    companion object {
        fun fromEnvironment(
            deploymentKind: String? = System.getenv("LOGDATE_DEPLOYMENT_KIND"),
            displayName: String? = System.getenv("LOGDATE_SERVER_DISPLAY_NAME"),
        ): ServerDescriptorConfig {
            val resolvedDeploymentKind =
                when (deploymentKind?.trim()?.lowercase()) {
                    null, "", "self_hosted", "self-hosted" -> DeploymentKind.SELF_HOSTED
                    "first_party", "first-party", "firstparty" -> DeploymentKind.FIRST_PARTY
                    else -> throw IllegalArgumentException("Unsupported deployment kind: $deploymentKind")
                }
            return ServerDescriptorConfig(
                deploymentKind = resolvedDeploymentKind,
                displayName =
                    displayName
                        ?.trim()
                        .orEmpty()
                        .ifBlank { defaultDisplayName(resolvedDeploymentKind) },
            )
        }

        private fun defaultDisplayName(deploymentKind: DeploymentKind): String =
            when (deploymentKind) {
                DeploymentKind.FIRST_PARTY -> "LogDate Cloud"
                DeploymentKind.SELF_HOSTED -> "LogDate Server"
            }
    }
}
