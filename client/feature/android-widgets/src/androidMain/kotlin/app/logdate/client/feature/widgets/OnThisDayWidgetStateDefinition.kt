package app.logdate.client.feature.widgets

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.glance.state.GlanceStateDefinition
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Glance state definition that persists [OnThisDayWidgetState] as JSON.
 *
 * DataStore instances are cached per fileKey to avoid creating duplicate
 * instances for the same backing file (a known Glance/DataStore pitfall).
 */
internal object OnThisDayWidgetStateDefinition : GlanceStateDefinition<OnThisDayWidgetState> {
    private const val FILE_NAME = "on_this_day_widget_state"

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val dataStoreCache = ConcurrentHashMap<String, DataStore<OnThisDayWidgetState>>()

    override suspend fun getDataStore(
        context: Context,
        fileKey: String,
    ): DataStore<OnThisDayWidgetState> =
        dataStoreCache.getOrPut(fileKey) {
            DataStoreFactory.create(
                serializer = OnThisDayWidgetStateSerializer,
            ) {
                File(context.filesDir, "glance/$FILE_NAME-$fileKey.json")
            }
        }

    override fun getLocation(
        context: Context,
        fileKey: String,
    ): File = File(context.filesDir, "glance/$FILE_NAME-$fileKey.json")

    private object OnThisDayWidgetStateSerializer : Serializer<OnThisDayWidgetState> {
        override val defaultValue: OnThisDayWidgetState = OnThisDayWidgetState.Loading

        override suspend fun readFrom(input: InputStream): OnThisDayWidgetState =
            try {
                json.decodeFromString<OnThisDayWidgetState>(input.readBytes().decodeToString())
            } catch (_: Exception) {
                OnThisDayWidgetState.Loading
            }

        override suspend fun writeTo(
            t: OnThisDayWidgetState,
            output: OutputStream,
        ) {
            output.write(json.encodeToString(OnThisDayWidgetState.serializer(), t).encodeToByteArray())
        }
    }
}
