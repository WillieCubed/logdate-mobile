package app.logdate.feature.core.settings.ui.watch.v2

import app.logdate.feature.core.settings.ui.watch.WatchConnectionManager
import app.logdate.feature.core.settings.ui.watch.WatchConnectionState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeoutOrNull

sealed interface WatchSetupState {
    data object ConfirmingOnPhone : WatchSetupState

    data object OpeningPlayOnWatch : WatchSetupState

    data object WaitingForInstall : WatchSetupState

    data object SettingUp : WatchSetupState

    data object Ready : WatchSetupState

    data object Failed : WatchSetupState
}

interface WatchSetupCoordinator {
    fun connection(): Flow<WatchConnectionState>

    fun addToWatch(): Flow<WatchSetupState>

    suspend fun isReady(connection: WatchConnectionState): Boolean = false
}

class DefaultWatchSetupCoordinator(
    private val manager: WatchConnectionManager,
    private val verifiedReady: suspend (WatchConnectionState) -> Boolean,
) : WatchSetupCoordinator {
    override fun connection(): Flow<WatchConnectionState> = manager.observeConnectionState()

    override suspend fun isReady(connection: WatchConnectionState): Boolean = verifiedReady(connection)

    override fun addToWatch(): Flow<WatchSetupState> =
        flow {
            try {
                val ready =
                    withTimeoutOrNull(120_000) {
                        var current = connection().first { it != WatchConnectionState.Loading }
                        var targetNode = current.nodeId
                        manager.prepareSetup(targetNode)
                        if (current is WatchConnectionState.NeedsAssociation || current == WatchConnectionState.AssociationPending) {
                            emit(WatchSetupState.ConfirmingOnPhone)
                            if (current is WatchConnectionState.NeedsAssociation) manager.beginAssociation()
                            current =
                                connection().first {
                                    (targetNode == null || it.nodeId == targetNode) &&
                                        (
                                            it is WatchConnectionState.AppNotInstalled ||
                                                it is WatchConnectionState.Connected ||
                                                it is WatchConnectionState.OutOfRange
                                        )
                                }
                        }
                        if (targetNode == null) targetNode = current.nodeId
                        if (current is WatchConnectionState.AppNotInstalled) {
                            emit(WatchSetupState.OpeningPlayOnWatch)
                            if (manager.installAppOnWatch(current.nodeId) !=
                                app.logdate.feature.core.settings.ui.watch.WatchRequestResult.Sent
                            ) {
                                return@withTimeoutOrNull false
                            }
                            emit(WatchSetupState.WaitingForInstall)
                            current =
                                connection().first {
                                    (targetNode == null || it.nodeId == targetNode) &&
                                        (it is WatchConnectionState.Connected || it is WatchConnectionState.OutOfRange)
                                }
                        }
                        if (current !is WatchConnectionState.Connected &&
                            current !is WatchConnectionState.OutOfRange
                        ) {
                            return@withTimeoutOrNull false
                        }
                        if (!verifiedReady(current)) {
                            emit(WatchSetupState.SettingUp)
                            while (!verifiedReady(current)) delay(500)
                        }
                        true
                    }
                emit(if (ready == true) WatchSetupState.Ready else WatchSetupState.Failed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Napier.e("Could not set up LogDate on the watch", failure)
                emit(WatchSetupState.Failed)
            }
        }
}

object NoWatchSetupCoordinator : WatchSetupCoordinator {
    override fun connection(): Flow<WatchConnectionState> = flowOf(WatchConnectionState.NoPairedWatch)

    override fun addToWatch(): Flow<WatchSetupState> = flowOf(WatchSetupState.Failed)
}
