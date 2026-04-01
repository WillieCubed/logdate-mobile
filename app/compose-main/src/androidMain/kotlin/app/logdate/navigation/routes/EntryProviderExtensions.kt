package app.logdate.navigation.routes

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import kotlin.reflect.KClass

private const val ROUTE_CLASS_METADATA_KEY = "routeClass"
internal typealias RouteMetadata = Map<String, Any>

internal fun <T : Any> NavEntry<T>.routeClass(): KClass<out T>? {
    @Suppress("UNCHECKED_CAST")
    return metadata[ROUTE_CLASS_METADATA_KEY] as? KClass<out T>
}

internal fun sceneRouteClass(scene: Scene<*>?): KClass<out NavKey>? {
    val entry = scene?.entries?.lastOrNull() ?: return null

    @Suppress("UNCHECKED_CAST")
    return entry.metadata[ROUTE_CLASS_METADATA_KEY] as? KClass<out NavKey>
}

internal inline fun <reified T : NavKey> EntryProviderScope<NavKey>.routeEntry(
    metadata: RouteMetadata = emptyMap(),
    noinline content: @Composable (T) -> Unit,
) {
    entry<T>(
        metadata = metadata + mapOf(ROUTE_CLASS_METADATA_KEY to T::class),
    ) { route ->
        content(route)
    }
}
