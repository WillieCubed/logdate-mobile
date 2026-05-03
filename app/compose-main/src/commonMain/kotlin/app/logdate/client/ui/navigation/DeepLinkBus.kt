package app.logdate.client.ui.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.uuid.Uuid

/**
 * Process-wide bus for navigation requests originating outside the Compose tree —
 * iOS URL scheme / universal link callbacks today, push-notification taps later.
 *
 * Platform shells call [emit] with a parsed [DeepLinkAction]; the root composable subscribes to
 * [actions] and forwards each emission to the navigation controller.
 */
object DeepLinkBus {
    private val _actions = MutableSharedFlow<DeepLinkAction>(replay = 1, extraBufferCapacity = 16)
    val actions: SharedFlow<DeepLinkAction> = _actions.asSharedFlow()

    fun emit(action: DeepLinkAction) {
        _actions.tryEmit(action)
    }
}

sealed class DeepLinkAction {
    data class OpenJournal(
        val id: Uuid,
    ) : DeepLinkAction()

    data class OpenNote(
        val id: Uuid,
    ) : DeepLinkAction()

    data class OpenRewind(
        val id: Uuid,
    ) : DeepLinkAction()

    data object OpenLocationTimeline : DeepLinkAction()
}
