package app.logdate.core.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.content.Context
import android.os.ParcelFileDescriptor
import app.logdate.core.database.BackupableDatabase
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

/**
 * A backup agent that backs up and restores the logdate database on device with Android Auto Backup.
 *
 * Because Android Audio Backup only stores up to 25 MB of data, this backup agent is responsible for
 * restoring the app to the state it was in when the backup was created. This does not initially
 * restore all user data to the device, but rather the state of the app, including:
 * - User identity
 * - User settings
 *
 * After the app is restored, the app will then download the user data from the server and update the
 * app to the latest state.
 *
 * Data is restored whenever the app is installed, whether from the Play Store, during device setup (when the system installs previously installed apps), or by running adb install. The restore operation occurs after the APK is installed but before the app is available to be launched by the user.
 *
 * This agent also checks for the version of the app used for the backup and follows the appropriate
 * restore procedure for the version.
 *
 * TODO: Finish implementation
 */
class LogdateBackupAgent @Inject constructor(
    private val appDatabase: BackupableDatabase,
    private val context: Context,
) : BackupAgent() {

    companion object {
        const val MY_BACKUP_KEY_ONE = "myBackupKeyOne"
        const val MY_BACKUP_KEY_TO_IGNORE = "myBackupKeyToIgnore"
    }

    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput,
        newState: ParcelFileDescriptor
    ) {
        val dbFile = appDatabase.exportBackup(context)
        val instream = FileInputStream(oldState?.fileDescriptor)
        val dataInputStream = DataInputStream(instream)
        try {
            // Get the last modified timestamp from the state file and data file
            val stateModified = dataInputStream.readLong()
            val fileModified: Long = dbFile.lastModified()
            if (stateModified != fileModified) {
                // The file has been modified, so do a backup
                // Or the time on the device changed, so be safe and do a backup
            } else {
                // Don't back up because the file hasn't changed
                return
            }
        } catch (e: IOException) {
            // Unable to read state file... be safe and do a backup
        }

    }

    private fun processBackupKeyOne(buffer: ByteArray) {
        TODO("Not yet implemented")
    }
    @Throws(IOException::class)
    override fun onRestore(
        data: BackupDataInput,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?
    ) {
        with(data) {
            // There should be only one entity, but the safest
            // way to consume it is using a while loop
            while (readNextHeader()) {
                when(key) {
                    MY_BACKUP_KEY_ONE -> {
                        val dataBuf = ByteArray(dataSize).also {
                            readEntityData(it, 0, dataSize)
                        }
                        ByteArrayInputStream(dataBuf).also {
                            DataInputStream(it).apply {
                                // Read the player name and score from the backup data
//                                playerName = readUTF()
//                                playerScore = readInt()
                            }
                            // Record the score on the device (to a file or something)
//                            recordScore(playerName, playerScore)
                        }
                    }
                    else -> skipEntityData()
                }
            }
        }

        // Finally, write to the state blob (newState) that describes the restored data
        FileOutputStream(newState?.fileDescriptor).also {
            DataOutputStream(it).apply {
//                writeUTF(playerName)
//                writeInt(mPlayerScore)
            }
        }

        TODO("Not yet implemented")
    }
}