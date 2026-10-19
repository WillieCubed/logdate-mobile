@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Stores whether a journal is a favorite. Until now the app read every journal as not favorited, so
 * the flag was never kept; every existing journal starts as not a favorite. A table that already has
 * the column is left as it is, so running the migration again is harmless.
 */
val MIGRATION_56_57 =
    object : Migration(56, 57) {
        override fun migrate(connection: SQLiteConnection) {
            if (connection.hasColumn("journals", "isFavorited")) return
            connection.execSQL("ALTER TABLE `journals` ADD COLUMN `isFavorited` INTEGER NOT NULL DEFAULT 0")
        }

        private fun SQLiteConnection.hasColumn(
            table: String,
            column: String,
        ): Boolean =
            prepare("PRAGMA table_info(`$table`)").use { columns ->
                while (columns.step()) {
                    if (columns.getText(1) == column) return true
                }
                false
            }
    }
