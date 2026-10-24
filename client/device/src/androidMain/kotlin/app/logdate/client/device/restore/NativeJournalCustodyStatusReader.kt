package app.logdate.client.device.restore

import android.content.Context
import kotlinx.coroutines.runBlocking

/** Presents durable transfer custody without exposing private paths or storage exceptions. */
class NativeJournalCustodyStatusReader(
    private val context: Context,
    private val stagedMediaInventories: () -> List<NativeJournalMediaInventory> = { emptyList() },
    private val isRecoverableStagedAuxiliary: (NativeIncomingJournal.Payload) -> Boolean = { false },
) {
    fun read(): NativeRestoreCustodyStatus = runBlocking { AndroidNativeRestoreDeliveryGate.withLock { readAfterDelivery() } }

    private fun readAfterDelivery(): NativeRestoreCustodyStatus {
        return runCatching {
            val custody = NativeJournalReplayCustody(context)
            if (!custody.hasInputs()) return@runCatching NativeRestoreCustodyStatus.CLEAR
            val payloads = custody.sealedPayloads()
            val authority = NativeJournalRestoreAuthority(context)
            val inventories = listOfNotNull(authority.completedMediaInventory()) + stagedMediaInventories()
            val recoverableAuxiliary = payloads.filterTo(mutableSetOf()) { it.complete && isRecoverableStagedAuxiliary(it) }
            if (payloads.isEmpty() || payloads.any { !it.complete || !knownPath(it, inventories, it in recoverableAuxiliary) }) {
                return@runCatching NativeRestoreCustodyStatus.NEEDS_TRANSFER
            }
            if (verifiedNativeJournalSources(payloads).isNotEmpty() ||
                payloads.all { it in recoverableAuxiliary } ||
                payloads.all { payload ->
                    payload in recoverableAuxiliary ||
                        (CANONICAL_MEDIA.matches(payload.path) && authority.completedSourceReceipt() != null) ||
                        inventories.any { it.entry(payload.path) != null }
                }
            ) {
                NativeRestoreCustodyStatus.RECOVERING
            } else {
                NativeRestoreCustodyStatus.NEEDS_TRANSFER
            }
        }.getOrDefault(NativeRestoreCustodyStatus.NEEDS_TRANSFER)
    }

    private fun knownPath(
        payload: NativeIncomingJournal.Payload,
        inventories: List<NativeJournalMediaInventory>,
        recoverableAuxiliary: Boolean,
    ): Boolean {
        if (recoverableAuxiliary) return true
        val path = payload.path
        val logical = path.removePrefix(NATIVE_JOURNAL_TRANSFER_PREFIX)
        val sourcePath = path.startsWith(NATIVE_JOURNAL_TRANSFER_PREFIX)
        if (sourcePath && logical in SOURCE_PATHS) return true
        if (CANONICAL_MEDIA.matches(logical)) return true
        if (logical.startsWith("files/user_media/") || logical.startsWith("files/audio_notes/")) {
            return inventories.any { inventory ->
                inventory.entry(logical)?.let { entry ->
                    entry.size == payload.file.length() && entry.sha256 == retainedJournalChecksum(payload.file)
                } == true
            }
        }
        return false
    }

    private companion object {
        val SOURCE_PATHS =
            setOf(
                NATIVE_SOURCE_RECEIPT_PATH,
                NATIVE_MEDIA_INVENTORY_PATH,
                "sender_owner",
                "database/logdate",
                "database/logdate-wal",
                "database/logdate-shm",
                "database/logdate-journal",
                "files/logdate.preferences_pb",
                "files/db_recovery/passphrase_backup",
                "files/db_recovery/working_passphrase_backup",
            )
        val CANONICAL_MEDIA =
            Regex("files/media/objects/sha256/[a-f0-9]{2}/[a-f0-9]{64}\\.[^/.]+", RegexOption.IGNORE_CASE)
    }
}
