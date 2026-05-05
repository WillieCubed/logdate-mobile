package app.logdate.feature.core.main

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Top-level home route. The shell hosts the bottom-nav tabs (Timeline / Library / Journals
 * / Rewind / Location) inside [HomeScreen]; the route itself carries no parameters.
 */
@Serializable
data object HomeRoute : NavKey
