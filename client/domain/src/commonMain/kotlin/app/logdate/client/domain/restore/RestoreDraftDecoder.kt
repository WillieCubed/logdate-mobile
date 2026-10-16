package app.logdate.client.domain.restore

import app.logdate.client.domain.export.ExportDraft
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableAudioBlock
import app.logdate.shared.model.SerializableCameraBlock
import app.logdate.shared.model.SerializableEntryBlock
import app.logdate.shared.model.SerializableImageBlock
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.SerializableVideoBlock
import io.github.aakira.napier.Napier
import kotlin.uuid.Uuid

internal class RestoreDraftDecoder(
    private val resolveMediaReference: suspend (String?) -> MediaResolution,
    private val warnings: MutableList<String>,
    private val onMediaImported: (Boolean) -> Unit,
) {
    suspend fun decode(draft: ExportDraft): EditorDraft {
        val restoredBlocks =
            if (draft.blocks.isNotEmpty()) {
                draft.blocks.mapNotNull { block ->
                    restoreDraftBlock(block)
                }
            } else {
                emptyList()
            }

        if (restoredBlocks.isNotEmpty()) {
            return EditorDraft(
                id = runCatching { Uuid.parse(draft.id) }.getOrDefault(Uuid.random()),
                blocks = restoredBlocks,
                selectedJournalIds = draft.parseSelectedJournalIds(),
                createdAt = draft.createdAt,
                lastModifiedAt = draft.updatedAt,
            )
        }

        val blocks = mutableListOf<SerializableEntryBlock>()
        if (draft.content.isNotBlank()) {
            blocks.add(
                SerializableTextBlock(
                    id = Uuid.random(),
                    timestamp = draft.createdAt,
                    locationLat = draft.location?.latitude,
                    locationLng = draft.location?.longitude,
                    content = draft.content,
                ),
            )
        }

        draft.mediaReferences.forEach { reference ->
            val resolution = resolveMediaReference(reference)
            onMediaImported(resolution.imported)
            val uri = resolution.uri ?: return@forEach
            blocks.add(
                SerializableCameraBlock(
                    id = Uuid.random(),
                    timestamp = draft.updatedAt,
                    uri = uri,
                ),
            )
        }

        val selectedJournalIds =
            draft.parseSelectedJournalIds()

        return EditorDraft(
            id = runCatching { Uuid.parse(draft.id) }.getOrDefault(Uuid.random()),
            blocks = blocks,
            selectedJournalIds = selectedJournalIds,
            createdAt = draft.createdAt,
            lastModifiedAt = draft.updatedAt,
        )
    }

    private suspend fun restoreDraftBlock(block: SerializableEntryBlock): SerializableEntryBlock? =
        when (block) {
            is SerializableTextBlock -> block
            is SerializableImageBlock ->
                resolveSimpleBlock(block.uri, block.id) {
                    block.copy(uri = it)
                }
            is SerializableVideoBlock -> {
                val mediaResolution = resolveMediaReference(block.uri)
                onMediaImported(mediaResolution.imported)
                val thumbnailResolution = resolveMediaReference(block.thumbnailUri)
                onMediaImported(thumbnailResolution.imported)
                resolvedDraftBlockOrDropped(block.uri, mediaResolution, block.id) {
                    block.copy(uri = it, thumbnailUri = thumbnailResolution.uri)
                }
            }
            is SerializableAudioBlock ->
                resolveSimpleBlock(block.uri, block.id) {
                    block.copy(uri = it)
                }
            is SerializableCameraBlock ->
                resolveSimpleBlock(block.uri, block.id) {
                    block.copy(uri = it)
                }
        }

    /**
     * Shared resolution path for the draft block types that only carry a single media
     * reference (image, audio, camera). [SerializableVideoBlock] resolves a second
     * reference (its thumbnail) and so is handled separately in [restoreDraftBlock].
     */
    private suspend fun resolveSimpleBlock(
        uri: String?,
        blockId: Uuid,
        copy: (String?) -> SerializableEntryBlock,
    ): SerializableEntryBlock? {
        val resolution = resolveMediaReference(uri)
        onMediaImported(resolution.imported)
        return resolvedDraftBlockOrDropped(uri, resolution, blockId, copy)
    }

    /**
     * Drops a draft media block when it had a real reference that couldn't be resolved, rather
     * than silently keeping it with a dead `uri = null`. A block that never had media to begin
     * with (blank/null [originalUri]) is left alone -- there was nothing to resolve.
     */
    private fun resolvedDraftBlockOrDropped(
        originalUri: String?,
        resolution: MediaResolution,
        blockId: Uuid,
        copy: (String?) -> SerializableEntryBlock,
    ): SerializableEntryBlock? {
        if (resolution.uri == null && !originalUri.isNullOrBlank()) {
            val message = "Skipped draft media block (ID: $blockId) - media file not found in archive"
            Napier.w(message)
            warnings.add(message)
            return null
        }
        return copy(resolution.uri)
    }

    private fun ExportDraft.parseSelectedJournalIds(): List<Uuid> =
        if (journalIds.isNotEmpty()) {
            journalIds.mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }
        } else {
            journalId?.let { value ->
                runCatching { listOf(Uuid.parse(value)) }.getOrNull() ?: emptyList()
            } ?: emptyList()
        }
}
