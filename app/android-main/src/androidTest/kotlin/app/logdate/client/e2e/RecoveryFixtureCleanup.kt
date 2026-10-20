package app.logdate.client.e2e

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Restore every touched setting even when another cleanup operation fails. */
internal suspend fun restoreFixtureState(vararg actions: suspend () -> Unit) =
    withContext(NonCancellable) {
        var failure: Throwable? = null
        actions.forEach { action ->
            try {
                action()
            } catch (error: Throwable) {
                if (failure == null) {
                    failure = error
                } else if (failure !== error) {
                    failure.addSuppressed(error)
                }
            }
        }
        failure?.let { throw it }
    }
