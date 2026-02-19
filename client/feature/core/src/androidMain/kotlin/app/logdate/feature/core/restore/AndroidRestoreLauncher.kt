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
import io.github.aakira.napier.Napier
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Android-specific implementation for launching data restore using Storage Access Framework and WorkManager.
 */
class AndroidRestoreLauncher(
    private val context: Context
) : RestoreLauncher {

    private val json = Json { ignoreUnknownKeys = true }

    private var pendingRestoreCallback: (() -> Unit)? = null
    private var lastSelectedUri: Uri? = null
    private var completionCallback: ((RestoreOutcome) -> Unit)? = null
    private var workInfoObserver: Observer<List<WorkInfo>>? = null

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
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(RestoreWorker.WORK_NAME)
                .removeObserver(workInfoObserver!!)
        }

        workInfoObserver = Observer { workInfoList ->
            if (workInfoList.isEmpty()) return@Observer

            val workInfo = workInfoList[0]

            when (workInfo.state) {
                WorkInfo.State.SUCCEEDED -> {
                    val summaryJson = workInfo.outputData.getString(RestoreWorker.SUMMARY_JSON_KEY)
                    if (summaryJson != null) {
                        val summary = runCatching { json.decodeFromString<RestoreSummary>(summaryJson) }
                            .getOrNull()
                        if (summary != null) {
                            completionCallback?.invoke(RestoreOutcome.Success(summary))
                        } else {
                            completionCallback?.invoke(RestoreOutcome.Failure("Restore completed, but summary was invalid"))
                        }
                    } else {
                        completionCallback?.invoke(RestoreOutcome.Failure("Restore completed, but no summary was returned"))
                    }
                }
                WorkInfo.State.FAILED -> {
                    val error = workInfo.outputData.getString(RestoreWorker.ERROR_KEY)
                    completionCallback?.invoke(RestoreOutcome.Failure(error ?: "Restore failed"))
                }
                WorkInfo.State.CANCELLED -> {
                    completionCallback?.invoke(RestoreOutcome.Cancelled)
                }
                else -> {
                    // Still in progress, do nothing
                }
            }
        }

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(RestoreWorker.WORK_NAME)
                .observe(lifecycleOwner, workInfoObserver!!)
        }
    }

    override fun setRestoreCompletionCallback(callback: (RestoreOutcome) -> Unit) {
        completionCallback = callback
    }

    override fun startRestore() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/octet-stream"))
        }

        try {
            pendingRestoreCallback = {
                val uri = lastSelectedUri
                if (uri != null) {
                    startRestoreWorker(uri)
                } else {
                    Napier.w("No URI selected for restore")
                    completionCallback?.invoke(RestoreOutcome.Cancelled)
                }
            }

            openDocumentLauncher?.launch(intent) ?: run {
                Napier.w("No document launcher available for restore")
                completionCallback?.invoke(RestoreOutcome.Failure("Restore requires a file picker"))
            }
        } catch (e: Exception) {
            Napier.e("Error launching restore file picker", e)
            completionCallback?.invoke(RestoreOutcome.Failure("Failed to launch restore picker"))
        }
    }

    override fun cancelRestore() {
        pendingRestoreCallback = null
        WorkManager.getInstance(context).cancelUniqueWork(RestoreWorker.WORK_NAME)
        completionCallback?.invoke(RestoreOutcome.Cancelled)
        Napier.i("Restore cancelled")
    }

    /**
     * Called when the user has selected a restore archive or cancelled the dialog.
     */
    fun onRestoreSourceSelected(uri: Uri?) {
        lastSelectedUri = uri

        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                Napier.i("User selected restore source: $uri")
                pendingRestoreCallback?.invoke()
            } catch (e: Exception) {
                Napier.e("Failed to take persistent URI permission", e)
                completionCallback?.invoke(RestoreOutcome.Failure("Unable to access selected file"))
            }
        } else {
            Napier.i("User cancelled restore source selection")
            completionCallback?.invoke(RestoreOutcome.Cancelled)
        }

        pendingRestoreCallback = null
    }

    private fun startRestoreWorker(uri: Uri) {
        completionCallback?.invoke(RestoreOutcome.Started)

        val inputData = Data.Builder()
            .putString(RestoreWorker.SOURCE_URI_KEY, uri.toString())
            .build()

        val workRequest = OneTimeWorkRequestBuilder<RestoreWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                RestoreWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

        Napier.i("Restore work enqueued with WorkManager")
    }
}
