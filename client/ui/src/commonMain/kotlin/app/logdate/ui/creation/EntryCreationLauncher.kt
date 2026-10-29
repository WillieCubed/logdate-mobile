package app.logdate.ui.creation

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf

/** Synchronous metadata only; each window provides its own launcher. Never retains a pending route. */
class EntryCreationLauncher {
    private var current: Pair<String, EntryCreationShape>? = null

    private data class SourceGeometry(
        val owner: Any,
        val shape: EntryCreationShape,
    )

    private val sources = mutableStateMapOf<String, SourceGeometry>()
    val currentSourceId: String? get() = current?.first
    val currentSourceShape: EntryCreationShape get() = current?.second ?: EntryCreationShape.Large

    internal fun registerSourceShape(
        id: String,
        owner: Any,
        shape: EntryCreationShape,
    ) {
        sources[id] = SourceGeometry(owner, shape)
    }

    internal fun unregisterSourceShape(
        id: String,
        owner: Any,
    ) {
        if (sources[id]?.owner === owner) sources.remove(id)
    }

    internal fun sourceShape(
        id: String,
        fallback: EntryCreationShape,
    ): EntryCreationShape = sources[id]?.shape ?: fallback

    fun <T> withSource(
        sourceId: String,
        action: () -> T,
    ): T = withSource(sourceId, EntryCreationShape.Large, action)

    fun <T> withSource(
        sourceId: String,
        shape: EntryCreationShape,
        action: () -> T,
    ): T {
        val previous = current
        current = sourceId to shape
        return try {
            action()
        } finally {
            current = previous
        }
    }
}

val LocalEntryCreationLauncher = staticCompositionLocalOf<EntryCreationLauncher?> { null }

fun entryCreationKey(sourceId: String): String = "entry-creation:$sourceId"
