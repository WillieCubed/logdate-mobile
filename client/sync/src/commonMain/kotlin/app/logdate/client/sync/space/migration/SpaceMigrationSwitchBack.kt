package app.logdate.client.sync.space.migration

import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.runCatchingUnlessCancelled
import app.logdate.client.sync.space.SPACE_CHECKPOINT
import app.logdate.shared.model.sync.SpaceMigrationErrorCodes
import app.logdate.shared.model.sync.SpaceMigrationPhase
import app.logdate.shared.model.sync.SpaceMigrationSwitchBackRequest
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Switches a moved journal back to legacy storage while the server still keeps the legacy copy.
 *
 * Everything written to the personal space after the switch has to reach legacy storage too, from
 * every device. So this device first uploads what it has waiting, asks the server to hold every
 * device's space writes, waits until the ones already under way have finished, and downloads the whole
 * space. Only then does it ask the server to switch back, naming the last change it downloaded; the
 * server refuses if anything came after. Finally it queues its whole journal, and every deletion
 * legacy storage hasn't seen, for legacy storage again.
 *
 * The queueing step is saved before the server is asked, so it happens even if the app stops right
 * after the switch: [finish] runs again before the next sync.
 */
internal class SpaceMigrationSwitchBack(
    private val env: SpaceMigrationEnvironment,
    private val scope: UploadScope,
    private val publish: (JournalMoveState) -> Unit,
) {
    suspend fun run() {
        publish(JournalMoveState.SwitchingBack)
        val status = server("status") { env.deps.api.status(it) }
        val migrationId = status.migrationId
        if (status.phase != SpaceMigrationPhase.SWITCHED || migrationId == null) {
            throw CloudApiException(
                if (status.phase ==
                    SpaceMigrationPhase.CLEANED
                ) {
                    SpaceMigrationErrorCodes.WINDOW_CLOSED
                } else {
                    SpaceMigrationErrorCodes.NOT_ACTIVE
                },
                "The journal isn't in a state that can be switched back",
            )
        }
        env.upload()
        val waiting = env.metadata.getPendingCount()
        if (waiting > 0) throw MigrationMismatchException(JournalMoveProblem.NotOnServer(JournalDataKind.ENTRIES, waiting))
        var switched = false
        try {
            hold(migrationId)
            val pulled = env.download()
            if (!pulled.success) {
                throw pulled.errors.firstNotNullOfOrNull { it.cause }
                    ?: CloudApiException("NETWORK_ERROR", "Downloading the space failed")
            }
            val staged = env.inbox.count()
            if (staged > 0) throw MigrationMismatchException(JournalMoveProblem.MissingOnThisDevice(JournalDataKind.ENTRIES, staged))
            val seq = env.inbox.cursor(SPACE_CHECKPOINT)
            val checkpoint =
                (env.deps.checkpoints.load(scope) ?: SpaceMigrationCheckpoint(migrationId)).copy(
                    migrationId = migrationId,
                    stage = SpaceMigrationStage.SWITCHING_BACK,
                    deletedForLegacy = deletedSinceSwitch(),
                    problem = null,
                    retryAt = null,
                )
            env.deps.checkpoints.save(scope, checkpoint)
            server("switchBack") { env.deps.api.switchBack(it, SpaceMigrationSwitchBackRequest(migrationId, seq)) }
            switched = true
            finish(checkpoint)
        } finally {
            if (!switched) withContext(NonCancellable) { releaseQuietly(migrationId) }
        }
    }

    /** Hands the journal back to legacy sync; safe to run again until it finishes. */
    suspend fun finish(checkpoint: SpaceMigrationCheckpoint) {
        publish(JournalMoveState.SwitchingBack)
        MovedMediaRefs(env.deps.checkpoints, env.legacyRefs, scope).restoreLegacyReferences(env.mediaOwners())
        env.refreshAccount()
        env.rebase(scope)
        env.requeueForLegacy(checkpoint.deletedForLegacy)
        env.upload()
        env.deps.checkpoints.save(scope, checkpoint.copy(stage = SpaceMigrationStage.SWITCHED_BACK, deletedForLegacy = emptyMap()))
        publish(JournalMoveState.SwitchedBack)
    }

    /** The ids legacy storage still has that this device no longer does, by legacy kind. */
    private suspend fun deletedSinceSwitch(): Map<String, Set<String>> =
        env.legacyChecks
            .filter { it.syncedHere() }
            .associate { check ->
                val legacy =
                    server("legacyInventory(${check.legacyKind})") { env.deps.api.legacyInventory(it, check.legacyKind) }
                        .kinds
                        .firstOrNull { it.kind == check.legacyKind }
                        ?.ids
                        .orEmpty()
                        .map { it.lowercase() }
                        .toSet()
                check.legacyKind to (legacy - check.localIds().map { it.lowercase() }.toSet())
            }.filterValues { it.isNotEmpty() }

    private suspend fun hold(migrationId: String) {
        val held = server("hold") { env.deps.api.hold(it, migrationId) }
        val readyAt = held.holdReadyAt ?: return
        val waitFor =
            (
                readyAt -
                    env.deps.clock
                        .now()
                        .toEpochMilliseconds()
            ).milliseconds
        if (waitFor.isPositive()) env.deps.wait(waitFor + 1.seconds)
    }

    private suspend fun releaseQuietly(migrationId: String) {
        runCatchingUnlessCancelled { server("release") { env.deps.api.release(it, migrationId) } }
    }

    private suspend fun <T> server(
        operation: String,
        block: suspend (String) -> T,
    ): T = env.server(scope, operation, block)
}
