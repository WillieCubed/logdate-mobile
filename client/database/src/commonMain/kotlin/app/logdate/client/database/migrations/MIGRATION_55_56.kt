@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Adds the personal-space record table: for each record this device wrote to or applied from an
 * account's personal space, its type, its own key, its sealed key, and the content identifier and
 * sequence number of the version last seen. No existing table changes.
 */
val MIGRATION_55_56 =
    object : Migration(55, 56) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """CREATE TABLE IF NOT EXISTS `sync_space_records` (
                    `ownerId` TEXT NOT NULL,
                    `serverOrigin` TEXT NOT NULL,
                    `collection` TEXT NOT NULL,
                    `innerRkey` TEXT NOT NULL,
                    `sealedRkey` TEXT NOT NULL,
                    `cid` TEXT NOT NULL,
                    `seq` INTEGER NOT NULL,
                    PRIMARY KEY(`ownerId`, `serverOrigin`, `collection`, `innerRkey`)
                )""",
            )
            connection.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_space_records_ownerId_serverOrigin_sealedRkey` " +
                    "ON `sync_space_records` (`ownerId`, `serverOrigin`, `sealedRkey`)",
            )
        }
    }
