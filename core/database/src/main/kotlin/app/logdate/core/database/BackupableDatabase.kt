package app.logdate.core.database

import android.content.Context
import java.io.File

/**
 * A database that can be backed up and restored.
 */
interface BackupableDatabase {
    /**
     * Exports a backup of the database to a file.
     *
     * @param context An application context
     */
    fun exportBackup(context: Context): File

    /**
     * Restores the database from a backup file.
     *
     * @param context An application context
     * @param file A backup file generated by [exportBackup].
     */
    fun restoreFromFile(context: Context, file: File)
}