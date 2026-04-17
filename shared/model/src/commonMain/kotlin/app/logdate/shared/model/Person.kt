package app.logdate.shared.model

import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class PersonOrigin {
    MANUAL,
    INFERRED,
    CONTACT_SELECTED,
    CONTACT_FULL,
}

@OptIn(ExperimentalUuidApi::class)
data class Person(
    val uid: Uuid = Uuid.random(), // Allow entities to have the same name
    val name: String,
    val photoUri: String? = null,
    val aliases: List<String> = listOf(),
    val relationshipLabel: String? = null,
    val notes: String? = null,
    val origin: PersonOrigin = PersonOrigin.MANUAL,
)

enum class InferredPersonClusterStatus {
    OPEN,
    RESOLVED,
    REJECTED,
}

enum class InferredPersonEvidenceSourceType {
    CONTACT,
    ENTRY_TEXT,
    TRANSCRIPT,
    EVENT_TEXT,
    CO_OCCURRENCE,
}

enum class PersonLinkTargetType {
    ENTRY,
    EVENT,
}

enum class PersonLinkProvenance {
    EXPLICIT,
    INFERRED,
}

enum class PersonLinkStatus {
    ACTIVE,
    REJECTED,
}

@OptIn(ExperimentalUuidApi::class)
data class InferredPersonCluster(
    val uid: Uuid = Uuid.random(),
    val displayNameHint: String,
    val status: InferredPersonClusterStatus = InferredPersonClusterStatus.OPEN,
    val linkedPersonId: Uuid? = null,
    val createdAt: Instant,
    val lastUpdated: Instant,
)

@OptIn(ExperimentalUuidApi::class)
data class InferredPersonEvidence(
    val uid: Uuid = Uuid.random(),
    val clusterId: Uuid,
    val sourceType: InferredPersonEvidenceSourceType,
    val sourceId: Uuid,
    val label: String? = null,
    val confidence: Double,
    val createdAt: Instant,
)

@OptIn(ExperimentalUuidApi::class)
data class PersonLink(
    val uid: Uuid = Uuid.random(),
    val personId: Uuid,
    val targetType: PersonLinkTargetType,
    val targetId: Uuid,
    val provenance: PersonLinkProvenance,
    val confidence: Double,
    val status: PersonLinkStatus = PersonLinkStatus.ACTIVE,
    val createdAt: Instant,
    val lastUpdated: Instant,
)
