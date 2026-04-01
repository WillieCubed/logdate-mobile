package app.logdate.feature.core.restore

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.logdate.client.domain.export.ExportFileStructure
import app.logdate.client.domain.export.ExportFormat
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.util.zip.ZipInputStream

/**
 * Android-specific implementation for launching data restore using Storage Access Framework and WorkManager.
 */
class AndroidRestoreLauncher(
    private val context: Context,
) : RestoreLauncher {
    private val json = Json { ignoreUnknownKeys = true }

    private var lastSelectedUri: Uri? = null
    private var completionCallback: ((RestoreOutcome) -> Unit)? = null
    private var fileSelectedCallback: ((ArchiveFileInfo?) -> Unit)? = null
    private var workInfoObserver: Observer<List<WorkInfo>>? = null

    private val _restoreProgress = MutableStateFlow<RestoreProgressInfo>(RestoreProgressInfo.Idle)
    override val restoreProgress: StateFlow<RestoreProgressInfo> = _restoreProgress.asStateFlow()

    private var openDocumentLauncher: ActivityResultLauncher<Intent>? = null

    /**
     * Sets up the activity result launcher. This should be called from the main activity during setup.
     */
    fun setupActivityResultLauncher(launcher: ActivityResultLauncher<Intent>) {
        openDocumentLauncher = launcher
    }

    /**
     * Set up lifecycle observer for the restore work.
     * This should be called by the MainActivity.
     */
    fun setupWorkObserver(lifecycleOwner: LifecycleOwner) {
        if (workInfoObserver != null) {
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(RestoreWorker.WORK_NAME)
                .removeObserver(workInfoObserver!!)
        }

        workInfoObserver =
            Observer { workInfoList ->
                if (workInfoList.isEmpty()) return@Observer

                val workInfo = workInfoList[0]

                when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        _restoreProgress.value = RestoreProgressInfo.Idle
                        val summaryJson = workInfo.outputData.getString(RestoreWorker.SUMMARY_JSON_KEY)
                        if (summaryJson != null) {
                            val summary =
                                runCatching { json.decodeFromString<RestoreSummary>(summaryJson) }
                                    .getOrNull()
                            if (summary != null) {
                                completionCallback?.invoke(RestoreOutcome.Success(summary))
                            } else {
                                completionCallback?.invoke(RestoreOutcome.Failure(RestoreError.INVALID_SUMMARY))
                            }
                        } else {
                            completionCallback?.invoke(RestoreOutcome.Failure(RestoreError.NO_SUMMARY_RETURNED))
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        _restoreProgress.value = RestoreProgressInfo.Idle
                        completionCallback?.invoke(RestoreOutcome.Failure(RestoreError.RESTORE_FAILED))
                    }
                    WorkInfo.State.CANCELLED -> {
                        _restoreProgress.value = RestoreProgressInfo.Idle
                        completionCallback?.invoke(RestoreOutcome.Cancelled)
                    }
                    else -> {
                        // Still in progress, do nothing
                    }
                }
            }

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(RestoreWorker.WORK_NAME)
                .observe(lifecycleOwner, workInfoObserver!!)
        }
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

    override fun completeRestore(outcome: RestoreOutcome) {
        _restoreProgress.value = RestoreProgressInfo.Idle
        completionCallback?.invoke(outcome)
        Napier.i("Restore completed via direct signal: $outcome")
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
        lastSelectedUri = null
        WorkManager.getInstance(context).cancelUniqueWork(RestoreWorker.WORK_NAME)
        _restoreProgress.value = RestoreProgressInfo.Idle
        completionCallback?.invoke(RestoreOutcome.Cancelled)
        Napier.i("Restore cancelled")
    }

    /**
     * Called when the user has selected a restore archive or cancelled the dialog.
     *
     * Instead of immediately starting a restore, this extracts the archive's
     * metadata and delivers it to the file-selected callback for preview.
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

        val metadataJson = extractMetadata(uri)
        if (metadataJson == null) {
            Napier.w("Could not extract metadata from selected archive")
            fileSelectedCallback?.invoke(null)
            completionCallback?.invoke(RestoreOutcome.Failure(RestoreError.INVALID_ARCHIVE))
            return
        }

        fileSelectedCallback?.invoke(
            ArchiveFileInfo(
                displayName = displayName,
                uri = uri.toString(),
                metadataJson = metadataJson,
            ),
        )
    }

    /**
     * Extracts only the `metadata.json` entry from the archive for preview.
     *
     * Uses [java.util.zip.ZipInputStream] to read sequentially from the content
     * URI stream, avoiding a full archive copy to disk.
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
