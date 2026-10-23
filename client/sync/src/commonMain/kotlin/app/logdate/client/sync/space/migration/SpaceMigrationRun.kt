package app.logdate.client.sync.space.migration

import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.FileMediaDataSource
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.space.SpaceAccess
import app.logdate.client.sync.space.SpaceClient
import app.logdate.shared.model.sync.LegacyInventory
import app.logdate.shared.model.sync.SpaceMigrationErrorCodes
import app.logdate.shared.model.sync.SpaceMigrationStatus
import app.logdate.shared.model.sync.SpaceMigrationSwitchRequest
import io.github.aakira.napier.Napier
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Rounds of uploading again what the check found different, before the move stops and reports it. */
private const val REPAIR_ROUNDS = 3

/** Switches tried before the move stops, when a store changed between the check and the switch. */
private const val SWITCH_ATTEMPTS = 3

/** Server answers that mean checking again and switching again can succeed. */
private val CHECK_AGAIN =
    setOf(SpaceMigrationErrorCodes.LEGACY_CHANGED, SpaceMigrationErrorCodes.SPACE_CHANGED, SpaceMigrationErrorCodes.HOLD_NOT_READY)

/**
 * One attempt at moving the journal, from wherever the saved checkpoint says it stopped:
 *
 * 1. **Upload** every item as sealed records, saving a checkpoint every few items.
 * 2. **Check without a hold**: read the personal space back, compare every record with this
 *    device's item by content hash, and download and compare every media file. Whatever differs is
 *    uploaded again, up to a few rounds.
 * 3. **Check under a hold**: ask the server to hold legacy writes, wait until every write that began
 *    before the hold has finished, download what other devices sent before it, compare legacy storage
 *    with this device, and read the space back once more.
 * 4. **Switch**, sending the server the proof of that last check. If either store changed, check and
 *    switch again.
 *
 * Until the switch nothing changes for legacy sync. Any problem releases the hold and leaves the
 * account on legacy storage.
 */
