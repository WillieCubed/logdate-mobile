package app.logdate.client.data.restore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import app.logdate.client.database.NativeJournalSnapshotScope
import app.logdate.client.database.captureNativeJournalDatabaseSnapshot
import app.logdate.client.database.encryption.PassphraseBackupStore
import app.logdate.client.database.verifyNativeJournalDatabaseSnapshot
import app.logdate.client.datastore.createAndroidMainDataStore
import app.logdate.client.device.restore.NATIVE_MEDIA_INVENTORY_PATH
import app.logdate.client.device.restore.NATIVE_SOURCE_RECEIPT_PATH
import app.logdate.client.device.restore.NativeJournalTransferOwnership
import app.logdate.client.device.restore.retainedJournalPassphraseFile
import app.logdate.client.device.restore.retainedJournalWorkingKeyName
import app.logdate.client.device.restore.verifyNativeJournalSourceReceipt
import app.logdate.client.device.restore.writeNativeJournalMediaInventory
import app.logdate.client.device.restore.writeNativeJournalSourceReceipt
import app.logdate.client.device.storage.createAndroidSecureStorage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

/** Owns exactly one callback-created immutable subtree, never a restored or abandoned subtree. */
internal class NativeJournalTransferSnapshot(
    private val context: Context,
) {
    private val root = File(context.filesDir, "native_journal_transfer")
    private val current = File(root, "current")
    private val ownership = NativeJournalTransferOwnership(File(context.noBackupFilesDir, "native-journal-sender-owner"), current)
    private var prepared = false

    fun prepare() {
        if (!context.getDatabasePath("logdate").exists()) {
            check(!current.exists()) { "The retained native journal must remain available for recovery" }
            return
        }
        ownership.discardOwned()
        check(!current.exists()) { "Incoming native journal inputs must remain in custody" }
        check(root.mkdirs() || root.isDirectory)
        val staging = File(root, ".${Uuid.random()}.preparing")
        ownership.begin(staging)
        val target = File(staging, "database/logdate").apply { check(checkNotNull(parentFile).mkdirs()) }
        val keys = sourceKeyCandidates()
        try {
            val preferences = exportWithAcceptedKey(staging, target, keys)
            val mediaInventory = File(staging, NATIVE_MEDIA_INVENTORY_PATH)
            writeNativeJournalMediaInventory(mediaInventory, context.filesDir)
            val receipt = File(staging, NATIVE_SOURCE_RECEIPT_PATH)
            writeNativeJournalSourceReceipt(
                receipt,
                preferences,
                File(staging, "files/logdate.preferences_pb"),
                mapOf("database/logdate" to target),
                mediaInventory = mediaInventory,
            )
            staging.walkBottomUp().filter(File::isDirectory).forEach(::syncDirectory)
            ownership.record(staging)
            Files.move(staging.toPath(), current.toPath(), StandardCopyOption.ATOMIC_MOVE)
            syncDirectory(root)
            prepared = true
        } finally {
            keys.forEach { it.fill(0) }
            if (staging.exists()) ownership.discardOwned()
        }
    }

    fun finish() {
        if (!prepared) return
        ownership.discardOwned()
        prepared = false
    }

    private fun exportWithAcceptedKey(
        staging: File,
        target: File,
        keys: List<ByteArray>,
    ): Preferences {
        var failure: Exception? = null
        for (key in keys) {
            try {
                return captureNativeJournalDatabaseSnapshot(context.getDatabasePath("logdate"), key, target) { writeScope ->
                    val transactionThread = Thread.currentThread()
                    // SQL -> DataStore is the same order used by scoped journal/draft writes.
                    // The synchronous callback keeps the SQLite transaction on its owner thread.
                    runBlocking {
                        withTimeout(15_000) {
                            createAndroidMainDataStore(context).updateData { snapshot ->
                                check(Thread.currentThread() === transactionThread) { "Native SQL transaction changed thread" }
                                val scope =
                                    checkNotNull(
                                        RetainedJournalSourceScope.from(
                                            snapshot,
                                            app.logdate.shared.config.DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL,
                                        ),
                                    )
                                copyDurably(File(context.filesDir, "logdate.preferences_pb"), File(staging, "files/logdate.preferences_pb"))
                                writeScope(
                                    NativeJournalSnapshotScope(
                                        checkNotNull(scope.ownerId),
                                        scope.bound,
                                        scope.backendOrigin,
                                        nativeSnapshotChecksum(File(staging, "files/logdate.preferences_pb")),
                                    ),
                                )
                                val portable = File(staging, "files/db_recovery/${retainedJournalPassphraseFile(context).name}")
                                writeDurably(portable, Base64.encode(key).encodeToByteArray())
                                snapshot
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                failure = error
                listOf("", "-wal", "-shm", "-journal").forEach { File(target.path + it).delete() }
            }
        }
        throw IOException("The source journal could not produce a verified native snapshot", failure)
    }

    private fun sourceKeyCandidates(): List<ByteArray> {
        val primary =
            runBlocking {
                createAndroidSecureStorage(context).getString(retainedJournalWorkingKeyName(context))
            }?.let { runCatching { Base64.decode(it) }.getOrNull() }
        return (listOfNotNull(primary) + PassphraseBackupStore(context) { retainedJournalPassphraseFile(context) }.readCandidates())
            .filter { it.size == 32 }
            .distinctBy { Base64.encode(it) }
    }
}

internal fun validateNativeJournalTransfer(context: Context): Map<String, File>? {
    val current = File(context.filesDir, "native_journal_transfer/current")
    if (!current.exists()) return null
    val files = current.walkTopDown().filter(File::isFile).associateBy { it.relativeTo(current).invariantSeparatorsPath }
    val database = checkNotNull(files["database/logdate"])
    check(files.keys.none { it in setOf("database/logdate-wal", "database/logdate-shm", "database/logdate-journal") })
    val receipt = checkNotNull(files[NATIVE_SOURCE_RECEIPT_PATH])
    val preferences = checkNotNull(files["files/logdate.preferences_pb"])
    verifyNativeJournalSourceReceipt(receipt, preferences, mapOf("database/logdate" to database))
    val scratch = File(context.noBackupFilesDir, "native-journal-scope-validation").apply { check(mkdirs() || isDirectory) }
    val scope =
        runBlocking {
            checkNotNull(readRetainedJournalSourceScope(preferences, receipt, scratch, mapOf("database/logdate" to database)))
        }
    val key = listOf("passphrase_backup", "working_passphrase_backup").mapNotNull { files["files/db_recovery/$it"] }.single()
    val decoded = Base64.decode(key.readText().trim())
    try {
        check(
            verifyNativeJournalDatabaseSnapshot(database, decoded) ==
                NativeJournalSnapshotScope(
                    checkNotNull(scope.ownerId),
                    scope.bound,
                    scope.backendOrigin,
                    nativeSnapshotChecksum(preferences),
                ),
        ) { "Encrypted source scope does not match native preferences" }
    } finally {
        decoded.fill(0)
    }
    return files
}

private fun copyDurably(
    source: File,
    target: File,
) {
    check(Files.isRegularFile(source.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
    check(checkNotNull(target.parentFile).let { it.mkdirs() || it.isDirectory })
    source.inputStream().use { input ->
        FileOutputStream(target).use { output ->
            input.copyTo(output)
            output.fd.sync()
        }
    }
}

private fun writeDurably(
    target: File,
    bytes: ByteArray,
) {
    check(checkNotNull(target.parentFile).let { it.mkdirs() || it.isDirectory })
    FileOutputStream(target).use {
        it.write(bytes)
        it.fd.sync()
    }
}

private fun syncDirectory(directory: File) {
    FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) }
}

internal fun nativeSnapshotChecksum(file: File): String =
    file.inputStream().use { input ->
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
