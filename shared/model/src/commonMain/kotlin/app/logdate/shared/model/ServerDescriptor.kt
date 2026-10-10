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
    CLOUD_TRANSCRIPTION,
}

/** Additive protocol flags that old clients can safely ignore. */
object ServerProtocolFeature {
    const val CANONICAL_OWNER_BINDING_V1 = "canonicalOwnerBindingV1"
    const val JOURNAL_MERGE_V1 = "journalMergeV1"
    const val RICH_DRAFTS_V1 = "richDraftsV1"
    const val DIAGNOSTIC_REPORTS_V1 = "diagnosticReportsV1"

    /**
     * The server publishes hosted `did:plc` operations to the PLC directory, so identity changes
     * that need a published operation -- rotating the signing key, registering a recovery key,
     * recovering with one -- can succeed. Without it those requests are refused, and clients must
     * not offer them.
     */
    const val ATPROTO_PLC_PUBLISHING_V1 = "atprotoPlcPublishingV1"

    /** Legacy flag retained for compatibility; servers never advertise server custody of keys. */
    const val ACCOUNT_KEY_VAULT_V1 = "accountKeyVaultV1"

    /** Account-bound passkey envelopes contain ciphertext only, unlocked locally with the PRF. */
    const val ENCRYPTED_ACCOUNT_KEYS_V1 = "encryptedAccountKeysV1"

    /**
     * The server hosts shared journals, so a journal link opens a readable copy for the people it
     * was shared with. Without it a journal link reaches no one, and clients must not offer one.
     */
    const val JOURNAL_SHARE_LINKS_V1 = "journalShareLinksV1"
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
    val protocolFeatures: List<String> = emptyList(),
    val privacyPolicyUrl: String? = null,
    val termsOfServiceUrl: String? = null,
) {
    fun hasCapability(capability: ServerCapability): Boolean = capabilities.contains(capability)

    fun hasProtocolFeature(feature: String): Boolean = protocolFeatures.contains(feature)
}

@Serializable
data class ServerInfoResponse(
    val success: Boolean,
    val data: ServerDescriptor,
)
