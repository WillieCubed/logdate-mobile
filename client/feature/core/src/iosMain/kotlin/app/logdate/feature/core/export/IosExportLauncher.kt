package app.logdate.feature.core.export

import app.logdate.client.domain.export.ExportProgress
import app.logdate.client.domain.export.ExportUserDataUseCase
import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.autoreleasepool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UniformTypeIdentifiers.UTTypeJSON

/**
 * iOS-specific implementation for launching data export.
 * 
 * This implementation creates a temporary JSON file and presents the iOS share sheet
 * to let the user choose where to save it.
 */
class IosExportLauncher(
    private val rootViewController: () -> UIViewController
) : ExportLauncher, KoinComponent {
    
    private val exportUserDataUseCase: ExportUserDataUseCase by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentExportJob: Job? = null
    private var completionCallback: ((String?) -> Unit)? = null
    
    override fun setExportCompletionCallback(callback: (String?) -> Unit) {
        completionCallback = callback
    }
    
    @OptIn(ExperimentalForeignApi::class)
    override fun startExport() {
        // Cancel any ongoing export job
        currentExportJob?.cancel()
        
        // Start a new export job
        currentExportJob = scope.launch {
            try {
                Napier.i("iOS: Starting export process")
                
                exportUserDataUseCase.exportUserData()
                    .catch { exception ->
                        Napier.e("iOS: Export failed", exception)
                        showAlert(
                            title = "Export Failed",
                            message = exception.message ?: "Unknown error occurred"
                        )
                        completionCallback?.invoke(null)
                    }
                    .collect { progress ->
                        when (progress) {
                            is ExportProgress.Starting -> {
                                Napier.i("iOS: Export started")
                            }
                            
                            is ExportProgress.InProgress -> {
                                val progressInt = (progress.progress * 100).toInt()
                                Napier.d("iOS: Export progress: $progressInt% - ${progress.message}")
                            }
                            
                            is ExportProgress.Completed -> {
                                try {
                                    // Generate a timestamp for the filename
                                    val timestamp = Clock.System.now()
                                        .toLocalDateTime(TimeZone.currentSystemDefault())
                                        .let { "${it.year}-${it.monthNumber.toString().padStart(2, '0')}-${it.dayOfMonth.toString().padStart(2, '0')}_${it.hour.toString().padStart(2, '0')}-${it.minute.toString().padStart(2, '0')}" }
                                    
                                    val fileName = "logdate_export_$timestamp.json"
                                    
                                    // Create a temporary file
                                    val tempFilePath = NSTemporaryDirectory() + fileName
                                    
                                    // Save JSON data to temporary file
                                    autoreleasepool {
                                        val nsString = NSString.create(string = progress.jsonData)
                                        val data = nsString.dataUsingEncoding(NSUTF8StringEncoding) ?: return@autoreleasepool
                                        data.writeToFile(tempFilePath, true)
                                    }
                                    
                                    // Present sharing options
                                    presentShareSheet(tempFilePath)
                                    
                                    Napier.i("iOS: Export completed and share sheet presented")
                                    completionCallback?.invoke(tempFilePath)
                                } catch (e: Exception) {
                                    Napier.e("iOS: Failed to save or share file", e)
                                    showAlert(
                                        title = "Export Failed",
                                        message = "Could not save or share the export file: ${e.message}"
                                    )
                                    completionCallback?.invoke(null)
                                }
                            }
                            
                            is ExportProgress.Failed -> {
                                Napier.e("iOS: Export failed: ${progress.errorMessage}")
                                showAlert(
                                    title = "Export Failed",
                                    message = progress.errorMessage
                                )
                                completionCallback?.invoke(null)
                            }
                        }
                    }
            } catch (e: Exception) {
                Napier.e("iOS: Export process failed", e)
                showAlert(
                    title = "Export Failed",
                    message = "Export process failed: ${e.message}"
                )
                completionCallback?.invoke(null)
            }
        }
    }
    
    override fun cancelExport() {
        currentExportJob?.cancel()
        currentExportJob = null
        completionCallback?.invoke(null)
        Napier.i("iOS: Export cancelled")
    }
    
    /**
     * Presents the iOS share sheet with the exported file.
     */
    private fun presentShareSheet(filePath: String) {
        val fileURL = platform.Foundation.NSURL.fileURLWithPath(filePath)
        
        // Create a document interaction controller
        val activityViewController = UIActivityViewController(
            activityItems = listOf(fileURL),
            applicationActivities = null
        )
        
        // Present the share sheet
        rootViewController().presentViewController(
            viewControllerToPresent = activityViewController,
            animated = true,
            completion = null
        )
    }
    
    /**
     * Shows a simple alert dialog.
     */
    private fun showAlert(title: String, message: String) {
        val alertController = platform.UIKit.UIAlertController.alertControllerWithTitle(
            title = title,
            message = message,
            preferredStyle = platform.UIKit.UIAlertControllerStyleAlert
        )
        
        alertController.addAction(
            platform.UIKit.UIAlertAction.actionWithTitle(
                title = "OK",
                style = platform.UIKit.UIAlertActionStyleDefault,
                handler = null
            )
        )
        
        rootViewController().presentViewController(
            viewControllerToPresent = alertController,
            animated = true,
            completion = null
        )
    }
}