internal class SpaceMigrationRun(
    private val env: SpaceMigrationEnvironment,
    private val scope: UploadScope,
    private val access: SpaceAccess,
    start: SpaceMigrationCheckpoint,
    private val publish: (JournalMoveState) -> Unit,
) {
    private val checkpoints = env.deps.checkpoints
    private val migrationId = start.migrationId
    private var current = start
    private val writer = SpaceMigrationWriter(SpaceClient(env.tokens, env.space.homes, access), access, env.space.swapStore, env.deps.wait)
    private val refs = MigrationMediaRefs(env.legacyRefs, checkpoints, scope)
    private val source = MigrationMediaSource(env.deps.api, migrationId, refs, env.deps.spaceMedia)
    private val preparer =
        MigrationMediaPreparer(
            env.tokens,
            scope,
            refs,
            migrationTransfer(env.mediaManager, refs, source, env.mediaReferences),
            source,
            env.legacyMedia,
            env.legacyRefs,
        )
    private val upload =
        SpaceMigrationUpload(
            writer,
            env.space.validator,
            checkpoints,
            scope,
        ) { done, total -> publish(JournalMoveState.Uploading(done, total)) }
    private val check = SpaceMigrationReadBackCheck(writer, access, env.space.validator)
    private val checkedMedia = mutableSetOf<String>()

    /** Runs the attempt to the switch, and returns the server's status once the account switched. */
    suspend fun execute(): SpaceMigrationStatus {
        val kinds = env.kinds(preparer)
        if (current.stage == SpaceMigrationStage.UPLOADING) current = upload.run(kinds, current)
        publish(JournalMoveState.Checking)
        checkSpace(kinds, withMedia = true)
        var switched = false
        try {
            val status = switchWhenChecked(kinds)
            switched = true
            return status
        } finally {
            if (!switched) withContext(NonCancellable) { releaseQuietly() }
        }
    }

    /** Settles this device on the personal space after the switch; see [settleAfterSwitch]. */
    suspend fun settle(status: SpaceMigrationStatus) {
        val seq = status.switchedAtSeq ?: error("The server switched the account without saying where the space stood")
        current = current.copy(stage = SpaceMigrationStage.SWITCHED, switchedAtSeq = seq, legacyKeptUntil = status.legacyKeptUntil)
        checkpoints.save(scope, current)
        settleAfterSwitch(env, scope, check.readBack(), seq, current)
    }

    private suspend fun switchWhenChecked(kinds: List<MigrationKind>): SpaceMigrationStatus {
        var lastRefusal: CloudApiException? = null
        repeat(SWITCH_ATTEMPTS) {
            holdLegacyWrites()
            val caughtUp = env.download()
            if (!caughtUp.success) {
                throw caughtUp.errors.firstNotNullOfOrNull { it.cause }
                    ?: CloudApiException("NETWORK_ERROR", "Catching up with legacy storage failed")
            }
            LegacyComparison(
                env.legacyChecks,
            ) { kind -> legacyIds(kind) }.compare(server("legacyInventory") { env.deps.api.legacyInventory(it) })
            val readBack = checkSpace(kinds, withMedia = true)
            val legacy = server("legacyInventory") { env.deps.api.legacyInventory(it) }
            try {
                return server("switch") { env.deps.api.switch(it, proof(legacy, readBack)) }
            } catch (refusal: CloudApiException) {
                if (refusal.errorCode !in CHECK_AGAIN) throw refusal
                Napier.w("A store changed before the switch; checking the journal again")
                lastRefusal = refusal
            }
        }
        throw lastRefusal ?: CloudApiException("SPACE_MIGRATION_NOT_ACTIVE", "The switch didn't go through")
    }

    /** Reads the space back and uploads again whatever differs, until nothing does; returns the last read. */
    private suspend fun checkSpace(
        kinds: List<MigrationKind>,
        withMedia: Boolean,
    ): SpaceReadBack {
        repeat(REPAIR_ROUNDS) { round ->
            val expected = check.expected(kinds)
            val actual = check.readBack()
            val difference = check.compare(expected, actual)
            val redo = difference.itemsToUpload.toMutableSet()
            if (difference.isEmpty && withMedia) redo += checkMedia()
            if (difference.isEmpty && redo.isEmpty()) return actual
            if (round == REPAIR_ROUNDS - 1) {
                throw MigrationMismatchException(
                    if (difference.isEmpty) JournalMoveProblem.CopyDiffers(JournalDataKind.MEDIA, redo.size) else difference.summary(),
                )
            }
            Napier.w("The personal space differs from this device; uploading ${redo.size} items again")
            for ((type, id) in redo) kinds.firstOrNull { it.entityType == type }?.let { upload.uploadItem(it, id) }
            difference.extra.forEach { writer.delete(it) }
        }
        error("unreachable")
    }

    /** Checks every media file moved since the last check, and returns the items whose files must be uploaded again. */
    private suspend fun checkMedia(): Set<Pair<EntityType, String>> {
        val spaceMedia = env.deps.spaceMedia as? FileMediaDataSource ?: return emptySet()
        val unchecked = refs.movedMedia().keys - checkedMedia
        if (unchecked.isEmpty()) return emptySet()
        SpaceMigrationMediaCheck(env.tokens, scope, spaceMedia, env.mediaManager, refs).check(unchecked)
        val marked = unchecked - refs.movedMedia().keys
        checkedMedia += unchecked - marked
        val owners = env.mediaOwners().associateBy { it.key }
        return marked.mapNotNull { owners[it]?.item }.toSet()
    }

    private suspend fun holdLegacyWrites() {
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

    private suspend fun legacyIds(kind: String): List<String> =
        server("legacyInventory($kind)") { env.deps.api.legacyInventory(it, kind) }
            .kinds
            .firstOrNull { it.kind == kind }
            ?.ids
            .orEmpty()

    private fun proof(
        legacy: LegacyInventory,
        readBack: SpaceReadBack,
    ) = SpaceMigrationSwitchRequest(
        migrationId = migrationId,
        legacyWatermark = legacy.watermark,
        legacyCounts = legacy.kinds.associate { it.kind to it.count },
        spaceSeq = readBack.lastSeq,
        spaceRecordCount = readBack.live.size,
        spaceDigest = readBack.digest(),
    )

    private suspend fun releaseQuietly() {
        try {
            server("release") { env.deps.api.release(it, migrationId) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // The hold lapses on its own; the reason the move stopped is what the person needs to see.
            Napier.w("Releasing the hold on legacy writes failed: ${error::class.simpleName}")
        }
    }

    private suspend fun <T> server(
        operation: String,
        block: suspend (String) -> T,
    ): T = env.server(scope, operation, block)
}
