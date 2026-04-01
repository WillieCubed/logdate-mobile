package app.logdate.feature.core.export

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
import app.logdate.client.domain.export.ExportFormat
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android-specific implementation for launching data export using Storage Access Framework and WorkManager.
 */
class AndroidExportLauncher(
    private val context: Context,
) : ExportLauncher {
    private var pendingExportCallback: (() -> Unit)? = null
    private var lastSelectedUri: Uri? = null
    private var completionCallback: ((String?) -> Unit)? = null
    private var workInfoObserver: Observer<List<WorkInfo>>? = null
    private var pendingExportOptions: ExportOptions = ExportOptions()

    private val _exportProgress = MutableStateFlow(ExportProgressInfo())
    override val exportProgress: StateFlow<ExportProgressInfo> = _exportProgress.asStateFlow()

    // This will be created and registered by the activity when this class is instantiated
    private var createDocumentLauncher: ActivityResultLauncher<Intent>? = null

    /**
     * Sets up the activity result launcher. This should be called from the main activity during setup.
     */
    fun setupActivityResultLauncher(launcher: ActivityResultLauncher<Intent>) {
        createDocumentLauncher = launcher
    }

    /**
     * Set up lifecycle observer for the export work.
     * This should be called by the MainActivity.
     */
    fun setupWorkObserver(lifecycleOwner: LifecycleOwner) {
        // Remove previous observer if it exists
        if (workInfoObserver != null) {
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(ExportWorker.WORK_NAME)
                .removeObserver(workInfoObserver!!)
        }

        // Create new observer for failure and cancellation states only.
        // Success completion is signaled via ExportLauncher.updateProgress() flow,
        // which carries stats. This eliminates race conditions and duplication.
        workInfoObserver =
            Observer { workInfoList ->
                if (workInfoList.isEmpty()) return@Observer

                val workInfo = workInfoList[0]

                when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        // Success is signaled via updateProgress() by the worker.
                        // Do nothing here — let the flow be the source of truth.
                        Napier.i("Export work succeeded")
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        _exportProgress.value = ExportProgressInfo()
                        Napier.i("Export failed or was cancelled")
                        completionCallback?.invoke(null)
                    }
                    else -> Unit
                }
            }

        // Register observer if lifecycle is at least STARTED
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(ExportWorker.WORK_NAME)
                .observe(lifecycleOwner, workInfoObserver!!)
        }
    }

    override fun updateProgress(info: ExportProgressInfo) {
        Napier.d("Export progress: ${info.progressPercent}% - ${info.message}")
        _exportProgress.value = info
    }

    override fun setExportCompletionCallback(callback: (String?) -> Unit) {
        completionCallback = callback
    }

    override fun startExport(options: ExportOptions) {
        pendingExportOptions = options
        val defaultFileName = generateExportFileName()

        // Create an intent to show the file picker
        val intent =
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = ExportFormat.MIME_TYPE
                putExtra(Intent.EXTRA_TITLE, defaultFileName)
            }

        try {
            pendingExportCallback = {
                if (lastSelectedUri != null) {
                    startExportWorker(lastSelectedUri!!)
                } else {
                    Napier.w("No URI selected for export")
                    completionCallback?.invoke(null)
                }
            }

            createDocumentLauncher?.launch(intent) ?: run {
                // If no launcher is available, fall back to default export
                Napier.w("No document launcher available, falling back to default export path")
                startExportWorker(null)
            }
        } catch (e: Exception) {
            Napier.e("Error launching file picker", e)
            // Fall back to default export if anything goes wrong
            startExportWorker(null)
        }
    }

    override fun cancelExport() {
        // Cancel the pending callback if the file picker is still open
        pendingExportCallback = null

        // Cancel the work if it's running
        WorkManager.getInstance(context).cancelUniqueWork(ExportWorker.WORK_NAME)

        // Reset progress
        _exportProgress.value = ExportProgressInfo()

        // Notify the callback
        completionCallback?.invoke(null)

        Napier.i("Export cancelled")
    }

    /**
     * Called when the user has selected a destination or cancelled the dialog
     */
    fun onExportDestinationSelected(uri: Uri?) {
        lastSelectedUri = uri

        if (uri != null) {
            try {
                // Take a persistent permission to write to this URI
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )

                Napier.i("User selected export destination: $uri")
                pendingExportCallback?.invoke()
            } catch (e: Exception) {
                Napier.e("Failed to take persistent URI permission", e)
                completionCallback?.invoke(null)
            }
        } else {
            Napier.i("User cancelled export destination selection")
            completionCallback?.invoke(null)
        }

        pendingExportCallback = null
    }

    /**
     * Starts the export worker with the provided URI
     */
    private fun startExportWorker(uri: Uri?) {
        Napier.i("Starting export worker")
        _exportProgress.value =
            ExportProgressInfo(
                isActive = true,
                progressPercent = 0,
                message = "Preparing export…",
            )

        val dataBuilder =
            Data
                .Builder()
                .putBoolean(ExportWorker.INCLUDE_JOURNALS_KEY, pendingExportOptions.includeJournals)
                .putBoolean(ExportWorker.INCLUDE_NOTES_KEY, pendingExportOptions.includeNotes)
                .putBoolean(ExportWorker.INCLUDE_DRAFTS_KEY, pendingExportOptions.includeDrafts)
                .putBoolean(ExportWorker.INCLUDE_MEDIA_KEY, pendingExportOptions.includeMedia)
                .putLong(
                    ExportWorker.DATE_CUTOFF_MILLIS_KEY,
                    pendingExportOptions.dateRange.toCutoffInstant()?.toEpochMilliseconds() ?: -1L,
                )

        if (uri != null) {
            dataBuilder.putString(ExportWorker.DESTINATION_URI_KEY, uri.toString())
        }

        val inputData = dataBuilder.build()

        val workRequest =
            OneTimeWorkRequestBuilder<ExportWorker>()
                .setInputData(inputData)
                .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(
                ExportWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest,
            )

        Napier.i("Export work enqueued with WorkManager")
    }
}
