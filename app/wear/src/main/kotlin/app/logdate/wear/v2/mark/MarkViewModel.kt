package app.logdate.wear.v2.mark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.ui.platform.LogDateHaptics
import app.logdate.wear.v2.permissions.PermissionPrompts
import app.logdate.wear.v2.permissions.WatchPermission
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** What Today says after Mark. */
enum class MarkFeedback {
    NONE,
    MARKED,
    FAILED,
}

/**
 * Mark from Today. The mark is saved at once and confirmed with a haptic and "Moment marked"; the
 * place and heart rate follow on [applicationScope], so they finish even if the screen goes away.
 * The first mark asks for the heart rate permission, once.
 */
class MarkViewModel(
    private val markMoment: MarkMomentUseCase,
    private val haptics: LogDateHaptics,
    private val permissions: PermissionPrompts,
    private val applicationScope: CoroutineScope,
) : ViewModel() {
    private val mutableFeedback = MutableStateFlow(MarkFeedback.NONE)
    private val permissionRequestChannel = Channel<WatchPermission>(Channel.BUFFERED)
    private var waitingForHeartRate: MarkedMomentEntity? = null

    val feedback: StateFlow<MarkFeedback> = mutableFeedback.asStateFlow()

    /** Permissions the screen should ask for now. */
    val permissionRequests: Flow<WatchPermission> = permissionRequestChannel.receiveAsFlow()

    fun mark() {
        viewModelScope.launch {
            val mark = saveMark() ?: return@launch
            haptics.momentMarked()
            mutableFeedback.value = MarkFeedback.MARKED
            if (permissions.takeOptionalPrompt(WatchPermission.HEART_RATE)) {
                waitingForHeartRate = mark
                permissionRequestChannel.trySend(WatchPermission.HEART_RATE)
                return@launch
            }
            enrichLater(mark)
        }
    }

    /** The heart rate request was answered; the mark waiting for it is filled in either way. */
    fun onPermissionResult(permission: WatchPermission) {
        if (permission != WatchPermission.HEART_RATE) return
        val mark = waitingForHeartRate ?: return
        waitingForHeartRate = null
        enrichLater(mark)
    }

    fun dismissFeedback() {
        mutableFeedback.value = MarkFeedback.NONE
    }

    private suspend fun saveMark(): MarkedMomentEntity? =
        try {
            markMoment.mark()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Couldn't mark the moment", e)
            haptics.actionRejected()
            mutableFeedback.value = MarkFeedback.FAILED
            null
        }

    private fun enrichLater(mark: MarkedMomentEntity) {
        applicationScope.launch {
            try {
                markMoment.enrich(mark)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Couldn't add the place or heart rate to mark ${mark.id}", e)
            }
        }
    }
}
