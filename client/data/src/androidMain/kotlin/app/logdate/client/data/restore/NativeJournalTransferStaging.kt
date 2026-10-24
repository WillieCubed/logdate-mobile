package app.logdate.client.data.restore

import android.content.Context
import app.logdate.client.device.restore.NATIVE_JOURNAL_TRANSFER_PREFIX
import app.logdate.client.device.restore.NATIVE_SOURCE_RECEIPT_PATH
import app.logdate.client.device.restore.NativeJournalReplayCustody
import app.logdate.client.device.restore.verifiedNativeJournalSources
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.uuid.Uuid

/** Rejected installation becomes durable incoming custody without modifying the accepted local journal. */
internal class NativeJournalTransferStaging(
    private val context: Context,
) {
    @Serializable
    private data class Intent(
        val receipt: String,
        val files: Map<String, String>,
        val cohorts: Map<String, String>,
        val replacedCohorts: Map<String, List<String>> = emptyMap(),
    )

    private val current = File(context.filesDir, "native_journal_transfer/current")
    private val marker = File(context.noBackupFilesDir, "native-journal-transfer-staged")
    private val custody = NativeJournalReplayCustody(context)
    private val stagedSources = NativeJournalStagedSourceIndex(context)

    fun stage(files: Map<String, File>) {
        val receipt = nativeSnapshotChecksum(files.getValue(NATIVE_SOURCE_RECEIPT_PATH))
        val hashes = files.mapValues { nativeSnapshotChecksum(it.value) }
        val intent =
            if (marker.exists()) {
                readIntent().also { check(it.receipt == receipt && it.files == hashes) }
            } else {
                Intent(receipt, hashes, hashes.mapValues { Uuid.random().toString() }).also(::writeIntent)
            }
        resume(intent)
    }

    fun wasStaged(): Boolean {
        if (marker.exists()) {
            resume(readIntent())
            return true
        }
        if (!custody.hasInputs()) return false
        // Every revision is sealed and verified; source admission remains the reconciliation worker's job.
        custody.sealedPayloads()
        return true
    }

    fun finish() {
        if (marker.exists()) {
            resume(readIntent())
            check(marker.delete())
            sync(marker.parentFile!!)
        }
    }

    private fun resume(intent: Intent) {
        val consumed = File(current.parentFile, ".${intent.receipt}.staged")
        val original = if (current.exists()) current else consumed
        var activeIntent = retireInterrupted(intent, original)
        for ((path, hash) in intent.files) {
            activeIntent = captureMissing(activeIntent, original, path, hash)
        }
        verifyAndRemember(intent, activeIntent, original)
        retireReplaced(intent, activeIntent)
        consumeOriginal(intent, consumed)
    }

    private fun retireInterrupted(
        intent: Intent,
        original: File,
    ): Intent {
        if (original.exists() && intent.replacedCohorts.isNotEmpty()) {
            intent.replacedCohorts.forEach { (path, oldIds) ->
                val source = File(original, path)
                oldIds.forEach { old ->
                    custody.retireInterruptedStagedCohort(old, path, source, intent.files.getValue(path))
                }
            }
            return intent.copy(replacedCohorts = emptyMap()).also(::writeIntent)
        }
        return intent
    }

    private fun captureMissing(
        intent: Intent,
        original: File,
        path: String,
        hash: String,
    ): Intent {
        var activeIntent = intent
        var cohort = activeIntent.cohorts.getValue(path)
        val pending = custody.isPendingCohort(cohort)
        val existing = if (pending) emptyList() else custody.sealedPayloadsInCohort(cohort)
        if (existing.any {
                it.complete &&
                    it.path == NATIVE_JOURNAL_TRANSFER_PREFIX + path &&
                    nativeSnapshotChecksum(it.file) == hash
            }
        ) {
            return activeIntent
        }
        if (pending || custody.hasCohort(cohort)) {
            val replacement = Uuid.random().toString()
            activeIntent =
                activeIntent.copy(
                    cohorts = activeIntent.cohorts + (path to replacement),
                    replacedCohorts =
                        activeIntent.replacedCohorts +
                            (path to (activeIntent.replacedCohorts[path].orEmpty() + cohort)),
                )
            writeIntent(activeIntent)
            if (original.exists()) {
                custody.retireInterruptedStagedCohort(cohort, path, File(original, path), hash)
                activeIntent = activeIntent.copy(replacedCohorts = activeIntent.replacedCohorts - path)
                writeIntent(activeIntent)
            }
            cohort = replacement
        }
        val source = File(original, path)
        check(Files.isRegularFile(source.toPath(), LinkOption.NOFOLLOW_LINKS) && nativeSnapshotChecksum(source) == hash)
        source.inputStream().use {
            custody.captureInCohort(cohort, NATIVE_JOURNAL_TRANSFER_PREFIX + path, source.length(), it)
        }
        return activeIntent
    }

    private fun verifyAndRemember(
        intent: Intent,
        activeIntent: Intent,
        original: File,
    ) {
        val payloads = custody.sealedPayloads()
        check(
            intent.files.all { (path, hash) ->
                custody.sealedPayloadsInCohort(activeIntent.cohorts.getValue(path)).any {
                    it.complete && it.path == NATIVE_JOURNAL_TRANSFER_PREFIX + path && nativeSnapshotChecksum(it.file) == hash
                }
            },
        )
        check(
            verifiedNativeJournalSources(
                payloads,
            ).values.any { nativeSnapshotChecksum(it.getValue("source_scope.json")) == intent.receipt },
        )
        if (original.exists()) {
            stagedSources.remember(intent.files.keys.associateWith { File(original, it) }, activeIntent.cohorts)
        } else {
            check(stagedSources.hasRemembered(intent.receipt))
        }
    }

    private fun retireReplaced(
        intent: Intent,
        activeIntent: Intent,
    ) {
        activeIntent.replacedCohorts.forEach { (path, oldIds) ->
            val replacement = activeIntent.cohorts.getValue(path)
            val verified =
                custody.sealedPayloadsInCohort(replacement).single {
                    it.complete &&
                        it.path == NATIVE_JOURNAL_TRANSFER_PREFIX + path &&
                        nativeSnapshotChecksum(it.file) == intent.files.getValue(path)
                }
            oldIds.forEach { old ->
                custody.retireReplacedStagedCohort(old, replacement, path, verified.expectedSize, intent.files.getValue(path))
            }
        }
        if (activeIntent.replacedCohorts.isNotEmpty()) {
            writeIntent(activeIntent.copy(replacedCohorts = emptyMap()))
        }
    }

    private fun consumeOriginal(
        intent: Intent,
        consumed: File,
    ) {
        if (current.exists()) {
            val files =
                current.walkTopDown().filter(File::isFile).associate {
                    it.relativeTo(current).invariantSeparatorsPath to
                        nativeSnapshotChecksum(it)
                }
            check(files == intent.files) { "Incoming native source changed while staging" }
            check(!consumed.exists())
            Files.move(current.toPath(), consumed.toPath(), StandardCopyOption.ATOMIC_MOVE)
            sync(current.parentFile!!)
        }
        if (consumed.exists()) {
            check(Files.isDirectory(consumed.toPath(), LinkOption.NOFOLLOW_LINKS))
            check(consumed.deleteRecursively())
            sync(consumed.parentFile!!)
        }
    }

    private fun readIntent(): Intent {
        check(marker.length() in 1..16_384)
        return Json.decodeFromString<Intent>(marker.readText()).also {
            check(it.receipt.matches(Regex("[a-f0-9]{64}")))
            check(it.files.keys == it.cohorts.keys && it.cohorts.values.all { id -> Uuid.parse(id).toString() == id })
            check(it.replacedCohorts.keys.all(it.files::containsKey))
            check(
                it.replacedCohorts.values
                    .flatten()
                    .all { id -> Uuid.parse(id).toString() == id },
            )
        }
    }

    private fun writeIntent(intent: Intent) {
        check(context.noBackupFilesDir.mkdirs() || context.noBackupFilesDir.isDirectory)
        val temporary = File.createTempFile(".native-journal-stage-", ".part", context.noBackupFilesDir)
        try {
            FileOutputStream(temporary).use {
                it.write(Json.encodeToString(intent).encodeToByteArray())
                it.fd.sync()
            }
            Files.move(temporary.toPath(), marker.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            sync(context.noBackupFilesDir)
        } finally {
            temporary.delete()
        }
    }

    private fun sync(directory: File) = FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) }
}
