package app.logdate.client.device.restore

import android.app.backup.BackupAgent
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.uuid.Uuid

/** Captures one real callback delivery without replacing the already completed journal. */
internal class NativePrivacyRepairDelivery(
    private val context: Context,
    private val preferences: DataStore<Preferences>,
    private val inspectIsolatedCopy: (Map<String, File>) -> Unit,
    private val readPreferences: suspend (File) -> Preferences,
    private val protectedFiles: () -> Map<String, File>,
    private val protectedInventory: () -> Map<String, String>,
    private val accountSource: suspend (NativePrivacyRepairAcknowledgement) -> Unit = {},
) {
    private data class Incoming(
        val identity: NativePrivacyRepairIdentity,
        val cohort: String,
        val protected: Map<String, String>,
    )

    private var active: Incoming? = null
    private val custody = NativeJournalReplayCustody(context)
    private val detector = PostRestoreDetector(context)
    private val intent =
        NativePrivacyRepairIntent(context, preferences, { id, hash ->
            readNativePrivacyRepairCohortPreferences(custody, id, hash, protectedInventory(), readPreferences)
        }, protectedInventory, accountSource = accountSource)

    fun captureIfRequired(
        fileType: Int,
        path: () -> String,
        capture: (String, String) -> Unit,
    ): Boolean =
        bounded {
            if (active == null) {
                intent.resume()
                if (!isProtectedUnknownDelivery()) return@bounded false
                val identity = checkNotNull(detector.capturePrivacyRepairIdentity()) { "Native privacy delivery identity is unavailable" }
                active = Incoming(identity, Uuid.random().toString(), protectedInventory())
            }
            val incoming = checkNotNull(active)
            requireCurrent(incoming)
            if (fileType == BackupAgent.TYPE_DIRECTORY) return@bounded true
            check(fileType == BackupAgent.TYPE_FILE) { "Privacy repair delivery must contain regular files" }
            capture(incoming.cohort, path())
            true
        }

    /** Returning true may mean retained and still blocked; it never infers completion from historical cohorts. */
    fun finishIfRequired(): Boolean =
        bounded {
            val incoming = active
            if (incoming == null) {
                if (intent.resume()) return@bounded true
                return@bounded isProtectedUnknownDelivery()
            }
            try {
                requireCurrent(incoming)
                if (!custody.hasCohort(incoming.cohort)) return@bounded true
                val hash = custody.sealCohort(incoming.cohort)
                val files = verifiedNativePrivacyRepairCohort(custody, incoming.cohort, hash, incoming.protected)
                verifyNativePrivacyRepairFiles(
                    protectedFiles(),
                    files.filterKeys(::isNativePrivacyRepairProtectedPath),
                    inspectIsolatedCopy,
                )
                requireCurrent(incoming)
                check(intent.record(incoming.identity, incoming.cohort, hash, incoming.protected)) {
                    "Incoming preferences cannot repair this journal's unknown privacy"
                }
                true
            } finally {
                active = null
            }
        }

    private suspend fun isProtectedUnknownDelivery(): Boolean =
        detector.hasCompletedNativeRestoreDelivery() &&
            (detector.hasMissingNativePrivacyPayload() || !NativeRestorePrivacyPolicy.hasKnownPrivacyMetadata(preferences.data.first()))

    private fun requireCurrent(incoming: Incoming) {
        val current = checkNotNull(detector.capturePrivacyRepairIdentity())
        check(nativePrivacyRepairIdentityMayAdvance(incoming.identity, current)) { "Privacy source belongs to another native delivery" }
        check(protectedInventory() == incoming.protected) { "Protected journal changed while receiving its privacy metadata" }
    }

    private fun <T> bounded(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { withTimeout(15_000) { block() } }
}

internal fun verifiedNativePrivacyRepairCohort(
    custody: NativeJournalReplayCustody,
    id: String,
    hash: String,
    protected: Map<String, String> = emptyMap(),
): Map<String, File> {
    val payloads = custody.verifiedCohort(id, hash)
    check(payloads.isNotEmpty() && payloads.all { it.complete }) { "Privacy source delivery remains incomplete" }
    return payloads.groupBy { it.path }.mapValues { (path, revisions) ->
        if (revisions.size == 1) {
            revisions.single().file
        } else {
            val expected = protected[path]
            check(isNativePrivacyRepairProtectedPath(path) && expected != null) { "Privacy source has ambiguous path revisions" }
            checkNotNull(revisions.singleOrNull { retainedJournalChecksum(it.file) == expected }) {
                "No unique source revision matches the protected journal"
            }.file
        }
    }
}

internal suspend fun readNativePrivacyRepairCohortPreferences(
    custody: NativeJournalReplayCustody,
    id: String,
    hash: String,
    protected: Map<String, String> = emptyMap(),
    read: suspend (File) -> Preferences,
): Preferences {
    val files = verifiedNativePrivacyRepairCohort(custody, id, hash, protected)
    val snapshot = read(checkNotNull(files["files/logdate.preferences_pb"]) { "Incoming privacy preferences are missing" })
    verifiedNativePrivacyRepairCohort(custody, id, hash, protected)
    return snapshot
}
