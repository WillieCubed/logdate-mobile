package app.logdate.client.data.restore

import app.logdate.client.device.restore.NativeIncomingJournal
import java.io.File

/** Each verified complete revision stays separate; these inputs never authorize a DB/WAL cohort. */
internal fun retainedIndependentIncomingCandidates(verified: Iterable<NativeIncomingJournal.Payload>): List<Map<String, File>> =
    verified
        .filter { payload ->
            payload.complete && (payload.path in KEY_PATHS || isCanonicalMediaPath(payload.path))
        }.map { payload -> mapOf(payload.path to payload.file) }

private fun isCanonicalMediaPath(path: String): Boolean {
    if (!path.startsWith("files/media/")) return false
    return retainedCanonicalMediaDigest("logdate-media://library/" + path.removePrefix("files/media/")) != null
}

private val KEY_PATHS = setOf("files/db_recovery/passphrase_backup", "files/db_recovery/working_passphrase_backup")
