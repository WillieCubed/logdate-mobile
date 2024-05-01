package app.logdate.core.updater

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

// TODO: Move to debug build variant, remove from release
/**
 * A fake implementation of [AppUpdater] for testing purposes.
 */
class DemoAppUpdater @Inject constructor() : AppUpdater {
    override val updateIsAvailable: Flow<Boolean>
        get() = flow {
            emit(false)
        }

    override suspend fun checkForUpdates() {
        TODO("Not yet implemented")
    }

    override suspend fun startUpdate() {
        TODO("Not yet implemented")
    }
}