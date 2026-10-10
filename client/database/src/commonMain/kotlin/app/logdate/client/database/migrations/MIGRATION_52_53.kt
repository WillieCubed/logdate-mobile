@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Adds marked moments and the watch delivery ledger after the journal merge table of version 52. */
val MIGRATION_52_53 =
    object : Migration(52, 53) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """CREATE TABLE IF NOT EXISTS `marked_moments` (
                    `id` TEXT NOT NULL,
                    `capturedAt` INTEGER NOT NULL,
                    `timeZoneId` TEXT NOT NULL,
                    `latitude` REAL,
                    `longitude` REAL,
                    `locationAccuracy` REAL,
                    `heartRateBpm` INTEGER,
                    `state` TEXT NOT NULL,
                    `caption` TEXT NOT NULL,
                    `resolvedNoteIds` TEXT NOT NULL,
                    `originDeviceId` TEXT NOT NULL,
                    `lastUpdated` INTEGER NOT NULL,
                    `syncVersion` INTEGER NOT NULL,
                    `deletedAt` INTEGER,
                    PRIMARY KEY(`id`)
                )""",
            )
            connection.execSQL(
                """CREATE TABLE IF NOT EXISTS `peer_delivery` (
                    `peerScope` TEXT NOT NULL,
                    `direction` TEXT NOT NULL,
                    `entityType` TEXT NOT NULL,
                    `entityId` TEXT NOT NULL,
                    `contentVersion` INTEGER NOT NULL,
                    `state` TEXT NOT NULL,
                    `attempts` INTEGER NOT NULL,
                    `lastAttemptAt` INTEGER,
                    `nextAttemptAt` INTEGER,
                    `lastCloudAttemptVersion` INTEGER,
                    `stateChangedAt` INTEGER NOT NULL,
                    `holdReason` TEXT,
                    PRIMARY KEY(`peerScope`, `direction`, `entityType`, `entityId`)
                )""",
            )
        }
    }
