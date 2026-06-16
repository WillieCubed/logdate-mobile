package app.logdate.feature.core.restore

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.logdate.client.domain.backup.EncryptedBackupFileFormat
import app.logdate.client.domain.export.ExportFileStructure
import app.logdate.client.domain.export.ExportFormat
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okio.buffer
import okio.source
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

/**
 * Android-specific [RestoreLauncher] backed by Storage Access Framework and WorkManager.
 *
 * ## Completion signaling
 *
 * Terminal outcomes ([RestoreOutcome.Success], [RestoreOutcome.Failure],
 * [RestoreOutcome.Cancelled]) are delivered to the registered completion callback exactly
 * once per restore operation, regardless of which path fires first:
 *
 * 1. **Direct** — [RestoreWorker] calls [completeRestore] before returning, carrying the
 *    typed outcome directly.
 * 2. **Fallback** — The [WorkManager] LiveData observer in [setupWorkObserver] fires when
 *    the worker's terminal [WorkInfo.State] is delivered. This covers the case where the
 *    worker is killed before it can call [completeRestore].
 *
 * [restoreCompleted] gates the callback via compare-and-set so only the first path to
 * arrive fires it. It is reset each time [startRestore] is called.
 *
 * ## File selection
 *
 * [onRestoreSourceSelected] extracts `metadata.json` from the chosen archive for preview
 * without a full copy to disk. This I/O is dispatched to [launcherScope] so it never
 * blocks the main thread. Any in-flight extraction is cancelled before a new one begins,
 * tracked by [metadataExtractionJob].
 */
