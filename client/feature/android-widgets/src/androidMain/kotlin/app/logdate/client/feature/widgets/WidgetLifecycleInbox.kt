package app.logdate.client.feature.widgets

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.uuid.Uuid

@Serializable
internal data class WidgetLifecycleEvent(
    val id: String,
    val receiver: String,
    val action: String,
    val ids: List<Int> = emptyList(),
    val oldIds: List<Int> = emptyList(),
    val sourceValues: Map<Int, WidgetSettingValues>? = null,
    val actionKey: String? = null,
    val sourceResolved: Boolean = true,
    val sourceGeneration: String? = null,
)

internal const val WIDGET_REFRESH_ACTION = "app.logdate.widget.REFRESH"

internal fun widgetRefreshEvent(eventId: String) = WidgetLifecycleEvent(eventId, "OnThisDayWidgetReceiver", WIDGET_REFRESH_ACTION)

@Serializable
internal data class WidgetLifecycleRecord(
    val sequence: Long,
    val event: WidgetLifecycleEvent,
    val settingsApplied: Boolean = false,
)

internal class WidgetLifecycleInbox(
    private val directory: File,
) {
    fun append(event: WidgetLifecycleEvent) =
        locked {
            val target = eventFile(event.id)
            if (target.exists()) {
                val retained = read(target).event
                require(
                    retained == event ||
                        (!event.sourceResolved && retained.copy(sourceValues = null, sourceResolved = false) == event),
                ) {
                    "An immutable widget event cannot be replaced"
                }
            } else {
                val pending = readPending()
                require(pending.size < MAX_EVENTS) { "Widget lifecycle queue is full" }
                val sequence = Math.addExact(pending.lastOrNull()?.sequence ?: 0L, 1L)
                write(target, WidgetLifecycleRecord(sequence, event))
            }
        }

    fun pending(): List<WidgetLifecycleRecord> = locked { readPending() }

    fun settingsApplied(id: String) =
        locked {
            val target = eventFile(id)
            val record = read(target)
            check(record.event.sourceResolved) { "Widget source choices have not been retained" }
            if (!record.settingsApplied) write(target, record.copy(settingsApplied = true))
        }

    fun resolveSource(
        id: String,
        values: Map<Int, WidgetSettingValues>,
    ) = locked {
        val target = eventFile(id)
        val record = read(target)
        if (record.event.sourceResolved) {
            require(record.event.sourceValues == values)
        } else {
            write(target, record.copy(event = record.event.copy(sourceValues = values, sourceResolved = true)))
        }
    }

    fun acknowledge(id: String) =
        locked {
            val target = eventFile(id)
            if (target.exists()) {
                check(read(target).settingsApplied) { "Widget settings have not been committed" }
                if (!target.delete()) throw IOException("Widget event completion remains pending")
            }
        }

    private fun eventFile(id: String): File {
        require(Uuid.parse(id).toString() == id)
        return File(directory, "$id.json").also { require(!Files.isSymbolicLink(it.toPath())) }
    }

    private fun readPending(): List<WidgetLifecycleRecord> {
        val files = directory.listFiles() ?: throw IOException("Widget inbox could not be read")
        return files.filter { it.extension == "json" }.map(::read).sortedBy { it.sequence }.also { records ->
            require(records.size <= MAX_EVENTS && records.map { it.sequence }.distinct().size == records.size)
        }
    }

    private fun read(file: File): WidgetLifecycleRecord {
        require(!Files.isSymbolicLink(file.toPath()))
        require(file.length() <= MAX_BYTES) { "Widget lifecycle event exceeds its limit" }
        return Json.decodeFromString<WidgetLifecycleRecord>(file.readText()).also { record ->
            require(record.sequence > 0 && eventFile(record.event.id) == file)
        }
    }

    private fun write(
        target: File,
        record: WidgetLifecycleRecord,
    ) {
        val bytes = Json.encodeToString(record).encodeToByteArray()
        require(bytes.size <= MAX_BYTES)
        val temporary = File(directory, "${Uuid.random()}.part")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            if (read(target) != record) throw IOException("Widget lifecycle event was not committed")
        } finally {
            temporary.delete()
        }
    }

    private fun <T> locked(block: () -> T): T =
        synchronized(processLock) {
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Widget inbox could not be created")
            RandomAccessFile(File(directory, "inbox.lock"), "rw").use { lock -> lock.channel.lock().use { block() } }
        }

    private companion object {
        val processLock = Any()
        const val MAX_EVENTS = 4096
        const val MAX_BYTES = 1_048_576
    }
}
