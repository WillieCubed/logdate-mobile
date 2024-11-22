package app.logdate.core.database.util

import androidx.room.TypeConverter
import kotlinx.datetime.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal object InstantConverters {
    @TypeConverter
    fun longToInstant(value: Long?): Instant? =
        value?.let(Instant::fromEpochMilliseconds)

    @TypeConverter
    fun instantToLong(instant: Instant?): Long? =
        instant?.toEpochMilliseconds()
}

@OptIn(ExperimentalUuidApi::class)
internal object UuidConverters {
    @TypeConverter
    fun uuidToString(value: Uuid): String =
        value.toString()

    @TypeConverter
    fun stringToUuid(uuid: String): Uuid =
        Uuid.parse(uuid)
}