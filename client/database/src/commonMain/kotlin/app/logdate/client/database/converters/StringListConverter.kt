package app.logdate.client.database.converters

import androidx.room.TypeConverter

class StringListConverter {
    @TypeConverter
    fun fromString(value: String?): List<String> =
        value
            ?.split(SEPARATOR)
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()

    @TypeConverter
    fun toString(values: List<String>?): String? =
        values
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.takeIf(List<String>::isNotEmpty)
            ?.joinToString(SEPARATOR)

    private companion object {
        const val SEPARATOR = "\u001F"
    }
}
