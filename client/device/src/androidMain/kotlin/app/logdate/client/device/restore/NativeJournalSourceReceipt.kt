package app.logdate.client.device.restore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.shared.config.DefaultLogDateConfigRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlin.uuid.Uuid

/** Source-owned proof for one immutable SQLCipher export and its exact preference snapshot. */
@Serializable
data class NativeJournalSourceReceipt(
    val version: Int,
    val readState: String,
    val canonicalOwnerId: String,
    val canonicalOwnerBound: String,
    val backendOrigin: String,
    val originProvenance: String,
    val preferencesSha256: String,
    val databaseSha256: Map<String, String>,
    val mediaInventorySha256: String? = null,
)

/** Caller owns the database and DataStore capture fences until these immutable bytes commit. */
fun writeNativeJournalSourceReceipt(
    destination: File,
    preferences: Preferences,
    preferencesFile: File,
    databaseFiles: Map<String, File>,
    mediaInventory: File? = null,
) {
    require(
        databaseFiles.keys.isNotEmpty() && "database/logdate" in databaseFiles && databaseFiles.keys.all { it in NATIVE_DATABASE_PATHS },
    )
    check(preferences[stringPreferencesKey("identity.pending_history_owner_adoption.v1")] == null) {
        "Native source owner adoption must finish before capture"
    }
    val storedOrigin = preferences[stringPreferencesKey("backend_url")]
    val mediaInventorySha256 =
        mediaInventory?.let { file ->
            val digest = nativeSourceChecksum(file)
            verifyNativeJournalMediaInventory(file, digest)
            digest
        }
    val receipt =
        NativeJournalSourceReceipt(
            version = if (mediaInventorySha256 == null) 2 else 3,
            readState = "captured",
            canonicalOwnerId = checkNotNull(preferences[stringPreferencesKey("identity.canonical_owner_id")]),
            canonicalOwnerBound = preferences[stringPreferencesKey("identity.canonical_owner_bound")] ?: "false",
            backendOrigin = storedOrigin ?: DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL,
            originProvenance = if (storedOrigin == null) "default-at-capture" else "stored-backend-url",
            preferencesSha256 = nativeSourceChecksum(preferencesFile),
            databaseSha256 = databaseFiles.mapValues { nativeSourceChecksum(it.value) },
            mediaInventorySha256 = mediaInventorySha256,
        )
    validateNativeSourceReceiptMetadata(receipt)
    retainedJournalWriteAtomic(destination, Json.encodeToString(receipt))
}

/** Hashes bind the receipt to source bytes; scope values are subsequently checked against the decoded preferences. */
fun verifyNativeJournalSourceReceipt(
    receiptFile: File,
    preferencesFile: File,
    databaseFiles: Map<String, File>,
): NativeJournalSourceReceipt {
    val receipt = readNativeJournalSourceReceipt(receiptFile)
    check(databaseFiles.keys == receipt.databaseSha256.keys) { "Native source database inventory changed" }
    check(nativeSourceChecksum(preferencesFile) == receipt.preferencesSha256) { "Native source preferences changed" }
    databaseFiles.forEach { (path, file) ->
        check(nativeSourceChecksum(file) == receipt.databaseSha256.getValue(path)) { "Native source database bytes changed" }
    }
    return receipt
}

/** Complete revisions may cross callback generations only when the sender's exact hashes select them. */
fun verifiedNativeJournalSources(payloads: Iterable<NativeIncomingJournal.Payload>): Map<String, Map<String, File>> {
    val complete = payloads.filter(NativeIncomingJournal.Payload::complete)
    val receipts = complete.filter { it.path == NATIVE_JOURNAL_TRANSFER_PREFIX + NATIVE_SOURCE_RECEIPT_PATH }
    return buildMap {
        for (payload in receipts) {
            val source = runCatching { nativeSourceFromReceipt(payload, complete) }.getOrNull() ?: continue
            put(source.first, source.second)
        }
    }
}

/** An inventory is usable only when its exact bytes belong to a verified v3 source receipt. */
fun verifiedNativeJournalMediaInventories(payloads: Iterable<NativeIncomingJournal.Payload>): Map<String, NativeJournalMediaInventory> =
    verifiedNativeJournalSources(payloads)
        .values
        .mapNotNull { files ->
            val receiptFile = files["source_scope.json"] ?: return@mapNotNull null
            val inventoryFile = files[NATIVE_MEDIA_INVENTORY_PATH] ?: return@mapNotNull null
            val expected = readNativeJournalSourceReceipt(receiptFile).mediaInventorySha256 ?: return@mapNotNull null
            val inventory = verifyNativeJournalMediaInventory(inventoryFile, expected)
            nativeSourceChecksum(receiptFile) to inventory
        }.toMap()

