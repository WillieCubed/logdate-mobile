package app.logdate.feature.core.export

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.logdate.client.domain.export.ExportProgress
import app.logdate.client.domain.export.ExportResult
import app.logdate.client.domain.export.ExportMediaFile
import app.logdate.client.domain.export.ExportUserDataUseCase
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.catch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * WorkManager worker for exporting user data according to the LogDate export specification.
 */
class ExportWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {
    
    companion object {
        const val WORK_NAME = "export_user_data"
        const val PROGRESS_KEY = "export_progress"
        const val MESSAGE_KEY = "export_message"
        const val FILE_PATH_KEY = "export_file_path"
        const val ERROR_KEY = "export_error"
        const val DESTINATION_URI_KEY = "destination_uri"
    }
    
    private val exportUserDataUseCase: ExportUserDataUseCase by inject()
    private val notificationHelper = ExportNotificationHelper(context, id)
    
    // Get destination URI from input data if available
    private val destinationUri: Uri? = inputData.getString(DESTINATION_URI_KEY)?.toUri()
    
    override suspend fun doWork(): Result {
        // Set this as a foreground service for long-running export work
        setForeground(getForegroundInfo())
        
        return try {
            var finalResult = Result.success()
            
            exportUserDataUseCase.exportUserData()
                .catch { exception ->
                    Napier.e("Export failed", exception)
                    setProgress(workDataOf(
                        PROGRESS_KEY to 0,
                        ERROR_KEY to (exception.message ?: "Unknown error occurred")
                    ))
                    finalResult = Result.failure(workDataOf(
                        ERROR_KEY to (exception.message ?: "Unknown error occurred")
                    ))
                }
                .collect { progress ->
                    when (progress) {
                        is ExportProgress.Starting -> {
                            setForeground(notificationHelper.createForegroundInfo(0, "Starting export..."))
                            setProgress(workDataOf(
                                PROGRESS_KEY to 0,
                                MESSAGE_KEY to "Starting export..."
                            ))
                        }
                        
                        is ExportProgress.InProgress -> {
                            val progressInt = (progress.percentage * 100).toInt()
                            setForeground(notificationHelper.createForegroundInfo(progressInt, progress.message))
                            setProgress(workDataOf(
                                PROGRESS_KEY to progressInt,
                                MESSAGE_KEY to progress.message
                            ))
                        }
                        
                        is ExportProgress.Completed -> {
                            try {
                                setForeground(notificationHelper.createForegroundInfo(90, "Creating ZIP archive..."))
                                
                                val filePath = if (destinationUri != null) {
                                    // User selected a custom destination
                                    saveToUri(progress.result, destinationUri)
                                } else {
                                    // Fall back to default Downloads directory
                                    saveToDownloads(progress.result)
                                }
                                
                                // Update notification with completion
                                setForeground(notificationHelper.createCompletionInfo(filePath))
                                
                                finalResult = Result.success(workDataOf(
                                    PROGRESS_KEY to 100,
                                    MESSAGE_KEY to "Export completed",
                                    FILE_PATH_KEY to filePath
                                ))
                            } catch (e: Exception) {
                                Napier.e("Failed to save file", e)
                                setForeground(notificationHelper.createErrorInfo("Failed to save file: ${e.message}"))
                                finalResult = Result.failure(workDataOf(
                                    ERROR_KEY to "Failed to save file: ${e.message}"
                                ))
                            }
                        }
                        
                        is ExportProgress.Failed -> {
                            setForeground(notificationHelper.createErrorInfo(progress.reason))
                            finalResult = Result.failure(workDataOf(
                                ERROR_KEY to progress.reason
                            ))
                        }
                    }
                }
            
            finalResult
            
        } catch (exception: Exception) {
            Napier.e("Worker execution failed", exception)
            setForeground(notificationHelper.createErrorInfo(exception.message ?: "Worker execution failed"))
            Result.failure(workDataOf(
                ERROR_KEY to (exception.message ?: "Worker execution failed")
            ))
        }
    }
    
    override suspend fun getForegroundInfo(): ForegroundInfo {
        return notificationHelper.createForegroundInfo(
            progress = 0,
            message = "Starting export..."
        )
    }
    
