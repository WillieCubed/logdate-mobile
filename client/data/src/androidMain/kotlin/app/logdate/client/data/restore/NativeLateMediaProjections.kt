package app.logdate.client.data.restore

import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.mediaRefOrNull
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableAudioBlock
import app.logdate.shared.model.SerializableCameraBlock
import app.logdate.shared.model.SerializableEntryBlock
import app.logdate.shared.model.SerializableImageBlock
import app.logdate.shared.model.SerializableVideoBlock

internal fun JournalNote.lateMediaReferences(): Set<String> = setOfNotNull(mediaRefOrNull())

internal fun JournalNote.withLateMedia(replacements: Map<String, String>): JournalNote {
    val reference = replacements[mediaRefOrNull()] ?: return this
    return withRetainedMediaRef(reference)
}

internal fun EditorDraft.lateMediaReferences(): Set<String> = blocks.flatMap { it.lateMediaReferences() }.toSet()

internal fun EditorDraft.withLateMedia(replacements: Map<String, String>): EditorDraft =
    copy(blocks = blocks.map { it.withLateMedia(replacements) })

internal fun EntryDraft.lateMediaReferences(): Set<String> =
    buildSet {
        notes.forEach { addAll(it.lateMediaReferences()) }
        editBase?.original?.let { addAll(it.lateMediaReferences()) }
        pendingMedia.forEach { record ->
            record.filePath?.let(::add)
            record.fallbackFilePath?.let(::add)
        }
    }

internal fun EntryDraft.withLateMedia(replacements: Map<String, String>): EntryDraft =
    copy(
        notes = notes.map { it.withLateMedia(replacements) },
        editBase = editBase?.let { it.copy(original = it.original.withLateMedia(replacements)) },
        pendingMedia =
            pendingMedia.map {
                it.copy(
                    filePath = replacements[it.filePath] ?: it.filePath,
                    fallbackFilePath =
                        replacements[it.fallbackFilePath] ?: it.fallbackFilePath,
                )
            },
    )

private fun SerializableEntryBlock.lateMediaReferences(): List<String> =
    when (this) {
        is SerializableImageBlock -> listOfNotNull(uri)
        is SerializableVideoBlock -> listOfNotNull(uri, thumbnailUri)
        is SerializableAudioBlock -> listOfNotNull(uri)
        is SerializableCameraBlock -> listOfNotNull(uri)
        else -> emptyList()
    }

private fun SerializableEntryBlock.withLateMedia(replacements: Map<String, String>): SerializableEntryBlock =
    when (this) {
        is SerializableImageBlock -> copy(uri = replacements[uri] ?: uri)
        is SerializableVideoBlock -> copy(uri = replacements[uri] ?: uri, thumbnailUri = replacements[thumbnailUri] ?: thumbnailUri)
        is SerializableAudioBlock -> copy(uri = replacements[uri] ?: uri)
        is SerializableCameraBlock -> copy(uri = replacements[uri] ?: uri)
        else -> this
    }
