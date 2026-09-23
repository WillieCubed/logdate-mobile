package app.logdate.client.domain.account

import app.logdate.client.datastore.LogdatePreferencesDataSource
import io.github.aakira.napier.Napier

/**
 * Persists "we have already backfilled local data into the sync queue for this account ID."
 *
 * Pulled out as a small interface so the use case can be tested without an in-memory DataStore.
 * Production binding adapts a [LogdatePreferencesDataSource]; tests substitute a trivial map.
 */
interface BackfilledAccountTracker {
    suspend fun getBackfilledAccountIds(): Set<String>

    suspend fun markAccountBackfilled(accountId: String)
}

class PreferencesBackfilledAccountTracker(
    private val preferences: LogdatePreferencesDataSource,
) : BackfilledAccountTracker {
    override suspend fun getBackfilledAccountIds(): Set<String> = preferences.getBackfilledAccountIds()

    override suspend fun markAccountBackfilled(accountId: String) = preferences.markAccountBackfilled(accountId)
}

/**
 * One-shot pass that walks the local database and re-enqueues every record into the sync
 * outbox. Runs the first time we see a session for a given account ID.
 *
 * Why this exists: writes performed while signed-out no longer enqueue (see
 * `DatabaseSyncMetadataService` — accountless writes succeed locally but produce no sync
 * signal). Without a backfill, signing in for the first time would find an empty queue and
 * leave any pre-account journaling stuck on-device. This walks the DB once per account, so
 * the user's mental model holds: "I created an account → my data backed up."
 *
 * Idempotency is per-account-ID. Re-signing into the same account is a no-op; signing into a
 * different account triggers a fresh pass (matches "this is a new account, sync everything I
 * have"). Cleared by sign-out only insofar as the next sign-in to a *new* account fires a new
 * pass — same account ID never re-runs.
 */
class BackfillLocalDataUseCase(
    private val enqueueAllLocalData: EnqueueAllLocalDataUseCase,
    private val tracker: BackfilledAccountTracker,
) {
    sealed class Result {
        data class AlreadyBackfilled(
            val accountId: String,
        ) : Result()

        data class Success(
            val journalCount: Int,
            val noteCount: Int,
            val associationCount: Int,
            val draftCount: Int,
        ) : Result()

        data class Error(
            val message: String,
        ) : Result()
    }

    suspend operator fun invoke(accountId: String): Result {
        if (accountId in tracker.getBackfilledAccountIds()) {
            return Result.AlreadyBackfilled(accountId)
        }
        val counts =
            enqueueAllLocalData().getOrElse { error ->
                Napier.e("Backfill failed for account $accountId", error)
                return Result.Error(error.message ?: "Unknown backfill failure")
            }
        tracker.markAccountBackfilled(accountId)
        Napier.i(
            "Backfilled local data for account $accountId: ${counts.journals} journals, ${counts.notes} notes, " +
                "${counts.associations} associations, ${counts.drafts} drafts",
        )
        return Result.Success(
            journalCount = counts.journals,
            noteCount = counts.notes,
            associationCount = counts.associations,
            draftCount = counts.drafts,
        )
    }
}
