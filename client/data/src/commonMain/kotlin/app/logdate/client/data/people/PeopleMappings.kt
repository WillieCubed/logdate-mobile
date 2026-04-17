package app.logdate.client.data.people

import app.logdate.client.database.entities.people.InferredPersonClusterEntity
import app.logdate.client.database.entities.people.InferredPersonEvidenceEntity
import app.logdate.client.database.entities.people.PersonEntity
import app.logdate.client.database.entities.people.PersonLinkEntity
import app.logdate.shared.model.InferredPersonCluster
import app.logdate.shared.model.InferredPersonClusterStatus
import app.logdate.shared.model.InferredPersonEvidence
import app.logdate.shared.model.InferredPersonEvidenceSourceType
import app.logdate.shared.model.Person
import app.logdate.shared.model.PersonLink
import app.logdate.shared.model.PersonLinkProvenance
import app.logdate.shared.model.PersonLinkStatus
import app.logdate.shared.model.PersonLinkTargetType
import app.logdate.shared.model.PersonOrigin
import kotlin.time.Clock

internal fun PersonEntity.toModel(): Person =
    Person(
        uid = id,
        name = name,
        photoUri = photoUri,
        aliases = aliases,
        relationshipLabel = relationshipLabel,
        notes = notes,
        origin = runCatching { PersonOrigin.valueOf(origin) }.getOrDefault(PersonOrigin.MANUAL),
    )

internal fun Person.toEntity(
    createdAt: kotlin.time.Instant,
    contactLookupKey: String? = null,
): PersonEntity {
    val now = Clock.System.now()
    val normalizedName = normalizePersonName(name)
    return PersonEntity(
        id = uid,
        name = normalizedName,
        photoUri = normalizeOptionalText(photoUri),
        aliases = normalizePersonAliases(aliases, normalizedName),
        relationshipLabel = normalizeOptionalText(relationshipLabel),
        notes = normalizeOptionalText(notes),
        origin = origin.name,
        contactLookupKey = normalizeOptionalText(contactLookupKey),
        created = createdAt,
        lastUpdated = now,
    )
}

internal fun InferredPersonClusterEntity.toModel(): InferredPersonCluster =
    InferredPersonCluster(
        uid = id,
        displayNameHint = displayNameHint,
        status =
            runCatching {
                InferredPersonClusterStatus.valueOf(status)
            }.getOrDefault(InferredPersonClusterStatus.OPEN),
        linkedPersonId = linkedPersonId,
        createdAt = created,
        lastUpdated = lastUpdated,
    )

internal fun InferredPersonEvidenceEntity.toModel(): InferredPersonEvidence =
    InferredPersonEvidence(
        uid = id,
        clusterId = clusterId,
        sourceType =
            runCatching {
                InferredPersonEvidenceSourceType.valueOf(sourceType)
            }.getOrDefault(InferredPersonEvidenceSourceType.ENTRY_TEXT),
        sourceId = sourceId,
        label = label,
        confidence = confidence,
        createdAt = created,
    )

internal fun PersonLinkEntity.toModel(): PersonLink =
    PersonLink(
        uid = id,
        personId = personId,
        targetType =
            runCatching {
                PersonLinkTargetType.valueOf(targetType)
            }.getOrDefault(PersonLinkTargetType.ENTRY),
        targetId = targetId,
        provenance =
            runCatching {
                PersonLinkProvenance.valueOf(provenance)
            }.getOrDefault(PersonLinkProvenance.INFERRED),
        confidence = confidence,
        status =
            runCatching {
                PersonLinkStatus.valueOf(status)
            }.getOrDefault(PersonLinkStatus.ACTIVE),
        createdAt = created,
        lastUpdated = lastUpdated,
    )
