package app.logdate.feature.core.sync.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Detail screen listing sync writes that exhausted their retry budget.
 */
@Serializable
data object SyncIssuesRoute : NavKey
