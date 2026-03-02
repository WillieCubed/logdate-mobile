package app.logdate.client.database.converters

import androidx.room.TypeConverter
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
object UuidConverter {
    @TypeConverter
    fun fromUuid(value: Uuid): String = value.toString()

    @TypeConverter
    fun toUuid(value: String): Uuid = Uuid.parse(value)
}
