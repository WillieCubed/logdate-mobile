@file:Suppress("DEPRECATION")

package app.logdate.client.repository.assistants

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
enum class AssistantCapability {
    @SerialName("memories:read")
    READ_MEMORIES,

    @SerialName("entries:add")
    PREPARE_ENTRIES,
}

/** Device-held consent. Changes to any permission require a new revision. */
data class AssistantGrant(
    val id: Uuid,
    val revision: Long,
    val accountId: Uuid,
    val accountServerOrigin: String,
    val clientId: String,
    val deviceId: String,
    val resource: String,
    val capabilities: Set<AssistantCapability>,
    val contentScope: AssistantContentScope,
    val destinationJournalId: Uuid?,
    val approvedAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant? = null,
) {
    override fun toString(): String = "AssistantGrant([redacted])"
}
