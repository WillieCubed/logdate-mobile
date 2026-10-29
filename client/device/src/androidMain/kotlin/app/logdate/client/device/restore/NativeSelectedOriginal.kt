package app.logdate.client.device.restore

import android.app.backup.BackupAgent

/** These originals are selected explicitly by the sender, outside Android's XML traversal roots. */
internal fun isNativeSelectedOriginal(path: String): Boolean =
    listOf("files/media/", "files/user_media/", "files/audio_notes/").any(path::startsWith)

internal fun retainSelectedNativeOriginal(
    fileType: Int,
    path: () -> String,
    retain: (String) -> Unit,
): Boolean {
    if (fileType == BackupAgent.TYPE_DIRECTORY) return false
    val incoming = path()
    if (!isNativeSelectedOriginal(incoming)) return false
    check(fileType == BackupAgent.TYPE_FILE) { "Native media delivery must contain regular files" }
    retain(incoming)
    return true
}
