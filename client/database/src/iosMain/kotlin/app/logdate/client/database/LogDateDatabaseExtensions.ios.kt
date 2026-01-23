package app.logdate.client.database

import androidx.room.execSQL
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/**
 * Clears all LogDate database tables using Room's iOS connection APIs.
 */
actual suspend fun LogDateDatabase.clearAllLogDateTables() {
    useWriterConnection { connection ->
        connection.immediateTransaction {
            val tableNames = mutableListOf<String>()

            usePrepared(
                "SELECT name FROM sqlite_master " +
                    "WHERE type='table' " +
                    "AND name NOT LIKE 'sqlite_%' " +
                    "AND name NOT IN ('room_master_table', 'android_metadata')"
            ) { statement ->
                while (statement.step()) {
                    tableNames.add(statement.getText(0))
                }
            }

            execSQL("PRAGMA foreign_keys = OFF")
            tableNames.forEach { tableName ->
                execSQL("DELETE FROM \"$tableName\"")
            }
            execSQL("PRAGMA foreign_keys = ON")
        }
    }
}
