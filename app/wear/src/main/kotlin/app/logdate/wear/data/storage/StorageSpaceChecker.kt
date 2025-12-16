package app.logdate.wear.data.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Utility for checking available storage space on Wear OS.
 * 
 * Used to validate if there's sufficient space for audio recordings
 * before starting a recording operation.
 */
class StorageSpaceChecker(private val context: Context) {

    /**
     * Gets the available storage space in bytes.
     * 
     * @return Available storage space in bytes
     */
    suspend fun getAvailableStorageSpace(): Long = withContext(Dispatchers.IO) {
        try {
            // Try to get app-specific external storage first
            val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            if (externalDir != null && externalDir.exists()) {
                return@withContext getAvailableSpaceForPath(externalDir.absolutePath)
            }
            
            // Fall back to app cache directory
            val cacheDir = context.cacheDir
            return@withContext getAvailableSpaceForPath(cacheDir.absolutePath)
        } catch (e: Exception) {
            Napier.e("Error checking storage space", e)
            // Return a minimal value that will fail the space check to be safe
            return@withContext 0L
        }
    }

    /**
     * Gets the available space for a specific path.
     * 
     * @param path The directory path to check
     * @return Available space in bytes
     */
    private fun getAvailableSpaceForPath(path: String): Long {
        return try {
            val stats = StatFs(path)
            val availableBlocks = stats.availableBlocksLong
            val blockSize = stats.blockSizeLong
            val availableBytes = availableBlocks * blockSize
            
            Napier.d("Available space at $path: ${formatSize(availableBytes)}")
            availableBytes
        } catch (e: Exception) {
            Napier.e("Failed to get space stats for $path", e)
            0L
        }
    }
    
    /**
     * Gets the total storage capacity.
     * Useful for debugging and logging.
     * 
     * @return Total storage capacity in bytes
     */
    suspend fun getTotalStorageCapacity(): Long = withContext(Dispatchers.IO) {
        try {
            // Check app-specific directory
            val externalDir = context.getExternalFilesDir(null)
            if (externalDir != null) {
                val stats = StatFs(externalDir.absolutePath)
                val totalBlocks = stats.blockCountLong
                val blockSize = stats.blockSizeLong
                return@withContext totalBlocks * blockSize
            }
            
            // Fall back to cache directory
            val stats = StatFs(context.cacheDir.absolutePath)
            val totalBlocks = stats.blockCountLong
            val blockSize = stats.blockSizeLong
            return@withContext totalBlocks * blockSize
        } catch (e: Exception) {
            Napier.e("Error checking total storage capacity", e)
            return@withContext 0L
        }
    }
    
    /**
     * Formats a byte size into a human-readable string.
     * Used for logging and debugging.
     * 
     * @param bytes Size in bytes
     * @return Formatted string (e.g. "1.5 MB")
     */
    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${String.format("%.2f", bytes / (1024.0 * 1024.0))} MB"
            else -> "${String.format("%.2f", bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }
    
    /**
     * Estimates recording file size based on duration and bitrate.
     * 
     * @param durationSeconds Recording duration in seconds
     * @param bitRate Bit rate in bits per second (default: 128000 for decent quality AAC)
     * @return Estimated file size in bytes
     */
    fun estimateRecordingSize(durationSeconds: Int, bitRate: Int = 128000): Long {
        // Convert bit rate (bits per second) to bytes per second and multiply by duration
        return (bitRate / 8L) * durationSeconds
    }
}