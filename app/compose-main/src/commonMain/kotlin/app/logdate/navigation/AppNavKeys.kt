@file:OptIn(ExperimentalSerializationApi::class)

package app.logdate.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Central registry of every typed navigation destination in the app.
 *
 * Compose Multiplatform's Navigation 3 layer cannot use reflection-based `NavKey`
 * serialization on iOS / web — every concrete `NavKey` subtype must be registered with the
 * polymorphic serializer below. Add new routes here as they migrate from the legacy
 * `androidx.navigation.compose` graph (`LogDateNavHost.kt`) to the multiplatform Nav3
 * graph.
 *
 * Status: scaffolding. The first iteration is intentionally empty. Each migrated route
 * will land here as a single line in the `polymorphic` block, e.g.
 *
 * ```kotlin
 * polymorphic(NavKey::class) {
 *     subclass(SettingsRoute::class, SettingsRoute.serializer())
 *     subclass(HomeRoute::class, HomeRoute.serializer())
 *     // ...
 * }
 * ```
 *
 * Pair this with `rememberNavBackStack(appNavSavedStateConfiguration, startKey)` in the
 * root `NavDisplay` composable.
 */
val appNavSavedStateConfiguration: SavedStateConfiguration =
    SavedStateConfiguration {
        serializersModule =
            SerializersModule {
                polymorphic(NavKey::class) {
                    // Migrated routes register their serializers here.
                    // First migration target: SettingsRoute / DevicesRoute / AccountSettingsRoute /
                    // PrivacySettingsRoute and the rest of `SettingsNavGraph.kt`'s @Serializable data
                    // objects/classes — they already exist in commonMain, just need `: NavKey`
                    // and a `subclass(...)` line here.
                }
            }
    }
