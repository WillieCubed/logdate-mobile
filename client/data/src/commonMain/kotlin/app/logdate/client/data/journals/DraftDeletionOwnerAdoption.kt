package app.logdate.client.data.journals

import app.logdate.client.datastore.KeyValueStorage
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import kotlin.uuid.Uuid

/** Transfers only deletion intent proven by the consented history-adoption journal. */
internal class DraftDeletionOwnerAdoption(
    private val storage: KeyValueStorage,
) {
    suspend fun transfer(
        oldOwner: String,
        newOwner: String,
        origin: String,
        proofKey: String,
        proof: String,
        bind: suspend () -> Unit,
    ) {
        require(oldOwner.isNotBlank() && newOwner.isNotBlank() && origin.isNotBlank())
        val request = Adoption(oldOwner, newOwner, origin, proofKey, proof)
        // The caller has finished its Room transaction before taking the draft lock.
        LocalFirstDraftRepository.storageMutex.withLock {
            check(storage.getStringOrThrow(proofKey) == proof) { "Draft adoption consent changed" }
            if (oldOwner == newOwner) {
                bind()
                return@withLock
            }
            val receipt = prepare(request)
            // Source shields remain active if binding or its durable receipt fails.
            bind()
            if (receipt != null && !receipt.consumed) consume(request, receipt)
        }
    }

    private suspend fun prepare(request: Adoption): DeletionAdoption? {
        check(storage.getStringOrThrow(request.proofKey) == request.proof) { "Draft adoption consent changed" }
        val previous = storage.getStringOrThrow(request.receiptKey)
        val sources = request.sourceKeys.associateWith { read(storage.getStringOrThrow(it)) }
        if (previous == null && sources.values.all { it.localDeletionIds.isEmpty() }) return null
        val keys = request.sourceKeys + request.destination + request.receiptKey + request.proofKey
        val snapshot = storage.getStringsOrThrow(keys)
        check(snapshot[request.proofKey] == request.proof) { "Draft adoption consent changed" }
        val receipt = snapshot[request.receiptKey]?.let { Json.decodeFromString<DeletionAdoption>(it) }
        if (receipt != null) return receipt.also { requireValid(it, request) }
        val prepared =
            DeletionAdoption(
                request.oldOwner,
                request.newOwner,
                request.origin,
                request.proof,
                request.sourceKeys.associateWith { read(snapshot[it]).localDeletionIds },
            )
        requireValid(prepared, request)
        val state = read(snapshot[request.destination])
        val updated = state.copy(localDeletionIds = state.localDeletionIds + prepared.sources.values.flatten())
        check(
            storage.compareAndSetStringsOrThrow(
                snapshot,
                mapOf(
                    request.destination to updated.encode(Json),
                    request.receiptKey to Json.encodeToString(prepared),
                ),
            ),
        ) { "Draft deletion custody changed during adoption" }
        return prepared
    }

    private suspend fun consume(
        request: Adoption,
        receipt: DeletionAdoption,
    ) {
        // The completed identity proof is durable before consuming source shields.
        val snapshot = storage.getStringsOrThrow(request.sourceKeys + request.receiptKey)
        check(snapshot[request.receiptKey] == Json.encodeToString(receipt)) { "Draft deletion custody changed during binding" }
        val updates =
            receipt.sources.mapValues { (key, ids) ->
                val current = read(snapshot[key])
                current.copy(localDeletionIds = current.localDeletionIds - ids).encode(Json)
            } + (request.receiptKey to Json.encodeToString(receipt.copy(consumed = true)))
        check(storage.compareAndSetStringsOrThrow(snapshot, updates)) { "Draft deletion custody changed during completion" }
    }

    private fun read(raw: String?) = ArchiveDraftStorageState.decode(Json, raw)

    private fun requireValid(
        receipt: DeletionAdoption,
        request: Adoption,
    ) {
        check(
            receipt.oldOwner == request.oldOwner &&
                receipt.newOwner == request.newOwner &&
                receipt.origin == request.origin &&
                receipt.proof == request.proof,
        )
        check(
            receipt.sources.keys == request.sourceKeys &&
                receipt.sources.values
                    .flatten()
                    .all { Uuid.parseOrNull(it) != null },
        )
    }

    private class Adoption(
        val oldOwner: String,
        val newOwner: String,
        val origin: String,
        val proofKey: String,
        val proof: String,
    ) {
        val sourceKeys = setOf(scopedKey(oldOwner, origin), LocalFirstDraftRepository.ANONYMOUS_KEY)
        val destination = scopedKey(newOwner, origin)
        val receiptKey = "draft_deletion_adoption_v1_" + proof.encodeUtf8().sha256().hex()

        private fun scopedKey(
            owner: String,
            origin: String,
        ) = "editor_drafts_scope_${owner.length}:$owner:${origin.length}:$origin"
    }

    @Serializable
    private data class DeletionAdoption(
        val oldOwner: String,
        val newOwner: String,
        val origin: String,
        val proof: String,
        val sources: Map<String, Set<String>>,
        val consumed: Boolean = false,
    )
}
