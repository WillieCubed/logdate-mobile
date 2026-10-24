package app.logdate.client.device.restore

import android.content.Context
import io.github.aakira.napier.Napier
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Untransferred intent distinguishes a vacant destination from an already accepted local journal. */
class NativeJournalRestoreAuthority(
    private val context: Context,
) {
    @Serializable
    private data class Installation(
        val receipt: String,
        val before: Map<String, String>,
        val after: Map<String, String>,
        val portableAfter: Map<String, String>? = null,
    )

    @Serializable
    private data class Completed(
        val receipt: String,
        val files: Map<String, String>,
        val sourceReceipt: NativeJournalSourceReceipt? = null,
    )

    @Serializable
    private data class FrameworkDatabase(
        val files: Map<String, String>,
    )

    @Serializable
    private data class FrameworkPortable(
        val files: Map<String, String>,
    )

    private val vacancy = File(context.noBackupFilesDir, "native-journal-restore-vacancy")
    private val required = File(context.noBackupFilesDir, "native-journal-transfer-required")
    private val installed = File(context.noBackupFilesDir, "native-journal-transfer-installed")
    private val cleanup = File(context.noBackupFilesDir, "native-journal-transfer-cleanup")
    private val completed = File(context.noBackupFilesDir, "native-journal-transfer-completed")
    private val completedMedia = File(context.noBackupFilesDir, "native-journal-transfer-completed-media-inventory")
    private val frameworkDatabase = File(context.noBackupFilesDir, "native-journal-framework-database")
    private val frameworkPortable = File(context.noBackupFilesDir, "native-journal-framework-portable")

    fun begin(resumeInterrupted: Boolean = !PostRestoreDetector(context).isNativeRestoreDeliveryComplete()) {
        if (vacancy.exists() && resumeInterrupted) return
        val senderOwner = File(context.noBackupFilesDir, "native-journal-sender-owner")
        if (senderOwner.exists()) {
            check(senderOwner.delete())
            retainedJournalSyncDirectory(context.noBackupFilesDir)
        }
        val database = context.getDatabasePath("logdate")
        val occupiedDatabase =
            listOf("", "-wal", "-shm", "-journal").any {
                java.nio.file.Files
                    .exists(File(database.path + it).toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)
            }
        retainedJournalWriteAtomic(vacancy, if (occupiedDatabase || portableInventory().isNotEmpty()) "occupied" else "vacant")
        if (frameworkDatabase.exists()) check(frameworkDatabase.delete())
        if (frameworkPortable.exists()) check(frameworkPortable.delete())
        retainedJournalSyncDirectory(context.noBackupFilesDir)
    }

    fun requireSnapshot() = retainedJournalWriteAtomic(required, "required")

    fun isSnapshotRequired(): Boolean = required.exists()

    fun requireVacant() {
        check(retainedJournalReadBounded(vacancy, 16) == "vacant") { "Native transfer must not replace an accepted local database" }
    }

    private fun installation(): Installation? =
        installed.takeIf(File::exists)?.let { Json.decodeFromString<Installation>(retainedJournalReadBounded(it, 2048)) }

    fun installedReceipt(): String? = installation()?.receipt

    fun cleanupReceipt(): String? = cleanup.takeIf(File::exists)?.let { retainedJournalReadBounded(it, 128) }

    fun beginCleanup(receipt: String) {
        check(receipt == installedReceipt())
        retainedJournalWriteAtomic(cleanup, receipt)
    }

    private fun completedTransfer(): Completed? =
        completed.takeIf(File::exists)?.let { Json.decodeFromString<Completed>(retainedJournalReadBounded(it, 8192)) }

    fun completedReceipt(): String? = completedTransfer()?.receipt

    /** Verified scope captured with the immutable source; absent on older completed transfers. */
    fun completedSourceReceipt(): NativeJournalSourceReceipt? = completedTransfer()?.sourceReceipt

    /** v2 completion has no media authority; a v3 inventory must match its source receipt. */
    fun completedMediaInventory(): NativeJournalMediaInventory? {
        val expected = completedSourceReceipt()?.mediaInventorySha256 ?: return null
        if (!completedMedia.exists()) return null
        return verifyNativeJournalMediaInventory(completedMedia, expected)
    }

    /** Also accepts a manifest delivered after core journal completion. */
    fun retainCompletedMediaInventory(file: File): Boolean {
        val expected = completedSourceReceipt()?.mediaInventorySha256 ?: return false
        retainMediaInventory(file, expected)
        return true
    }

    /** A raw pre-31 callback may only extend the exact framework-owned DB inventory. */
    fun requireFrameworkDatabaseWrite(destination: File) {
        requireVacant()
        check(destination.name in DATABASE_NAMES)
        check(databaseInventory(context.getDatabasePath("logdate")) == frameworkDatabaseInventory()) {
            "The destination journal changed before the next framework database callback"
        }
    }

    fun recordFrameworkDatabaseWrite(destination: File) {
        check(destination.name in DATABASE_NAMES)
        val expected = frameworkDatabaseInventory() + (destination.name to retainedJournalChecksum(destination))
        check(databaseInventory(context.getDatabasePath("logdate")) == expected) {
            "The destination journal changed during a framework database callback"
        }
        retainedJournalWriteAtomic(frameworkDatabase, Json.encodeToString(FrameworkDatabase(expected)))
    }

    private fun frameworkDatabaseInventory(): Map<String, String> =
        frameworkDatabase.takeIf(File::exists)?.let {
            Json.decodeFromString<FrameworkDatabase>(retainedJournalReadBounded(it, 2048)).files
        } ?: emptyMap()

    /** The first occupied destination and later local edits are captured rather than overwritten. */
    fun requiresCustodyForIncoming(): Boolean {
        if (retainedJournalReadBounded(vacancy, 16) == "occupied") return true
        val database = databaseInventory(context.getDatabasePath("logdate"))
        val portable = portableInventory()
        val previous = installation()
        val frameworkMatches = database == frameworkDatabaseInventory() && portable == frameworkPortableInventory()
        val installedMatches = previous?.portableAfter != null && database == previous.after && portable == previous.portableAfter
        return !frameworkMatches && !installedMatches
    }

    /** A complete install can be retried only while both the database and portable state are unchanged. */
    fun recordInstalledPortableState() {
        val previous = checkNotNull(installation())
        check(databaseInventory(context.getDatabasePath("logdate")) == previous.after)
        retainedJournalWriteAtomic(installed, Json.encodeToString(previous.copy(portableAfter = portableInventory())))
    }

    fun recordFrameworkPortableState() {
        requireVacant()
        retainedJournalWriteAtomic(frameworkPortable, Json.encodeToString(FrameworkPortable(portableInventory())))
    }

    private fun frameworkPortableInventory(): Map<String, String> =
        frameworkPortable.takeIf(File::exists)?.let {
            Json.decodeFromString<FrameworkPortable>(retainedJournalReadBounded(it, 2048)).files
        } ?: emptyMap()

    private fun portableInventory(): Map<String, String> =
        PORTABLE_PATHS
            .mapNotNull { path ->
                val file = File(context.filesDir, path)
                if (!java.nio.file.Files
                        .exists(file.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)
                ) {
                    return@mapNotNull null
                }
                check(
                    java.nio.file.Files
                        .isRegularFile(file.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS),
                )
                path to retainedJournalChecksum(file)
            }.toMap()

    fun matchesCompletedSnapshot(files: Map<String, File>): Boolean {
        val prior = completedTransfer() ?: return false
        val receipt = files[NATIVE_SOURCE_RECEIPT_PATH] ?: return false
        if (retainedJournalChecksum(receipt) != prior.receipt) return false
        return files.all { (path, file) -> prior.files[path] == retainedJournalChecksum(file) }
    }

    fun rememberCompletedSnapshot(
        receipt: String,
        files: Map<String, File>,
    ) {
        check(receipt == installedReceipt())
        if (completedReceipt() == receipt && completedSourceReceipt() != null && matchesCompletedSnapshot(files)) {
            val inventory = files[NATIVE_MEDIA_INVENTORY_PATH]
            if (inventory != null && runCatching { completedMediaInventory() }.getOrNull() == null) {
                try {
                    retainCompletedMediaInventory(inventory)
                } catch (_: Exception) {
                    Napier.w("Native media inventory remains pending")
                }
            }
            return
        }
        val receiptFile = checkNotNull(files[NATIVE_SOURCE_RECEIPT_PATH])
        check(retainedJournalChecksum(receiptFile) == receipt)
        val sourceReceipt =
            verifyNativeJournalSourceReceipt(
                receiptFile,
                checkNotNull(files["files/logdate.preferences_pb"]),
                files.filterKeys { it in SOURCE_DATABASE_PATHS },
            )
        sourceReceipt.mediaInventorySha256?.let { expected ->
            files[NATIVE_MEDIA_INVENTORY_PATH]?.let { inventory ->
                try {
                    retainMediaInventory(inventory, expected)
                } catch (_: Exception) {
                    Napier.w("Native media inventory remains pending")
                }
            }
        }
        retainedJournalWriteAtomic(
            completed,
            Json.encodeToString(Completed(receipt, files.mapValues { retainedJournalChecksum(it.value) }, sourceReceipt)),
        )
    }

    private fun retainMediaInventory(
        source: File,
        expected: String,
    ) {
        verifyNativeJournalMediaInventory(source, expected)
        if (completedMedia.exists() && retainedJournalChecksum(completedMedia) == expected) return
        retainedJournalDurableDirectory(context.noBackupFilesDir)
        val temporary = File.createTempFile(".native-media-inventory-", ".part", context.noBackupFilesDir)
        try {
            source.inputStream().use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            verifyNativeJournalMediaInventory(temporary, expected)
            Files.move(temporary.toPath(), completedMedia.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            retainedJournalSyncDirectory(context.noBackupFilesDir)
        } finally {
            temporary.delete()
        }
    }

    fun matchesCompletedPayloads(payloads: List<NativeIncomingJournal.Payload>): Boolean {
        if (payloads.any { !it.complete } || payloads.map { it.path }.distinct().size != payloads.size) return false
        if (payloads.any { !it.path.startsWith(NATIVE_JOURNAL_TRANSFER_PREFIX) }) return false
        if (payloads.none { it.path == NATIVE_JOURNAL_TRANSFER_PREFIX + NATIVE_SOURCE_RECEIPT_PATH }) return false
        return matchesCompletedSnapshot(payloads.associate { nativeJournalLogicalPath(it.path) to it.file })
    }

    fun authorizeCompletedReplay(database: File) {
        val receipt = checkNotNull(completedReceipt())
        check(database.isFile) { "A completed transfer cannot replace a missing local journal" }
        val inventory = databaseInventory(database)
        retainedJournalWriteAtomic(installed, Json.encodeToString(Installation(receipt, inventory, inventory)))
    }

    /** A crash can resume only the exact original framework bytes or the exact installed snapshot. */
    fun prepareInstallation(
        receiptSha256: String,
        database: File,
        expectedDatabaseSha256: String,
    ): Boolean {
        val current = databaseInventory(database)
        val expected = mapOf(database.name to expectedDatabaseSha256)
        val previous = installation()
        if (previous != null) {
            check(previous.receipt == receiptSha256 && previous.after == expected)
            val originalRemains =
                current[database.name] == previous.before[database.name] &&
                    current.all { (path, hash) -> previous.before[path] == hash }
            check(originalRemains || current == previous.after) { "The destination journal changed after native installation began" }
        } else {
            requireVacant()
            check(current == frameworkDatabaseInventory()) {
                "The destination journal was not delivered by Android restore callbacks"
            }
            retainedJournalWriteAtomic(installed, Json.encodeToString(Installation(receiptSha256, current, expected)))
        }
        return current != expected
    }

    private fun databaseInventory(database: File): Map<String, String> =
        listOf("", "-wal", "-shm", "-journal").map { File(database.path + it) }.filter(File::exists).associate {
            check(
                java.nio.file.Files
                    .isRegularFile(it.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS),
            )
            it.name to retainedJournalChecksum(it)
        }

    fun finish() {
        listOf(vacancy, required, installed, cleanup, frameworkDatabase, frameworkPortable).forEach {
            if (it.exists()) check(it.delete())
        }
        retainedJournalSyncDirectory(context.noBackupFilesDir)
    }

    private companion object {
        val DATABASE_NAMES = setOf("logdate", "logdate-wal", "logdate-shm", "logdate-journal")
        val SOURCE_DATABASE_PATHS = DATABASE_NAMES.mapTo(mutableSetOf()) { "database/$it" }
        val PORTABLE_PATHS =
            setOf(
                "logdate.preferences_pb",
                "db_recovery/passphrase_backup",
                "db_recovery/working_passphrase_backup",
                "identity_recovery/phrase_backup",
                "identity_recovery/account_key_backup",
                "identity_recovery/media_key_backup",
            )
    }
}
