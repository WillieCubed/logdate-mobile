package app.logdate.feature.core.watch.continuity

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class WatchHandoffRoute(
    val noteId: String,
    val target: String = "edit",
) : NavKey
