package app.logdate.client.feature.widgets

import android.content.SharedPreferences
import android.util.AtomicFile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import kotlin.uuid.Uuid

/** Installation-local choices cannot be replaced by a second delivery of backed-up preferences. */
internal class AndroidWidgetRestoreStateStore(
    directory: File,
    private val rebuild: () -> WidgetRestoreState,
) : WidgetRestoreStateStore {
    private val file = AtomicFile(File(directory, "widget-restore-choices.json"))

    override fun read(): WidgetRestoreState {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return WidgetRestoreState()
        return try {
            readStored()
        } catch (damaged: IllegalArgumentException) {
            val retained = rebuild()
            val original = file.baseFile
            val quarantine = File(original.parentFile, "${original.name}.damaged-${Uuid.random()}")
            if (!original.renameTo(quarantine)) throw IOException("Widget recovery metadata could not be retained", damaged)
            write(retained)
            retained
        }
    }

    private fun readStored(): WidgetRestoreState =
        file.openRead().use { input ->
            require(input.channel.size() <= MAX_BYTES) { "Widget recovery metadata exceeds its limit" }
            Json.decodeFromString<WidgetRestoreState>(input.readBytes().decodeToString()).also { state ->
                require(state.version == 1) { "Unsupported widget recovery metadata" }
                require(state.widgets.size <= MAX_WIDGETS && state.widgets.keys.all { it > 0 })
                require(state.widgets.values.all { record -> record.sourceIds.all { it > 0 } })
                require(state.retiredSourceIds.all { it > 0 })
                require(state.retiredRestoredSourceIds.orEmpty().all { it > 0 })
            }
        }

    override fun write(state: WidgetRestoreState) {
        require(state.widgets.size <= MAX_WIDGETS)
        val bytes = Json.encodeToString(state).encodeToByteArray()
        require(bytes.size <= MAX_BYTES)
        val output = file.startWrite()
        try {
            output.write(bytes)
            output.fd.sync()
            file.finishWrite(output)
            if (readStored() != state) throw IOException("Widget recovery metadata was not committed")
        } catch (failure: Throwable) {
            runCatching { file.failWrite(output) }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    private companion object {
        const val MAX_BYTES = 1_048_576
        const val MAX_WIDGETS = 4096
    }
}

internal class AndroidWidgetPreferencesStore(
    private val preferences: SharedPreferences,
) : WidgetPreferencesStore {
    override fun read(): Map<Int, WidgetSettingValues> {
        val strings = mutableMapOf<Int, MutableMap<String, String>>()
        val flags = mutableMapOf<Int, MutableMap<String, Boolean>>()
        preferences.all.forEach { (key, value) ->
            val (id, field) = widgetKey(key) ?: return@forEach
            when (value) {
                is String -> strings.getOrPut(id) { mutableMapOf() }[field] = value
                is Boolean -> flags.getOrPut(id) { mutableMapOf() }[field] = value
            }
        }
        return (strings.keys + flags.keys).associateWith { id ->
            WidgetSettingValues(strings[id].orEmpty().toMap(), flags[id].orEmpty().toMap())
        }
    }

    override fun write(values: Map<Int, WidgetSettingValues>) {
        val editor = preferences.edit()
        preferences.all.forEach { (key, value) ->
            if (widgetKey(key) != null && (value is String || value is Boolean)) editor.remove(key)
        }
        values.forEach { (id, fields) ->
            fields.strings.forEach { (key, value) -> editor.putString("$id.$key", value) }
            fields.flags.forEach { (key, value) -> editor.putBoolean("$id.$key", value) }
        }
        if (!editor.commit()) throw IOException("Widget choices remain pending in installation-local recovery state")
    }

    private fun widgetKey(key: String): Pair<Int, String>? {
        val id = key.substringBefore('.').toIntOrNull()?.takeIf { it > 0 } ?: return null
        val field = key.substringAfter('.', "").takeIf(String::isNotEmpty) ?: return null
        return id to field
    }
}
