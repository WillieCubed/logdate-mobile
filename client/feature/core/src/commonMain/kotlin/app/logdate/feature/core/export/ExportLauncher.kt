package app.logdate.feature.core.export

/**
 * Interface for launching data export operations.
 */
interface ExportLauncher {
    /**
     * Starts the data export process.
     */
    fun startExport()
    
    /**
     * Cancels any ongoing export operation.
     */
    fun cancelExport()
    
    /**
     * Sets a callback to be notified of export completion with a path.
     * 
     * @param callback Function that receives the export path or null if export failed/was cancelled
     */
    fun setExportCompletionCallback(callback: (String?) -> Unit)
}