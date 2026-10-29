package app.logdate.client.device.restore

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlin.uuid.Uuid

/** Local-only evidence that every input was represented when an actual repair was acknowledged. */
@Serializable
internal data class NativePrivacyRepairAccounting(
    val version: Int = 1,
    val generation: String,
    val accountDeliveryId: String,
    val cohortId: String,
    val manifestSha256: String,
    val protectedInventorySha256: String,
    val acceptedPreferencesSha256: String,
)

/** Caller has completed policy acknowledgement and whole-cohort coverage; payload bytes stay intact. */
internal fun writeNativePrivacyRepairAccounting(
    directory: File,
    receipt: NativePrivacyRepairAccounting,
) {
    requireAccountingBinding(directory, receipt)
    val marker = File(directory, ACCOUNTING_MARKER)
    if (Files.exists(marker.toPath(), LinkOption.NOFOLLOW_LINKS)) {
        check(isAccountedNativePrivacyRepairCohort(directory))
        check(Json.decodeFromString<NativePrivacyRepairAccounting>(retainedJournalReadBounded(marker, MAX_ACCOUNTING_BYTES)) == receipt)
        return
    }
    retainedJournalWriteAtomic(marker, Json.encodeToString(receipt))
}

/** This untransferred receipt is routing authority, not permission to delete or select source bytes. */
internal fun isAccountedNativePrivacyRepairCohort(directory: File): Boolean {
    check(!Files.isSymbolicLink(directory.toPath())) { "Native source accounting directory must not be a symlink" }
    val marker = File(directory, ACCOUNTING_MARKER)
    if (!Files.exists(marker.toPath(), LinkOption.NOFOLLOW_LINKS)) return false
    check(Files.isDirectory(directory.toPath(), LinkOption.NOFOLLOW_LINKS))
    val receipt = Json.decodeFromString<NativePrivacyRepairAccounting>(retainedJournalReadBounded(marker, MAX_ACCOUNTING_BYTES))
    requireAccountingBinding(directory, receipt)
    return true
}

private fun requireAccountingBinding(
    directory: File,
    receipt: NativePrivacyRepairAccounting,
) {
    check(Files.isDirectory(directory.toPath(), LinkOption.NOFOLLOW_LINKS))
    check(receipt.version == 1 && receipt.cohortId == directory.name)
    check(listOf(receipt.generation, receipt.accountDeliveryId, receipt.cohortId).all { Uuid.parse(it).toString() == it })
    check(
        listOf(receipt.manifestSha256, receipt.protectedInventorySha256, receipt.acceptedPreferencesSha256).all {
            ACCOUNTING_SHA.matches(it)
        },
    )
    check(!Files.exists(File(directory, "pending").toPath(), LinkOption.NOFOLLOW_LINKS))
    val manifest = File(directory, "manifest.json")
    check(Files.isRegularFile(manifest.toPath(), LinkOption.NOFOLLOW_LINKS))
    check(retainedJournalChecksum(manifest) == receipt.manifestSha256) { "Accounted native source manifest changed" }
}

internal data class NativePrivacyRepairAcknowledgement(
    val identity: NativePrivacyRepairIdentity,
    val cohortId: String,
    val manifestSha256: String,
    val protectedInventory: Map<String, String>,
    val acceptedPreferencesSha256: String,
)

private const val ACCOUNTING_MARKER = "privacy-accounted.json"
private const val MAX_ACCOUNTING_BYTES = 2048
private val ACCOUNTING_SHA = Regex("[a-f0-9]{64}")
