package app.logdate.client.domain.restore

import app.logdate.client.media.storage.UnavailableMediaReferences
import kotlinx.serialization.Serializable

@Serializable
enum class RecoveryMediaRole { ORIGINAL, THUMBNAIL }

@Serializable
enum class UnavailableMediaReason { NOT_IN_ARCHIVE, IMPORT_FAILED, NOT_SELECTED }

/** Identifies an attachment patch without treating a missing original as a missing entry. */
@Serializable
data class UnavailableMediaAsset(
    val entityId: String,
    val blockId: String? = null,
    val role: RecoveryMediaRole = RecoveryMediaRole.ORIGINAL,
    val reference: String,
    val archivePath: String? = null,
    val reason: UnavailableMediaReason,
    val sourceReference: String? = null,
    val recoveredReference: String? = null,
    val kind: String? = null,
    val expectedSha256: String? = null,
)

internal fun unavailableArchiveReference(
    entityId: String,
    blockId: String? = null,
    role: RecoveryMediaRole = RecoveryMediaRole.ORIGINAL,
    generation: String? = null,
): String = UnavailableMediaReferences.archive(entityId, blockId, role.name.lowercase(), generation)

internal fun MediaResolution.unavailableAsset(
    entityId: String,
    blockId: String? = null,
    role: RecoveryMediaRole = RecoveryMediaRole.ORIGINAL,
    generation: String? = null,
    kind: String? = null,
): UnavailableMediaAsset? =
    unavailableReason?.let { reason ->
        uri?.let {
            UnavailableMediaAsset(
                entityId,
                blockId,
                role,
                unavailableArchiveReference(entityId, blockId, role, generation),
                archivePath,
                reason,
                it,
                kind = kind,
            )
        }
    }

internal fun MediaResolution.retainedReference(
    entityId: String,
    blockId: String? = null,
    role: RecoveryMediaRole = RecoveryMediaRole.ORIGINAL,
    generation: String? = null,
): String? = if (unavailableReason == null) uri else unavailableArchiveReference(entityId, blockId, role, generation)
