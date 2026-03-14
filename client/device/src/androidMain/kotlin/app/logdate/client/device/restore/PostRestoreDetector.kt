package app.logdate.client.device.restore

import android.content.Context
import io.github.aakira.napier.Napier
import java.io.File

/**
 * Detects whether the app is launching after a backup restore and differentiates
 * between device-to-device (D2D) transfers and cloud restores.
 *
 * Uses a sentinel file in [Context.getNoBackupFilesDir] which is excluded from all
 * backup/transfer mechanisms. When the sentinel is absent but the user is onboarded,
 * a restore has occurred.
 */
class PostRestoreDetector(
    private val context: Context,
) {
    /**
     * Detects the current restore state.
     *
     * @param isOnboarded Whether the user has completed onboarding
     * @return The detected restore type
     */
    fun detect(isOnboarded: Boolean): PostRestoreType {
        val sentinelFile = File(context.noBackupFilesDir, SENTINEL_FILENAME)
        if (sentinelFile.exists()) return PostRestoreType.NONE
        if (!isOnboarded) return PostRestoreType.NONE

        // Onboarded but no sentinel → this device didn't perform the original onboarding.
        // Check the database to distinguish D2D from cloud restore.
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        return if (dbFile.exists() && dbFile.length() > 0) {
            Napier.i("Post-restore detected: device-to-device transfer (database present)")
            PostRestoreType.DEVICE_TRANSFER
        } else {
            Napier.i("Post-restore detected: cloud restore (database absent or empty)")
            PostRestoreType.CLOUD_RESTORE
        }
    }

    /**
     * Writes the sentinel file to mark this device as initialized.
     * Call after onboarding completes or after a restore state has been handled.
     */
    fun markDeviceInitialized() {
        val sentinelFile = File(context.noBackupFilesDir, SENTINEL_FILENAME)
        sentinelFile.parentFile?.mkdirs()
        if (!sentinelFile.exists()) {
            sentinelFile.createNewFile()
            Napier.d("Device initialization sentinel written")
        }
    }

    companion object {
        private const val SENTINEL_FILENAME = ".logdate_device_initialized"
        private const val DATABASE_NAME = "logdate"
    }
}

/**
 * The type of restore detected on app startup.
 */
enum class PostRestoreType {
    /** No restore detected. Normal app launch. */
    NONE,

    /** Device-to-device transfer. Database and preferences are present. App should work normally. */
    DEVICE_TRANSFER,

    /** Cloud restore. Database is absent or empty. Preferences may be partially restored. */
    CLOUD_RESTORE,
}
