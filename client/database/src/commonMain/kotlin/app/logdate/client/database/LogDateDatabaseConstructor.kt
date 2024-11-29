package app.logdate.client.database

import androidx.room.RoomDatabaseConstructor

/**
 * A [RoomDatabaseConstructor] for the [LogDateDatabase].
 *
 * The Room compiler generates the `actual` implementations for this class in the platform-specific
 * source sets for JVM, Android, and native iOS.
 *
 * @see [LogDateDatabase]
 */
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object LogDateDatabaseConstructor : RoomDatabaseConstructor<LogDateDatabase> {
    override fun initialize(): LogDateDatabase
}