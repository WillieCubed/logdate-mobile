package app.logdate.client.device.restore

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
internal data class NativePreferenceDeliveryRecord(
    val generation: String,
    val names: Set<String>,
    val delivered: Set<String>,
)

/** The caller holds the shared snapshot file lock across journal reads and writes. */
internal class NativePreferenceDeliveryJournal(
    private val file: File,
    private val generation: String,
) {
    fun read(): NativePreferenceDeliveryRecord? {
        if (!file.exists()) return null
        if (file.length() !in 1..MAX_BYTES) throw IOException("Native preference delivery record has an invalid size")
        return try {
            Json.decodeFromString<NativePreferenceDeliveryRecord>(file.readText()).also(::validate)
        } catch (failure: Exception) {
            throw IOException("Native preference delivery record is unavailable", failure)
        }
    }

    fun record(
        names: Set<String>,
        delivered: Set<String>,
    ): NativePreferenceDeliveryRecord {
        val proposed = NativePreferenceDeliveryRecord(generation, names.toSet(), delivered.toSet()).also(::validate)
        read()?.let { previous ->
            if (previous.names != proposed.names) throw IOException("Native preference delivery enrollment changed")
            return previous
        }
        val parent = requireNotNull(file.parentFile)
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Native preference delivery directory is unavailable")
        val temporary = File(parent, "${file.name}.part")
        FileOutputStream(temporary).use { output ->
            output.write(Json.encodeToString(proposed).encodeToByteArray())
            output.fd.sync()
        }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        if (read() != proposed) throw IOException("Native preference delivery decision was not retained")
        return proposed
    }

    private fun validate(record: NativePreferenceDeliveryRecord) {
        if (record.generation != generation ||
            record.names.size !in 1..16 ||
            record.names.any { !it.matches(Regex("[a-z][a-z0-9_]{0,99}")) } ||
            !record.names.containsAll(record.delivered)
        ) {
            throw IOException("Native preference delivery record does not match this delivery")
        }
    }

    private companion object {
        const val MAX_BYTES = 16_384L
    }
}
