package app.logdate.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class DeploymentKind {
    FIRST_PARTY,
    SELF_HOSTED,
}

@Serializable
enum class ServerCapability {
    AUTH_PASSKEY,
    SYNC_CONTENT,
    SYNC_MEDIA,
    ATPROTO_IDENTITY,
    ATPROTO_OAUTH,
    BILLING_SUBSCRIPTIONS,
    MANAGED_QUOTA,
}

@Serializable
data class ServerPasskeyConfig(
    val rpId: String,
    val rpName: String,
)

@Serializable
data class ServerDescriptor(
    val serverOrigin: String,
    val apiBaseUrl: String,
    val apiVersion: String = "v1",
    val deploymentKind: DeploymentKind,
    val displayName: String,
    val handleDomain: String? = null,
    val passkey: ServerPasskeyConfig? = null,
    val capabilities: List<ServerCapability> = emptyList(),
) {
    fun hasCapability(capability: ServerCapability): Boolean = capabilities.contains(capability)
}

@Serializable
data class ServerInfoResponse(
    val success: Boolean,
    val data: ServerDescriptor,
)
