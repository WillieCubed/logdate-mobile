package app.logdate.client.feature.widgets

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import app.logdate.client.repository.user.UserStateRepository
import kotlinx.coroutines.flow.first

/** Discards setup content when its activity leaves the foreground. */
abstract class SecureWidgetConfigActivity : FragmentActivity() {
    private var contentVisible = false
    private var hasStopped = false

    protected suspend fun unlockSetup(userStateRepository: UserStateRepository): Boolean {
        val allowed = unlockWidgetContent(userStateRepository)
        if (!allowed || hasStopped) {
            finish()
            return false
        }
        val started = lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) || it == Lifecycle.State.DESTROYED }
        if (started == Lifecycle.State.DESTROYED || hasStopped) {
            finish()
            return false
        }
        contentVisible = true
        return true
    }

    override fun onStop() {
        super.onStop()
        hasStopped = true
        if (contentVisible && !isChangingConfigurations) finish()
    }
}
