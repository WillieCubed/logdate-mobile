package app.logdate.client.data.restore

import app.logdate.client.device.restore.NativeJournalMediaInventory
import app.logdate.client.device.restore.NativeJournalReplayCustody
import app.logdate.client.media.storage.StoredMediaReferences
import app.logdate.client.media.storage.UnavailableMediaReferences
import app.logdate.client.media.storage.isRecoveredOriginal
import java.io.File

internal suspend fun repairNativeLateLegacyMedia(
    targets: NativeLateMediaTargets,
    repositories: NativeLateMediaRepositories,
    custody: NativeJournalReplayCustody,
    inventory: NativeJournalMediaInventory,
    sourceReceiptSha256: String,
    packageName: String,
    receiptsRoot: File,
    isDeliveryCurrent: () -> Boolean,
    acknowledge: Boolean,
): Boolean {
    val captured = targets.read()
    val currentReferences = captured.flatMap { it.references }.toSet()
    val receipts = NativeLateLegacyMediaReceipts(receiptsRoot, sourceReceiptSha256)
    val payloads = custody.sealedPayloads()
    val selected = selectNativeLegacyLateMediaCandidates(payloads, currentReferences, packageName, inventory)
    // After a successful CAS the legacy reference is gone. Its durable import receipt can bind
    // a still-current canonical reference to the same authenticated callback revision on restart.
    val resumed =
        payloads.mapNotNull { payload ->
            val expected = inventory.entry(payload.path) ?: return@mapNotNull null
            val canonical = receipts.read(payload.path, expected.sha256) ?: return@mapNotNull null
            val references = currentReferences.filter { repositories.references.storedReference(it) == canonical }.toSet()
            val synthetic = File("/data/data/$packageName/" + payload.path).toURI().toString()
            selectNativeLegacyLateMediaCandidates(listOf(payload), setOf(synthetic), packageName, inventory)
                .singleOrNull()
                ?.copy(references = references)
        }
    val media = repositories.media
    val engine =
        NativeLateLegacyMediaRepair(
            (selected + resumed).distinctBy { it.file.canonicalPath },
            requireCurrent = targets::requireCurrent,
            legacyReadable = { reference ->
                !UnavailableMediaReferences.isUnavailable(reference) && media.exists(reference)
            },
            available = { media.isRecoveredOriginal(it, StoredMediaReferences.Unchanged) },
            importOriginal = { candidate ->
                val name = candidate.logicalPath.substringAfterLast('/')
                nativeLegacyMediaMimeType(candidate.file, name)?.takeIf { candidate.file.length() > 0 }?.let { mime ->
                    importNativeLateMediaOriginal(media, RetainedManagedMediaSource.Asset(candidate.file, name, mime), isDeliveryCurrent)
                        .let(repositories.references::storedReference)
                }
            },
            remembered = { receipts.read(it.logicalPath, it.sha256) },
            remember = receipts::remember,
            canonicalReference = { repositories.references.storedReference(UnavailableMediaReferences.nativeSource(it) ?: it) },
        )
    val result = engine.repair(captured)
    targets.requireCurrent()
    if (acknowledge) custody.acknowledgeRecoveredMedia(result.verifiedFiles)
    return result.retry
}
