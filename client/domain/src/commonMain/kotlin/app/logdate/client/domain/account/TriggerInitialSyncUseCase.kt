package app.logdate.client.domain.account

import app.logdate.client.sync.SyncManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the first sync right after a user signs in or creates a cloud account, so the "Account
 * ready" screen only appears once we've actually round-tripped with the server.
 *
 * Without this, [SyncManager.sync] enqueues a periodic worker that may not fire for up to 15
 * minutes — the user sees an empty app and no indication the cloud is working. Running a blocking
 * `fullSync` with a short timeout keeps onboarding responsive: if the network is flaky we surface
 * the failure instead of lying about success.
 */
class TriggerInitialSyncUseCase(
    private val syncManager: SyncManager,
    private val timeout: Duration = DEFAULT_TIMEOUT,
) {
    sealed class Result {
        object Success : Result()

        /**
         * Sync returned a non-success result (server error, auth failure, etc.). The account was
         * created successfully; the caller should surface this so the banner can prompt a retry.
         */
        data class Partial(
            val uploadedItems: Int,
            val downloadedItems: Int,
            val errorMessages: List<String>,
        ) : Result()

        /** Sync didn't complete within [timeout]. Local-to-remote backlog may still upload later. */
        object TimedOut : Result()

        /** An unexpected throwable escaped from sync. */
        data class Error(
            val message: String,
        ) : Result()
    }

    suspend operator fun invoke(): Result =
        try {
            withTimeout(timeout) {
                val result = syncManager.fullSync()
                // Kick the scheduler so background periodic sync is armed for subsequent runs.
                syncManager.sync(startNow = false)

                if (result.success && result.errors.isEmpty()) {
                    Result.Success
                } else {
                    Napier.w(
                        "Initial sync finished with issues: success=${result.success}, " +
                            "errors=${result.errors.map { it.type.name + ":" + it.message }}",
                    )
                    Result.Partial(
                        uploadedItems = result.uploadedItems,
                        downloadedItems = result.downloadedItems,
                        errorMessages = result.errors.map { it.message },
                    )
                }
            }
        } catch (_: TimeoutCancellationException) {
            Napier.w("Initial sync timed out after $timeout; background worker will retry")
            Result.TimedOut
        } catch (e: Exception) {
            Napier.e("Initial sync failed unexpectedly", e)
            Result.Error(e.message ?: "Unknown sync failure")
        }

    companion object {
        // Keep this short enough that a slow network never turns the "Signing in…" screen into a
        // frozen-app perception, but long enough that a one-round-trip sync over a normal mobile
        // connection finishes before the deadline. Tuning this up is a UX regression.
        val DEFAULT_TIMEOUT: Duration = 10.seconds
    }
}
