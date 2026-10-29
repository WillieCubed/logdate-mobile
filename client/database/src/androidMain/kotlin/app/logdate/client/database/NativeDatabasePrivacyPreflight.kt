package app.logdate.client.database

import android.content.Context
import app.logdate.client.device.restore.NativeRestorePrivacyGuard
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/** Unknown restored privacy must be checked before migrations or writable Room callbacks. */
internal suspend fun verifyNativePrivacyBeforeDatabaseOpen(
    context: Context,
    passphrases: List<ByteArray>,
    guard: NativeRestorePrivacyGuard,
    inspectCopy: (Context, Map<String, File>, List<ByteArray>, File) -> RetainedDatabaseCopy? = ::openRetainedDatabaseCopy,
) {
    guard.verifyBeforeContent {
        val sources =
            listOf(DATABASE_NAME, "$DATABASE_NAME-wal", "$DATABASE_NAME-shm", "$DATABASE_NAME-journal")
                .map { name -> name to context.getDatabasePath(name) }
                .filter { (_, file) -> Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS) }
                .toMap()
        if (sources.isEmpty()) return@verifyBeforeContent false
        check(DATABASE_NAME in sources) { "Retained database sidecars cannot establish an empty installation" }
        check(sources.values.all { Files.isRegularFile(it.toPath(), LinkOption.NOFOLLOW_LINKS) }) {
            "Retained database inspection requires regular files"
        }
        if (sources.values.all { it.length() == 0L }) return@verifyBeforeContent false
        currentCoroutineContext().ensureActive()
        val isolated =
            checkNotNull(inspectCopy(context, sources, passphrases, File(context.noBackupFilesDir, "native-privacy-inspection"))) {
                "Retained journal could not be inspected without modifying it"
            }
        isolated.use {
            currentCoroutineContext().ensureActive()
            val retained = it.database.integrityDao().hasRetainedJournalContent()
            currentCoroutineContext().ensureActive()
            retained
        }
    }
}
