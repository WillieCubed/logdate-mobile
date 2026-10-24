package app.logdate.client.data.restore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.logdate.client.data.notes.drafts.AndroidLocalEntryDraftStore
import app.logdate.client.device.restore.NATIVE_MEDIA_INVENTORY_PATH
import app.logdate.client.device.restore.NativeJournalMediaInventory
import app.logdate.client.device.restore.verifyNativeJournalMediaInventory
import app.logdate.client.media.MediaManager
import app.logdate.client.media.storage.StoredMediaReferences
import app.logdate.client.media.storage.isRecoveredOriginal
import app.logdate.client.repository.journals.EntryDraftRecoveryRepository
import app.logdate.client.repository.journals.PendingMediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption

/** Imports only pending camera drafts whose DataStore and original bytes were selected by one receipt. */
internal class NativePendingCameraDraftImporter(
    private val filesDir: File,
    private val packageName: String,
    private val media: MediaManager,
    private val drafts: EntryDraftRecoveryRepository,
    private val canCommit: () -> Boolean,
    private val requireCurrent: suspend () -> Unit,
    private val scopeFence: suspend (commit: suspend () -> Boolean) -> Boolean,
) {
    suspend fun import(
        generation: String,
        sourceFiles: Map<String, File>,
        expectedInventorySha256: String?,
    ): Boolean {
        val draftFile = sourceFiles[ENTRY_DRAFTS_PATH] ?: return true
        val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { draftFile })
            val sourceDrafts = AndroidLocalEntryDraftStore(store).getAllDrafts()
            val cameraDrafts = sourceDrafts.filter(::hasPendingCameraOriginal)
            if (cameraDrafts.isEmpty()) return true
            val inventoryHash = expectedInventorySha256 ?: return false
            val inventoryFile = sourceFiles[NATIVE_MEDIA_INVENTORY_PATH] ?: return false
            val inventory = verifyNativeJournalMediaInventory(inventoryFile, inventoryHash)
            for (draft in cameraDrafts) {
                val recoveredReferences = mutableMapOf<String, String>()
                val cameraRecords =
                    draft.pendingMedia.filter {
                        it.mediaType == PendingMediaType.CAMERA_PHOTO || it.mediaType == PendingMediaType.CAMERA_VIDEO
                    }
                for (record in cameraRecords) {
                    val recordPaths = listOfNotNull(record.filePath, record.fallbackFilePath).distinct()
                    for (sourcePath in recordPaths) {
                        val transferPath = nativeTransferMediaPaths(setOf(sourcePath), packageName, filesDir).singleOrNull()
                        if (transferPath == null || !transferPath.startsWith("files/camera-captures/")) {
                            if (retainedCanonicalMediaDigest(sourcePath) == null) return false
                            continue
                        }
                        val entry = inventory.entry(transferPath) ?: return false
                        val sourceFile = sourceFiles[transferPath] ?: return false
                        if (!verifyFile(sourceFile, entry)) return false
                        requireCurrent()
                        val target =
                            File(
                                File(filesDir, "camera-captures"),
                                importedCaptureName(generation, draft.id.toString(), record.blockId.toString(), transferPath),
                            )
                        copyDurably(sourceFile, target, entry)
                        requireCurrent()
                        recoveredReferences[sourcePath] = target.absolutePath
                    }
                }
                if (!entryDraftCameraMediaIsComplete(draft, recoveredReferences.keys) { reference ->
                        requireCurrent()
                        media.isRecoveredOriginal(reference, StoredMediaReferences.Unchanged)
                    }
                ) {
                    return false
                }
                if (RetainedPendingCameraDraftAcceptance(drafts).accept(generation, draft, recoveredReferences, canCommit, scopeFence) ==
                    null
                ) {
                    return false
                }
            }
            return true
        } finally {
            storeScope.cancel()
        }
    }

    private fun copyDurably(
        source: File,
        target: File,
        entry: NativeJournalMediaInventory.Entry,
    ) {
        val parent = ensurePrivateCameraCaptureDirectory(filesDir)
        check(target.parentFile == parent)
        if (target.exists()) {
            check(verifyFile(target, entry)) { "Existing retained camera original differs from the incoming source" }
            return
        }
        val temporary = File.createTempFile(".camera-transfer-", ".part", parent)
        try {
            source.inputStream().use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            check(verifyFile(temporary, entry)) { "Staged camera bytes changed during handoff" }
            try {
                Files.createLink(target.toPath(), temporary.toPath())
            } catch (_: java.nio.file.FileAlreadyExistsException) {
                check(verifyFile(target, entry)) { "Concurrent camera handoff produced different bytes" }
            }
            FileChannel.open(parent.toPath(), StandardOpenOption.READ).use { it.force(true) }
        } finally {
            temporary.delete()
        }
    }

    private fun verifyFile(
        file: File,
        entry: NativeJournalMediaInventory.Entry,
    ): Boolean =
        Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(file.toPath()) &&
            file.length() == entry.size &&
            nativeSnapshotChecksum(file) == entry.sha256

    private fun importedCaptureName(
        generation: String,
        draftId: String,
        blockId: String,
        sourcePath: String,
    ): String {
        val basename = sourcePath.substringAfterLast('/')
        require(basename.isNotBlank() && basename.none { it == '/' || it == '\\' || it.code < 32 })
        val extension = basename.substringAfterLast('.', "").takeIf { it.matches(Regex("[A-Za-z0-9]{1,12}")) }
        val pathDigest =
            java.security.MessageDigest
                .getInstance("SHA-256")
                .digest(sourcePath.encodeToByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(16)
        return "transfer-${generation.take(12)}-$draftId-$blockId-$pathDigest${extension?.let { ".$it" }.orEmpty()}"
    }

    private companion object {
        const val ENTRY_DRAFTS_PATH = "files/datastore/entry_drafts.preferences_pb"
    }
}

internal fun ensurePrivateCameraCaptureDirectory(filesDir: File): File {
    val root = filesDir.toPath()
    check(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(root))
    val directory = File(filesDir, "camera-captures")
    val path = directory.toPath()
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
        try {
            Files.createDirectory(path)
        } catch (_: java.nio.file.FileAlreadyExistsException) {
            // A concurrent importer may have created this directory.
        }
    }
    check(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path))
    return directory
}

internal suspend fun entryDraftCameraMediaIsComplete(
    draft: app.logdate.client.repository.journals.EntryDraft,
    recoveredCameraPaths: Set<String>,
    isAvailableAtDestination: suspend (String) -> Boolean,
): Boolean {
    if (draft.pendingMedia.any {
            it.mediaType != PendingMediaType.CAMERA_PHOTO && it.mediaType != PendingMediaType.CAMERA_VIDEO
        }
    ) {
        return false
    }
    return draft.lateMediaReferences().all { it in recoveredCameraPaths || isAvailableAtDestination(it) }
}

internal fun hasPendingCameraOriginal(draft: app.logdate.client.repository.journals.EntryDraft): Boolean =
    draft.pendingMedia.any { it.mediaType == PendingMediaType.CAMERA_PHOTO || it.mediaType == PendingMediaType.CAMERA_VIDEO }
