package app.logdate.client.domain.restore

import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.SerializableAudioBlock
import app.logdate.shared.model.SerializableCameraBlock
import app.logdate.shared.model.SerializableEntryBlock
import app.logdate.shared.model.SerializableImageBlock
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.SerializableVideoBlock

internal fun JournalNote.withRecoveredOriginal(reference: String): JournalNote =
    when (this) {
        is JournalNote.Audio -> copy(mediaRef = reference)
        is JournalNote.Image -> copy(mediaRef = reference)
        is JournalNote.Video -> copy(mediaRef = reference)
        is JournalNote.Text -> this
    }

internal fun SerializableEntryBlock.archiveMediaReference(role: RecoveryMediaRole): String? =
    when (this) {
        is SerializableTextBlock -> null
        is SerializableImageBlock -> uri.takeIf { role == RecoveryMediaRole.ORIGINAL }
        is SerializableCameraBlock -> uri.takeIf { role == RecoveryMediaRole.ORIGINAL }
        is SerializableAudioBlock -> uri.takeIf { role == RecoveryMediaRole.ORIGINAL }
        is SerializableVideoBlock -> if (role == RecoveryMediaRole.ORIGINAL) uri else thumbnailUri
    }

internal fun SerializableEntryBlock.withRecoveredAsset(
    role: RecoveryMediaRole,
    reference: String,
): SerializableEntryBlock =
    when (this) {
        is SerializableTextBlock -> this
        is SerializableImageBlock -> copy(uri = reference)
        is SerializableCameraBlock -> copy(uri = reference)
        is SerializableAudioBlock -> copy(uri = reference)
        is SerializableVideoBlock -> if (role == RecoveryMediaRole.ORIGINAL) copy(uri = reference) else copy(thumbnailUri = reference)
    }

internal fun SerializableEntryBlock.archiveMediaKind(): String? =
    when (this) {
        is SerializableTextBlock -> null
        is SerializableImageBlock -> "image"
        is SerializableCameraBlock -> "camera"
        is SerializableAudioBlock -> "audio"
        is SerializableVideoBlock -> "video"
    }
