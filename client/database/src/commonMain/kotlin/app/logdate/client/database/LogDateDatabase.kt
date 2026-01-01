package app.logdate.client.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.logdate.client.database.converters.DurationConverter
import app.logdate.client.database.converters.LocationDataConverter
import app.logdate.client.database.converters.MediaDimensionsConverter
import app.logdate.client.database.converters.TimestampConverter
import app.logdate.client.database.converters.UuidConverter
import app.logdate.client.database.dao.ImageNoteDao
import app.logdate.client.database.dao.JournalDao
import app.logdate.client.database.dao.JournalNotesDao
import app.logdate.client.database.dao.LocationHistoryDao
import app.logdate.client.database.dao.SearchDao
import app.logdate.client.database.dao.StorageMetadataDao
import app.logdate.client.database.dao.TextNoteDao
import app.logdate.client.database.dao.TranscriptionDao
import app.logdate.client.database.dao.UserDevicesDao
import app.logdate.client.database.dao.UserMediaDao
import app.logdate.client.database.dao.VideoNoteDao
import app.logdate.client.database.dao.AudioNoteDao
import app.logdate.client.database.dao.journals.JournalContentDao
import app.logdate.client.database.dao.media.IndexedMediaDao
import app.logdate.client.database.dao.rewind.CachedRewindDao
import app.logdate.client.database.dao.rewind.RewindGenerationRequestDao
import app.logdate.client.database.dao.sync.SyncMetadataDao
import app.logdate.client.database.entities.ImageNoteEntity
import app.logdate.client.database.entities.JournalEntity
import app.logdate.client.database.entities.JournalNoteCrossRef
import app.logdate.client.database.entities.LocationLogEntity
import app.logdate.client.database.entities.StorageMetadataEntity
import app.logdate.client.database.entities.TextNoteEntity
import app.logdate.client.database.entities.TranscriptionEntity
import app.logdate.client.database.entities.UserDeviceEntity
import app.logdate.client.database.entities.VideoNoteEntity
import app.logdate.client.database.entities.VoiceNoteEntity
import app.logdate.client.database.entities.journals.JournalContentEntityLink
import app.logdate.client.database.entities.media.IndexedImageEntity
import app.logdate.client.database.entities.media.IndexedVideoEntity
import app.logdate.client.database.entities.media.MediaImageEntity
import app.logdate.client.database.entities.rewind.RewindEntity
import app.logdate.client.database.entities.rewind.RewindGenerationRequestEntity
import app.logdate.client.database.entities.rewind.RewindImageContentEntity
import app.logdate.client.database.entities.rewind.RewindTextContentEntity
import app.logdate.client.database.entities.rewind.RewindVideoContentEntity
import app.logdate.client.database.entities.sync.PendingUploadEntity
import app.logdate.client.database.entities.sync.SyncCursorEntity
import app.logdate.client.database.migrations.MIGRATION_10_11
import app.logdate.client.database.migrations.MIGRATION_11_12
import app.logdate.client.database.migrations.MIGRATION_12_13
import app.logdate.client.database.migrations.MIGRATION_13_14
import app.logdate.client.database.migrations.MIGRATION_14_15
import app.logdate.client.database.migrations.MIGRATION_15_16
import app.logdate.client.database.migrations.MIGRATION_16_17
import app.logdate.client.database.migrations.MIGRATION_17_18
import app.logdate.client.database.migrations.MIGRATION_18_19
import app.logdate.client.database.migrations.MIGRATION_19_20
import app.logdate.client.database.migrations.MIGRATION_1_2
import app.logdate.client.database.migrations.MIGRATION_20_21
import app.logdate.client.database.migrations.MIGRATION_2_3
import app.logdate.client.database.migrations.MIGRATION_3_4
import app.logdate.client.database.migrations.MIGRATION_4_5
import app.logdate.client.database.migrations.MIGRATION_5_6
import app.logdate.client.database.migrations.MIGRATION_6_7
import app.logdate.client.database.migrations.MIGRATION_7_8
import app.logdate.client.database.migrations.MIGRATION_8_9
import app.logdate.client.database.migrations.MIGRATION_9_10
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * The main on-device database for a LogDate client.
 *
 * This database is responsible for storing all data that is not ephemeral, such as projects and
 * experiments. It is built on top of Room, a SQLite database library, and is designed to be used
 * with Kotlin Multiplatform on JVM, Android, and native iOS build targets.
 *
 * @see [getRoomDatabase]
 */
