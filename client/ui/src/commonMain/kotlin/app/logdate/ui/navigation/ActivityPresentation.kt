package app.logdate.ui.navigation

import androidx.navigation3.runtime.NavEntry

sealed interface ActivityPresentation {
    data object Replacement : ActivityPresentation

    data object Home : ActivityPresentation

    data class Collection(
        val context: String,
    ) : ActivityPresentation

    data class Detail(
        val context: String,
        val childContext: String? = null,
    ) : ActivityPresentation

    data object SettingsRoot : ActivityPresentation

    data class SettingsDetail(
        val section: String,
    ) : ActivityPresentation
}

fun ActivityPresentation.asMetadata(): Map<String, Any> = mapOf("logdate.activityPresentation" to this)

fun NavEntry<*>.activityPresentation(): ActivityPresentation =
    metadata["logdate.activityPresentation"] as? ActivityPresentation ?: ActivityPresentation.Replacement
