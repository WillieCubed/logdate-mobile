package app.logdate.core.assist

import javax.inject.Inject

/**
 * A utility for providing available screen actions to the Android assistant.
 */
class AndroidAssistantActionsProvider @Inject constructor() : AssistantActionsProvider {
    override val supportedActions: List<AppAction>
        get() = AppAction.entries
}