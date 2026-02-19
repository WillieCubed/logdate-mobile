package app.logdate.client.database

import androidx.room.execSQL
import androidx.room.useWriterConnection

/**
 * Clears all LogDate database tables using Room's APIs.
 */
actual suspend fun LogDateDatabase.clearAllLogDateTables() {
    useWriterConnection { connection ->
        val tables = mutableListOf<String>()
        connection.usePrepared(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
        ) { statement ->
            while (statement.step()) {
                tables.add(statement.getText(0))
            }
        }

        tables.forEach { table ->
            val escaped = table.replace("\"", "\"\"")
            connection.execSQL("DELETE FROM \"$escaped\"")
        }
    }
}