/** Only the explicitly declared immutable native subtree can normalize another path's domain. */
fun nativeJournalLogicalPath(path: String): String = path.removePrefix(NATIVE_JOURNAL_TRANSFER_PREFIX)

private fun nativeSourceFromReceipt(
    payload: NativeIncomingJournal.Payload,
    complete: List<NativeIncomingJournal.Payload>,
): Pair<String, Map<String, File>>? {
    val receipt = readNativeJournalSourceReceipt(payload.file)
    val prefix = payload.path.removeSuffix(NATIVE_SOURCE_RECEIPT_PATH)
    check(prefix == NATIVE_JOURNAL_TRANSFER_PREFIX)

    fun selected(
        path: String,
        hash: String,
    ): File? = complete.firstOrNull { it.path == prefix + path && nativeSourceChecksum(it.file) == hash }?.file
    val preferences = selected("files/logdate.preferences_pb", receipt.preferencesSha256) ?: return null
    val databases =
        receipt.databaseSha256.mapValues { (path, hash) -> selected(path, hash) ?: return null }
    verifyNativeJournalSourceReceipt(payload.file, preferences, databases)
    val digest = nativeSourceChecksum(payload.file)
    val id = "${digest.take(
        8,
    )}-${digest.substring(8, 12)}-${digest.substring(12, 16)}-${digest.substring(16, 20)}-${digest.substring(20, 32)}"
    val inventory =
        receipt.mediaInventorySha256?.let { expected ->
            selected(NATIVE_MEDIA_INVENTORY_PATH, expected)?.also { verifyNativeJournalMediaInventory(it, expected) }
        }
    val accepted = databases + ("files/logdate.preferences_pb" to preferences) + ("source_scope.json" to payload.file)
    return id to if (inventory == null) accepted else accepted + (NATIVE_MEDIA_INVENTORY_PATH to inventory)
}

private fun readNativeJournalSourceReceipt(file: File): NativeJournalSourceReceipt {
    val encoded = retainedJournalReadBounded(file, 8192)
    return Json.decodeFromString<NativeJournalSourceReceipt>(encoded).also {
        check(Json.encodeToString(it) == encoded) { "Native source receipt must have one explicit canonical interpretation" }
        validateNativeSourceReceiptMetadata(it)
    }
}

private fun validateNativeSourceReceiptMetadata(receipt: NativeJournalSourceReceipt) {
    check(receipt.version in 2..3 && receipt.readState == "captured")
    check((receipt.version == 2) == (receipt.mediaInventorySha256 == null))
    receipt.mediaInventorySha256?.let { check(it.matches(NATIVE_SOURCE_DIGEST)) }
    Uuid.parse(receipt.canonicalOwnerId)
    check(receipt.canonicalOwnerBound in setOf("true", "false"))
    check(receipt.originProvenance in setOf("stored-backend-url", "default-at-capture"))
    val origin = URI(receipt.backendOrigin)
    check(origin.scheme?.lowercase() in setOf("http", "https") && !origin.host.isNullOrBlank() && origin.port in -1..65535)
    check(origin.rawUserInfo == null && origin.rawQuery == null && origin.rawFragment == null && origin.path in listOf(null, "", "/"))
    check(receipt.preferencesSha256.matches(NATIVE_SOURCE_DIGEST))
    check("database/logdate" in receipt.databaseSha256 && receipt.databaseSha256.keys.all { it in NATIVE_DATABASE_PATHS })
    check(receipt.databaseSha256.values.all { it.matches(NATIVE_SOURCE_DIGEST) })
}

private fun nativeSourceChecksum(file: File): String {
    check(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) && file.length() > 0)
    return retainedJournalChecksum(file)
}

const val NATIVE_SOURCE_RECEIPT_PATH = "files/db_recovery/native_source_scope.json"
const val NATIVE_JOURNAL_TRANSFER_PREFIX = "files/native_journal_transfer/current/"
private val NATIVE_SOURCE_DIGEST = Regex("[a-f0-9]{64}")
private val NATIVE_DATABASE_PATHS = setOf("database/logdate", "database/logdate-wal", "database/logdate-shm", "database/logdate-journal")