    /**
     * Save export data to a ZIP file at the content URI that the user selected
     */
    private fun saveToUri(exportData: ExportResult, uri: Uri): String {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            ZipOutputStream(outputStream).use { zipOut ->
                // Add metadata.json file
                zipOut.putNextEntry(ZipEntry("metadata.json"))
                zipOut.write(exportData.metadata.toByteArray())
                zipOut.closeEntry()
                
                // Add journals.json file
                zipOut.putNextEntry(ZipEntry("journals.json"))
                zipOut.write(exportData.journals.toByteArray())
                zipOut.closeEntry()
                
                // Add notes.json file
                zipOut.putNextEntry(ZipEntry("notes.json"))
                zipOut.write(exportData.notes.toByteArray())
                zipOut.closeEntry()
                
                // Add drafts.json file
                zipOut.putNextEntry(ZipEntry("drafts.json"))
                zipOut.write(exportData.drafts.toByteArray())
                zipOut.closeEntry()
                
                // Add media files
                // For each media file, we need to copy from the source URI to the ZIP
                exportData.mediaFiles.forEach { mediaFile ->
                    try {
                        zipOut.putNextEntry(ZipEntry(mediaFile.exportPath))
                        
                        // Get the actual media content from the source URI
                        val sourceUri = mediaFile.sourceUri.toUri()
                        context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                            inputStream.copyTo(zipOut)
                        }
                        
                        zipOut.closeEntry()
                    } catch (e: Exception) {
                        Napier.e("Failed to add media file to ZIP: ${mediaFile.sourceUri}", e)
                        // Continue with the rest of the files
                    }
                }
            }
        } ?: throw IllegalStateException("Could not open output stream for URI: $uri")
        
        // For display purposes, try to get a readable name for the URI
        val displayName = try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex("_display_name")
                    if (displayNameIndex != -1) {
                        cursor.getString(displayNameIndex)
                    } else {
                        uri.lastPathSegment ?: uri.toString()
                    }
                } else {
                    uri.lastPathSegment ?: uri.toString()
                }
            } ?: uri.lastPathSegment ?: uri.toString()
        } catch (e: Exception) {
            Napier.e("Error getting display name for URI", e)
            uri.lastPathSegment ?: uri.toString()
        }
        
        return "Selected location: $displayName"
    }
    
    /**
     * Fall back method to save to the Downloads directory if no URI was selected
     */
    private fun saveToDownloads(exportData: ExportResult): String {
        val timestamp = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .let { "${it.year}-${it.monthNumber.toString().padStart(2, '0')}-${it.dayOfMonth.toString().padStart(2, '0')}" }
        
        val fileName = "logdate-export-$timestamp.zip"
        
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)
        
        FileOutputStream(file).use { fileOut ->
            ZipOutputStream(fileOut).use { zipOut ->
                // Add metadata.json file
                zipOut.putNextEntry(ZipEntry("metadata.json"))
                zipOut.write(exportData.metadata.toByteArray())
                zipOut.closeEntry()
                
                // Add journals.json file
                zipOut.putNextEntry(ZipEntry("journals.json"))
                zipOut.write(exportData.journals.toByteArray())
                zipOut.closeEntry()
                
                // Add notes.json file
                zipOut.putNextEntry(ZipEntry("notes.json"))
                zipOut.write(exportData.notes.toByteArray())
                zipOut.closeEntry()
                
                // Add drafts.json file
                zipOut.putNextEntry(ZipEntry("drafts.json"))
                zipOut.write(exportData.drafts.toByteArray())
                zipOut.closeEntry()
                
                // Add media files
                // For each media file, we need to copy from the source URI to the ZIP
                exportData.mediaFiles.forEach { mediaFile ->
                    try {
                        zipOut.putNextEntry(ZipEntry(mediaFile.exportPath))
                        
                        // Get the actual media content from the source URI
                        val sourceUri = mediaFile.sourceUri.toUri()
                        context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                            inputStream.copyTo(zipOut)
                        }
                        
                        zipOut.closeEntry()
                    } catch (e: Exception) {
                        Napier.e("Failed to add media file to ZIP: ${mediaFile.sourceUri}", e)
                        // Continue with the rest of the files
                    }
                }
            }
        }
        
        return file.absolutePath
    }
}