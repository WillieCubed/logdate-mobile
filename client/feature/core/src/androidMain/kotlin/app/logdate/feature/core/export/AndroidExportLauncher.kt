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
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.UUID

/**
 * Android-specific implementation for launching data export using Storage Access Framework and WorkManager.
 */
class AndroidExportLauncher(
    private val context: Context,
) : ExportLauncher {
    
    private var pendingExportCallback: (() -> Unit)? = null
    private var lastSelectedUri: Uri? = null
    private var completionCallback: ((String?) -> Unit)? = null
    private var currentWorkId: UUID? = null
    private var workInfoObserver: Observer<List<WorkInfo>>? = null
    
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
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(ExportWorker.WORK_NAME)
                .removeObserver(workInfoObserver!!)
        }
        
        // Create new observer
        workInfoObserver = Observer { workInfoList ->
            if (workInfoList.isEmpty()) return@Observer
            
            val workInfo = workInfoList[0]
            
            when (workInfo.state) {
                WorkInfo.State.SUCCEEDED -> {
                    val filePath = workInfo.outputData.getString(ExportWorker.FILE_PATH_KEY)
                    if (filePath != null) {
                        Napier.i("Export completed: $filePath")
                        completionCallback?.invoke(filePath)
                    } else {
                        Napier.w("Export completed but no file path was returned")
                        completionCallback?.invoke(null)
                    }
                }
                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                    Napier.i("Export failed or was cancelled")
                    completionCallback?.invoke(null)
                }
                else -> {
                    // Still in progress, do nothing
                }
            }
        }
        
        // Register observer if lifecycle is at least STARTED
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(ExportWorker.WORK_NAME)
                .observe(lifecycleOwner, workInfoObserver!!)
        }
    }
    
    override fun setExportCompletionCallback(callback: (String?) -> Unit) {
        completionCallback = callback
    }
    
    override fun startExport() {
        // Generate default filename with timestamp
        val timestamp = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .let { "${it.year}-${it.monthNumber.toString().padStart(2, '0')}-${it.dayOfMonth.toString().padStart(2, '0')}_${it.hour.toString().padStart(2, '0')}-${it.minute.toString().padStart(2, '0')}" }
        
        val defaultFileName = "logdate_export_$timestamp.json"
        
        // Create an intent to show the file picker
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
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
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
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
        val inputData = if (uri != null) {
            Data.Builder()
                .putString(ExportWorker.DESTINATION_URI_KEY, uri.toString())
                .build()
        } else {
            Data.Builder().build()
        }
        
        val workRequest = OneTimeWorkRequestBuilder<ExportWorker>()
            .setInputData(inputData)
            .build()
        
        currentWorkId = workRequest.id
            
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                ExportWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE, // Replace any existing export work
                workRequest
            )
            
        Napier.i("Export work enqueued with WorkManager")
    }
}