@Database(
    entities = [
        TextNoteEntity::class,
        ImageNoteEntity::class,
        VideoNoteEntity::class,
        VoiceNoteEntity::class,
        JournalEntity::class,
        JournalNoteCrossRef::class,
        LocationLogEntity::class,
        StorageMetadataEntity::class,
        UserDeviceEntity::class,
        MediaImageEntity::class,
        JournalContentEntityLink::class,
        // Rewind entities
        RewindEntity::class,
        RewindTextContentEntity::class,
        RewindImageContentEntity::class,
        RewindVideoContentEntity::class,
        RewindGenerationRequestEntity::class,
        // Indexed media entities
        IndexedImageEntity::class,
        IndexedVideoEntity::class,
        // Sync metadata entities
        SyncCursorEntity::class,
        PendingUploadEntity::class,
        // Others
        TranscriptionEntity::class,
    ],
    version = 21, // Added sync metadata tables
    exportSchema = true,
    autoMigrations = [
    ],
)
@TypeConverters(
    TimestampConverter::class,
    UuidConverter::class,
    DurationConverter::class,
    MediaDimensionsConverter::class,
    LocationDataConverter::class,
)
@ConstructedBy(LogDateDatabaseConstructor::class)
abstract class LogDateDatabase : RoomDatabase() {
    abstract fun textNoteDao(): TextNoteDao
    abstract fun imageNoteDao(): ImageNoteDao
    abstract fun videoNoteDao(): VideoNoteDao
    abstract fun voiceNoteDao(): AudioNoteDao
    abstract fun journalDao(): JournalDao
    abstract fun journalNotesDao(): JournalNotesDao
    abstract fun journalContentDao(): JournalContentDao
    abstract fun rewindDao(): CachedRewindDao
    abstract fun rewindGenerationRequestDao(): RewindGenerationRequestDao
    abstract fun locationHistoryDao(): LocationHistoryDao
    abstract fun storageMetadataDao(): StorageMetadataDao
    abstract fun userDevicesDao(): UserDevicesDao
    abstract fun userMediaDao(): UserMediaDao
    abstract fun indexedMediaDao(): IndexedMediaDao
    abstract fun transcriptionDao(): TranscriptionDao
    abstract fun searchDao(): SearchDao
    abstract fun syncMetadataDao(): SyncMetadataDao
}

/**
 * The name of the [LogDateDatabase] file.
 */
internal const val DATABASE_NAME = "logdate"

/**
 * Creates a new [LogDateDatabase].
 *
 * This is the main function to use when a client needs a [LogDateDatabase] and must only be
 * called once per application lifecycle. This depends on a [RoomDatabase.Builder] to be passed in,
 * which can be found in the platform-specific source set. This builder is responsible for
 * applying all necessary configurations to the database, including database migrations.
 *
 * This creates a SQLite database that uses the bundled SQLite driver and runs all queries on the
 * IO dispatcher.
 *
 * @param builder The [RoomDatabase.Builder] to use to create the database.
 * @param driver The SQLite driver to use for the database. Defaults to [BundledSQLiteDriver].
 * @param dispatcher The dispatcher to use for all database queries. Defaults to [Dispatchers.IO].
 * @param destroyTablesOnDowngrade Whether to destructively recreate database tables if the
 * database version is downgraded. True by default.
 *
 * @return A new [LogDateDatabase] instance.
 */
fun getRoomDatabase(
    builder: RoomDatabase.Builder<LogDateDatabase>,
    driver: SQLiteDriver = BundledSQLiteDriver(),
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    destroyTablesOnUpgrade: Boolean = false,
    destroyTablesOnDowngrade: Boolean = true,
): LogDateDatabase = builder
    .addMigrations(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17,
        MIGRATION_17_18,
        MIGRATION_18_19,
        MIGRATION_19_20,
        MIGRATION_20_21,
    )
    .fallbackToDestructiveMigration(destroyTablesOnUpgrade)
    .fallbackToDestructiveMigrationOnDowngrade(destroyTablesOnDowngrade)
    .setDriver(driver)
    .setQueryCoroutineContext(dispatcher)
    .build()