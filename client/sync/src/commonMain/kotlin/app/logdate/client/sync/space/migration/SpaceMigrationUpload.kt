package app.logdate.client.sync.space.migration

import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.space.SpaceRecordInvalidException
import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.client.sync.space.outgoingRecords
import io.github.aakira.napier.Napier
import studio.hypertext.atproto.lexicon.LexiconRecordValidator
import kotlin.coroutines.cancellation.CancellationException

/** How many items are uploaded between saved checkpoints. */
private const val CHECKPOINT_EVERY = 25

/** Items of [kind] that this version of LogDate can't write as records, so moving them would lose them. */
internal class MigrationUnsupportedItemsException(
    val kind: JournalDataKind,
    val count: Int,
) : Exception("$count ${kind.name.lowercase()} can't be written as personal-space records")

/**
 * Uploads every item of every kind as sealed records, resuming after the last item a saved
 * checkpoint names. Items are uploaded in a fixed order, sorted by id within each kind, so the
 * checkpoint names a place in that order; an item that changes after it was uploaded is caught by the
 * check and uploaded again then.
 */
internal class SpaceMigrationUpload(
    private val writer: SpaceMigrationWriter,
    private val validator: LexiconRecordValidator,
    private val checkpoints: SpaceMigrationCheckpoints,
    private val scope: UploadScope,
    private val progress: (done: Int, total: Int) -> Unit,
) {
    suspend fun run(
        kinds: List<MigrationKind>,
        start: SpaceMigrationCheckpoint,
    ): SpaceMigrationCheckpoint {
        val ids = kinds.associateWith { it.orderedIds() }
        val total = ids.values.sumOf { it.size }
        var done = 0
        var current = start
        for (kind in kinds) {
            val type = kind.entityType.name
            val all = ids.getValue(kind)
            if (type in current.finishedKinds) {
                done += all.size
                continue
            }
            val after = current.uploadedThrough[type]
            done += all.count { after != null && it <= after }
            var unsupported = 0
            for (batch in all.filter { after == null || it > after }.chunked(CHECKPOINT_EVERY)) {
                batch.forEach { id -> if (!uploadItem(kind, id)) unsupported++ }
                done += batch.size
                current = current.copy(uploadedThrough = current.uploadedThrough + (type to batch.last()))
                checkpoints.save(scope, current)
                progress(done, total)
            }
            if (unsupported > 0) throw MigrationUnsupportedItemsException(kind.dataKind, unsupported)
            current = current.copy(finishedKinds = current.finishedKinds + type)
            checkpoints.save(scope, current)
        }
        current = current.copy(stage = SpaceMigrationStage.CHECKING)
        checkpoints.save(scope, current)
        return current
    }

    /**
     * Uploads the item [id] of [kind] as its records. Returns false when the item can't be written as
     * records at all; an item that no longer exists here counts as uploaded.
     */
    suspend fun uploadItem(
        kind: MigrationKind,
        id: String,
    ): Boolean {
        val item = kind.kind.local.load(id) ?: return true
        val records =
            try {
                kind.kind.outgoingRecords(kind.prepare(item), validator)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SpaceRecordMappingException) {
                if (error.retryable) throw error
                Napier.w("A ${kind.dataKind.name.lowercase()} item can't be written as a personal-space record")
                return false
            } catch (error: SpaceRecordInvalidException) {
                Napier.w("A ${kind.dataKind.name.lowercase()} record fails its schema")
                return false
            }
        writer.write(records)
        return true
    }
}
