package app.logdate.client.data.restore

import android.content.Context
import app.logdate.client.device.restore.NATIVE_SOURCE_RECEIPT_PATH
import app.logdate.client.device.restore.NativeJournalRestoreAuthority
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.client.device.restore.retainedJournalPassphraseFile
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/** Installation is allowed only by local vacancy intent captured before the first native file. */
internal class NativeJournalTransferRestore(
    private val context: Context,
    private val restorePreferences: (File) -> Unit,
    private val validateSnapshot: (Context) -> Map<String, File>? = ::validateNativeJournalTransfer,
) {
    private val current = File(context.filesDir, "native_journal_transfer/current")
    private val authority = NativeJournalRestoreAuthority(context)
    private val staging = NativeJournalTransferStaging(context)

    fun restore(): Boolean {
        if (staging.wasStaged()) return false
        if (current.exists() && authority.matchesCompletedSnapshot(snapshotFiles())) {
            authority.authorizeCompletedReplay(context.getDatabasePath("logdate"))
            return true
        }
        val files = validateSnapshot(context)
        if (files == null) {
            check(!authority.isSnapshotRequired() && authority.installedReceipt() == null) {
                "The required immutable native journal remains incomplete"
            }
            return false
        }
        val receiptHash = checksum(files.getValue(NATIVE_SOURCE_RECEIPT_PATH))
        if (receiptHash == authority.completedReceipt()) {
            authority.authorizeCompletedReplay(context.getDatabasePath("logdate"))
            return true
        }
        if (authority.requiresCustodyForIncoming()) {
            staging.stage(files)
            return false
        }
        authority.requireVacant()
        val key =
            files
                .filterKeys { it in setOf("files/db_recovery/passphrase_backup", "files/db_recovery/working_passphrase_backup") }
                .values
                .single()
        val sourceDatabase = files.getValue("database/logdate")
        val install = authority.prepareInstallation(receiptHash, context.getDatabasePath("logdate"), checksum(sourceDatabase))
        listOf("logdate-wal", "logdate-shm", "logdate-journal").forEach { name ->
            val file = context.getDatabasePath(name)
            if (file.exists()) check(file.delete())
        }
        syncDirectory(context.getDatabasePath("logdate").parentFile!!)
        if (install) installAtomically(sourceDatabase, context.getDatabasePath("logdate"))
        installAtomically(key, retainedJournalPassphraseFile(context))
        restorePreferences(files.getValue("files/logdate.preferences_pb"))
        authority.recordInstalledPortableState()
        return true
    }

    fun wasStaged(): Boolean = staging.wasStaged()

    fun finish() {
        check(PostRestoreDetector(context).hasCompletedNativeRestoreDelivery())
        if (staging.wasStaged()) {
            staging.finish()
            authority.finish()
            return
        }
        val receipt =
            authority.cleanupReceipt() ?: if (current.exists()) {
                checkNotNull(authority.installedReceipt()).also {
                    val files = snapshotFiles()
                    check(
                        (
                            authority.completedReceipt() == it &&
                                authority.matchesCompletedSnapshot(files)
                        ) ||
                            files[NATIVE_SOURCE_RECEIPT_PATH]?.let(::checksum) == it,
                    ) { "Uninstalled native journal inputs must remain in custody" }
                    authority.rememberCompletedSnapshot(it, files)
                    authority.beginCleanup(it)
                }
            } else {
                null
            }
        if (receipt != null) {
            check(receipt == authority.installedReceipt())
            val consumed = File(current.parentFile, ".$receipt.consumed")
            if (!consumed.exists() && current.exists()) {
                check(authority.matchesCompletedSnapshot(snapshotFiles()))
                Files.move(current.toPath(), consumed.toPath(), StandardCopyOption.ATOMIC_MOVE)
                syncDirectory(current.parentFile!!)
            }
            if (consumed.exists()) {
                check(Files.isDirectory(consumed.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
                check(consumed.deleteRecursively())
                syncDirectory(consumed.parentFile!!)
            }
        }
        authority.finish()
    }

    private fun snapshotFiles(): Map<String, File> =
        current.walkTopDown().filter(File::isFile).associateBy { it.relativeTo(current).invariantSeparatorsPath }
}

private fun installAtomically(
    source: File,
    target: File,
) {
    check(target.parentFile!!.mkdirs() || target.parentFile!!.isDirectory)
    val temporary = File.createTempFile(".native-journal-", ".part", target.parentFile)
    try {
        source.inputStream().use { input ->
            FileOutputStream(temporary).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        syncDirectory(target.parentFile!!)
    } finally {
        temporary.delete()
    }
}

private fun checksum(file: File): String =
    file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

private fun syncDirectory(directory: File) {
    FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) }
}