class AndroidRestoreLauncher(
    private val context: Context,
) : RestoreLauncher {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Scope for metadata extraction coroutines. Lives for the lifetime of this singleton;
     * individual jobs are tracked via [metadataExtractionJob] and cancelled as needed.
     */
    private val launcherScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Guards the completion callback so it fires at most once per restore operation.
     * Reset in [startRestore]; set atomically in [completeRestore] and the WorkManager
     * fallback observer.
     */
    private val restoreCompleted = AtomicBoolean(false)

    /** The active metadata extraction job, if any. Cancelled before starting a new one. */
    private var metadataExtractionJob: Job? = null

    private var lastSelectedUri: Uri? = null
    private var workInfoObserver: Observer<List<WorkInfo>>? = null

    @Volatile private var completionCallback: ((RestoreOutcome) -> Unit)? = null

    @Volatile private var fileSelectedCallback: ((ArchiveFileInfo?) -> Unit)? = null

    private val _restoreProgress = MutableStateFlow<RestoreProgressInfo>(RestoreProgressInfo.Idle)
    override val restoreProgress: StateFlow<RestoreProgressInfo> = _restoreProgress.asStateFlow()

    private var openDocumentLauncher: ActivityResultLauncher<Intent>? = null

    /**
     * Registers the SAF [launcher] used to open the system file picker.
     * Must be called from the host activity before [startFileSelection].
     */
    fun setupActivityResultLauncher(launcher: ActivityResultLauncher<Intent>) {
        openDocumentLauncher = launcher
    }

    /**
     * Attaches a lifecycle-aware WorkManager observer for this [lifecycleOwner].
     *
     * The observer acts as a fallback completion signal: it fires the callback only if
     * [completeRestore] was not already called by the worker. Must be called from the
     * host activity; typically in `onCreate`.
     */
    fun setupWorkObserver(lifecycleOwner: LifecycleOwner) {
        workInfoObserver?.let {
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(RestoreWorker.WORK_NAME)
                .removeObserver(it)
        }

        workInfoObserver =
            Observer { workInfoList ->
                if (workInfoList.isEmpty()) return@Observer
                val workInfo = workInfoList[0]

                when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        _restoreProgress.value = RestoreProgressInfo.Idle
                        if (restoreCompleted.compareAndSet(false, true)) {
                            val summaryJson = workInfo.outputData.getString(RestoreWorker.SUMMARY_JSON_KEY)
                            val summary =
                                summaryJson?.let {
                                    runCatching { json.decodeFromString<RestoreSummary>(it) }.getOrNull()
                                }
                            completionCallback?.invoke(
                                if (summary != null) {
                                    RestoreOutcome.Success(summary)
                                } else {
                                    RestoreOutcome.Failure(
                                        if (summaryJson != null) {
                                            RestoreError.INVALID_SUMMARY
                                        } else {
                                            RestoreError.NO_SUMMARY_RETURNED
                                        },
                                    )
                                },
                            )
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        _restoreProgress.value = RestoreProgressInfo.Idle
                        if (restoreCompleted.compareAndSet(false, true)) {
                            completionCallback?.invoke(RestoreOutcome.Failure(RestoreError.RESTORE_FAILED))
                        }
                    }
                    WorkInfo.State.CANCELLED -> {
                        _restoreProgress.value = RestoreProgressInfo.Idle
                        if (restoreCompleted.compareAndSet(false, true)) {
                            completionCallback?.invoke(RestoreOutcome.Cancelled)
                        }
                    }
                    else -> Unit
                }
            }

        WorkManager
            .getInstance(context)
            .getWorkInfosForUniqueWorkLiveData(RestoreWorker.WORK_NAME)
            .observe(lifecycleOwner, workInfoObserver!!)
    }

    override fun setRestoreCompletionCallback(callback: (RestoreOutcome) -> Unit) {
        completionCallback = callback
    }

    override fun setFileSelectedCallback(callback: (ArchiveFileInfo?) -> Unit) {
        fileSelectedCallback = callback
    }

    override fun updateProgress(info: RestoreProgressInfo) {
        _restoreProgress.value = info
    }

    /**
     * Primary completion signal, called directly by [RestoreWorker] before it returns.
     *
     * Fires the completion callback exactly once. If the WorkManager fallback observer
     * arrives first (rare), this call is a no-op.
     */
    override fun completeRestore(outcome: RestoreOutcome) {
        _restoreProgress.value = RestoreProgressInfo.Idle
        if (restoreCompleted.compareAndSet(false, true)) {
            completionCallback?.invoke(outcome)
        }
    }

    override fun startFileSelection() {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, ExportFormat.ACCEPTED_IMPORT_MIME_TYPES)
            }

        try {
            openDocumentLauncher?.launch(intent) ?: run {
                Napier.w("No document launcher available for restore")
                completionCallback?.invoke(RestoreOutcome.Failure(RestoreError.FILE_PICKER_UNAVAILABLE))
            }
        } catch (e: Exception) {
            Napier.e("Error launching restore file picker", e)
            completionCallback?.invoke(RestoreOutcome.Failure(RestoreError.FILE_PICKER_FAILED))
        }
    }

    override fun startRestore(options: ImportOptions) {
        val uri =
            lastSelectedUri ?: run {
                Napier.w("No URI selected for restore")
                completionCallback?.invoke(RestoreOutcome.Failure(RestoreError.MISSING_SOURCE))
                return
            }

        restoreCompleted.set(false)
        completionCallback?.invoke(RestoreOutcome.Started)
        _restoreProgress.value = RestoreProgressInfo.Idle

        val inputData =
            Data
                .Builder()
                .putString(RestoreWorker.SOURCE_URI_KEY, uri.toString())
                .putBoolean(RestoreWorker.INCLUDE_DRAFTS_KEY, options.includeDrafts)
                .putBoolean(RestoreWorker.INCLUDE_MEDIA_KEY, options.includeMedia)
                .build()

        val workRequest =
            OneTimeWorkRequestBuilder<RestoreWorker>()
                .setInputData(inputData)
                .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(
                RestoreWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest,
            )

        Napier.i("Restore work enqueued with WorkManager")
    }

    override fun cancelRestore() {
        metadataExtractionJob?.cancel()
        metadataExtractionJob = null
        lastSelectedUri = null
        WorkManager.getInstance(context).cancelUniqueWork(RestoreWorker.WORK_NAME)
        _restoreProgress.value = RestoreProgressInfo.Idle
        if (restoreCompleted.compareAndSet(false, true)) {
            completionCallback?.invoke(RestoreOutcome.Cancelled)
        }
        Napier.i("Restore cancelled")
    }

    /**
     * Called when the user selects a restore archive or dismisses the file picker.
     *
     * Reads `metadata.json` from the archive on [launcherScope] for preview without a
     * full archive copy. Any previously in-flight extraction is cancelled first. On
     * success, delivers an [ArchiveFileInfo] to the file-selected callback. On failure
     * (unparseable archive, missing entry, or permission error), delivers a null file
     * info and an [RestoreOutcome.Failure] to the completion callback.
     */
    fun onRestoreSourceSelected(uri: Uri?) {
        if (uri == null) {
            Napier.i("User cancelled restore source selection")
            fileSelectedCallback?.invoke(null)
            return
        }

        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (e: Exception) {
            Napier.e("Failed to take persistent URI permission", e)
            completionCallback?.invoke(RestoreOutcome.Failure(RestoreError.FILE_NOT_ACCESSIBLE))
            return
        }

        lastSelectedUri = uri
        val displayName = context.contentResolver.resolveDisplayName(uri) ?: uri.lastPathSegment ?: "archive"

        metadataExtractionJob?.cancel()
        metadataExtractionJob =
            launcherScope.launch {
                if (isEncryptedBackup(uri)) {
                    fileSelectedCallback?.invoke(
                        ArchiveFileInfo(
                            displayName = displayName,
                            uri = uri.toString(),
                            archiveFormat = RestoreArchiveFormat.EncryptedBackup,
                        ),
                    )
                    return@launch
                }

                val metadataJson = extractMetadata(uri)
                if (metadataJson == null) {
                    Napier.w("Could not extract metadata from selected archive")
                    fileSelectedCallback?.invoke(null)
                    completionCallback?.invoke(RestoreOutcome.Failure(RestoreError.INVALID_ARCHIVE))
                    return@launch
                }

                fileSelectedCallback?.invoke(
                    ArchiveFileInfo(
                        displayName = displayName,
                        uri = uri.toString(),
                        metadataJson = metadataJson,
                        archiveFormat = RestoreArchiveFormat.LegacyZip,
                    ),
                )
            }
    }

    private fun isEncryptedBackup(uri: Uri): Boolean =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { rawInput ->
                EncryptedBackupFileFormat.isEncryptedBackup(rawInput.source().buffer())
            } ?: false
        }.getOrDefault(false)

    /**
     * Reads only the `metadata.json` entry from the archive for preview.
     *
     * Streams through the ZIP sequentially so the full archive is never loaded into
     * memory. Returns `null` if the entry is missing or the stream cannot be read.
     */
    private fun extractMetadata(uri: Uri): String? =
        try {
            context.contentResolver.openInputStream(uri)?.use { rawInput ->
                ZipInputStream(rawInput).use { zipInput ->
                    var entry = zipInput.nextEntry
                    while (entry != null) {
                        if (entry.name == ExportFileStructure.METADATA_FILE) {
                            return@use zipInput.bufferedReader(Charsets.UTF_8).readText()
                        }
                        zipInput.closeEntry()
                        entry = zipInput.nextEntry
                    }
                    null
                }
            }
        } catch (e: Exception) {
            Napier.e("Failed to extract metadata from archive", e)
            null
        }
}
