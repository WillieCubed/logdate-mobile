package app.logdate.server

import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.shared.model.DeploymentKind
import app.logdate.shared.model.ServerCapability
import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.ServerPasskeyConfig

data class ServerDescriptorConfig(
    val deploymentKind: DeploymentKind = DeploymentKind.SELF_HOSTED,
    val displayName: String = defaultDisplayName(DeploymentKind.SELF_HOSTED),
    val privacyPolicyUrl: String? = null,
    val termsOfServiceUrl: String? = null,
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
                    add(ServerCapability.CLOUD_TRANSCRIPTION)
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
            privacyPolicyUrl = privacyPolicyUrl,
            termsOfServiceUrl = termsOfServiceUrl,
        )
    }

    companion object {
        fun fromEnvironment(
            deploymentKind: String? = System.getenv("LOGDATE_DEPLOYMENT_KIND"),
            displayName: String? = System.getenv("LOGDATE_SERVER_DISPLAY_NAME"),
            privacyPolicyUrl: String? = System.getenv("LOGDATE_PRIVACY_POLICY_URL"),
            termsOfServiceUrl: String? = System.getenv("LOGDATE_TERMS_OF_SERVICE_URL"),
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
                privacyPolicyUrl =
                    privacyPolicyUrl
                        ?.trim()
                        .orEmpty()
                        .ifBlank { defaultPrivacyPolicyUrl(resolvedDeploymentKind) },
                termsOfServiceUrl =
                    termsOfServiceUrl
                        ?.trim()
                        .orEmpty()
                        .ifBlank { defaultTermsOfServiceUrl(resolvedDeploymentKind) },
            )
        }

        private fun defaultDisplayName(deploymentKind: DeploymentKind): String =
            when (deploymentKind) {
                DeploymentKind.FIRST_PARTY -> "LogDate Cloud"
                DeploymentKind.SELF_HOSTED -> "LogDate Server"
            }

        private fun defaultPrivacyPolicyUrl(deploymentKind: DeploymentKind): String? =
            when (deploymentKind) {
                DeploymentKind.FIRST_PARTY -> "https://logdate.app/privacy"
                DeploymentKind.SELF_HOSTED -> null
            }

        private fun defaultTermsOfServiceUrl(deploymentKind: DeploymentKind): String? =
            when (deploymentKind) {
                DeploymentKind.FIRST_PARTY -> "https://logdate.app/terms"
                DeploymentKind.SELF_HOSTED -> null
            }
    }
}
