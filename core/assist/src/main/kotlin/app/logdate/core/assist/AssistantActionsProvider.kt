package app.logdate.core.assist

import android.app.DirectAction

interface AssistantActionsProvider {
    val supportedActions: List<AppAction>
}

enum class AppAction(
    val id: String,
) {
    CREATE_JOURNAL("create_journal"),
    VIEW_JOURNAL("view_journal"),
    TAKE_PHOTO("take_photo"),
    VIEW_PHOTO("view_photo"),
    VIEW_TIMELINE("view_timeline"),
    VIEW_TIMELINE_DETAILS("view_timeline_details"),
    SHARE_MEDIA("share_media"),
}

fun AppAction.toDirectAction(): DirectAction {
    return DirectAction.Builder(id).build()
}

internal fun DirectAction.toAppAction(): AppAction {
    return AppAction.entries.first { it.id == id }
}