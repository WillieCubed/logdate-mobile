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
                val cohort = Uuid.random().toString()
                Intent(receipt, hashes, hashes.mapValues { cohort }).also(::writeIntent)
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

    fun hasPendingStageMarker(): Boolean = marker.exists()

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
        var activeIntent = intent
        for ((cohort, paths) in intent.cohorts.entries.groupBy({ it.value }, { it.key })) {
            val expected = paths.associateWith { intent.files.getValue(it) }
            if (isCohortComplete(cohort, expected)) continue
            val oldIds = if (custody.hasCohort(cohort)) listOf(cohort) else emptyList()
            val replacement = if (oldIds.isEmpty()) cohort else Uuid.random().toString()
            if (replacement != cohort) {
                activeIntent = activeIntent.withReplacementCohort(paths, replacement, oldIds)
                writeIntent(activeIntent)
                retireInterruptedGroup(cohort, paths.associateWith { File(original, it) to intent.files.getValue(it) })
            }
            captureCohort(replacement, expected, original)
            check(hasCompletePayloads(replacement, expected))
        }
        verifyAndRemember(intent, activeIntent, original)
        retireReplacedGroups(intent, activeIntent)
        consumeOriginal(intent, consumed)
    }

    private fun isCohortComplete(
        cohort: String,
        expected: Map<String, String>,
    ): Boolean {
        val existing = custody.sealedPayloadsInCohort(cohort)
        return existing.size == expected.size &&
            expected.all { (path, hash) ->
                existing.singleOrNull {
                    it.complete &&
                        it.path == NATIVE_JOURNAL_TRANSFER_PREFIX + path &&
                        nativeSnapshotChecksum(it.file) == hash
                } !=
                    null
            }
    }

    private fun Intent.withReplacementCohort(
        paths: List<String>,
        replacement: String,
        oldIds: List<String>,
    ): Intent =
        copy(
            cohorts = cohorts + paths.associateWith { replacement },
            replacedCohorts =
                replacedCohorts +
                    paths.associateWith { path ->
                        replacedCohorts[path].orEmpty() + oldIds
                    },
        )

    private fun captureCohort(
        cohort: String,
        expected: Map<String, String>,
        original: File,
    ) {
        for ((path, hash) in expected) {
            val source = File(original, path)
            check(Files.isRegularFile(source.toPath(), LinkOption.NOFOLLOW_LINKS) && nativeSnapshotChecksum(source) == hash)
            source.inputStream().use {
                custody.captureInCohort(
                    cohort,
                    NATIVE_JOURNAL_TRANSFER_PREFIX + path,
                    source.length(),
                    it,
                )
            }
        }
    }

    private fun hasCompletePayloads(
        cohort: String,
        expected: Map<String, String>,
    ): Boolean {
        val staged = custody.sealedPayloadsInCohort(cohort)
        return expected.all { (path, hash) ->
            staged.any {
                it.complete &&
                    it.path == NATIVE_JOURNAL_TRANSFER_PREFIX + path &&
                    nativeSnapshotChecksum(it.file) == hash
            }
        }
    }

    private fun retireInterruptedGroup(
        oldId: String,
        sources: Map<String, Pair<File, String>>,
    ) {
        if (custody.hasCohort(oldId)) custody.retireInterruptedStagedCohortGroup(oldId, sources)
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

    private fun retireReplacedGroups(
        intent: Intent,
        activeIntent: Intent,
    ) {
        activeIntent.replacedCohorts.entries.groupBy({ it.value }, { it.key }).forEach { (oldIds, paths) ->
            val replacement = activeIntent.cohorts.getValue(paths.first())
            val payloads = custody.sealedPayloadsInCohort(replacement)
            check(
                paths.all { path ->
                    payloads.any {
                        it.complete &&
                            it.path == NATIVE_JOURNAL_TRANSFER_PREFIX + path &&
                            nativeSnapshotChecksum(it.file) == intent.files.getValue(path)
                    }
                },
            )
            oldIds.distinct().forEach { old ->
                retireInterruptedGroup(
                    old,
                    paths.associateWith { path -> File(originalForRetirement(intent), path) to intent.files.getValue(path) },
                )
            }
        }
        if (activeIntent.replacedCohorts.isNotEmpty()) {
            writeIntent(activeIntent.copy(replacedCohorts = emptyMap()))
        }
    }

    private fun originalForRetirement(intent: Intent): File =
        if (current.exists()) current else File(current.parentFile, ".${intent.receipt}.staged")

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
