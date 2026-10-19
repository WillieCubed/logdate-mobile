package app.logdate.server.identity

import app.logdate.server.auth.Account
import app.logdate.server.logging.IdentityPublishingNotices
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi

/** What one run of the identity publishing command is allowed to do. */
data class IdentityPublishingOptions(
    val publish: Boolean,
    val dryRun: Boolean = false,
    val reconcile: Boolean = false,
    val batchSize: Int = DEFAULT_BATCH_SIZE,
    val operationsPerMinute: Int = DEFAULT_OPERATIONS_PER_MINUTE,
    val limit: Int? = null,
    val maxFailures: Int = DEFAULT_MAX_FAILURES,
    val handle: String? = null,
) {
    init {
        require(batchSize in 1..MAX_BATCH_SIZE) { "batch size must be between 1 and $MAX_BATCH_SIZE" }
        require(operationsPerMinute in 1..MAX_OPERATIONS_PER_MINUTE) {
            "operations per minute must be between 1 and $MAX_OPERATIONS_PER_MINUTE"
        }
        require(limit == null || limit > 0) { "limit must be positive" }
        require(maxFailures > 0) { "max failures must be positive" }
    }

    /** Whether this run may send anything to the directory or change stored state. */
    val writes: Boolean get() = publish && !dryRun

    companion object {
        const val DEFAULT_BATCH_SIZE: Int = 25
        const val DEFAULT_OPERATIONS_PER_MINUTE: Int = 30
        const val DEFAULT_MAX_FAILURES: Int = 1
        const val MAX_BATCH_SIZE: Int = 500
        const val MAX_OPERATIONS_PER_MINUTE: Int = 600
    }
}

/**
 * Spaces out writes to the PLC directory so a run never sends more than the configured number of
 * operations per minute. Reads are not paced.
 */
class PlcWritePacer(
    operationsPerMinute: Int,
    private val nowMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
    private val sleepMillis: suspend (Long) -> Unit = { delay(it) },
) {
    private val intervalMillis = MILLIS_PER_MINUTE / operationsPerMinute
    private var lastWriteAt: Long? = null

    /** Waits until the next write is allowed, then claims that slot. */
    suspend fun awaitTurn() {
        lastWriteAt?.let { last ->
            val wait = last + intervalMillis - nowMillis()
            if (wait > 0) sleepMillis(wait)
        }
        lastWriteAt = nowMillis()
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

/** One account's line in the run report. */
@Serializable
data class IdentityPublishingReportEntry(
    val accountId: String,
    val did: String?,
    val before: String,
    val differences: List<String> = emptyList(),
    val action: String,
    val after: String,
    val failure: String? = null,
    val detail: String? = null,
)

/** Everything a run saw and did. The command writes it to `--report-file` when one is given. */
@Serializable
data class IdentityPublishingReport(
    val mode: String,
    val directory: String,
    val accountsConsidered: Int,
    val stoppedAtFailureLimit: Boolean,
    val counts: Map<String, Int>,
    val accounts: List<IdentityPublishingReportEntry>,
    val exitCode: Int,
)

/**
 * Walks every account in a stable order (oldest first), a batch at a time, and either reports where
 * each identity stands or publishes it. Writes are paced by a [PlcWritePacer], and a publishing run
 * stops once it reaches [IdentityPublishingOptions.maxFailures] so one systematic problem can't
 * repeat across every account.
 */
@OptIn(ExperimentalUuidApi::class)
class IdentityPublishingRun(
    private val service: IdentityPublishingService,
    private val loadAccounts: suspend () -> List<Account>,
    private val pacer: PlcWritePacer,
    private val directoryUrl: String,
) {
    suspend fun run(options: IdentityPublishingOptions): IdentityPublishingReport {
        Napier.i(IdentityPublishingNotices.RUN_STARTED)
        val ordered =
            loadAccounts()
                .filter { account -> options.handle == null || account.handle.equals(options.handle, ignoreCase = true) }
                .sortedWith(compareBy<Account>({ it.createdAt }, { it.id.toString() }))
        val accounts = options.limit?.let(ordered::take) ?: ordered
        val entries = mutableListOf<IdentityPublishingReportEntry>()
        val counts = linkedMapOf<String, Int>()
        var failures = 0
        var stopped = false

        for (batch in accounts.chunked(options.batchSize)) {
            for (account in batch) {
                val entry = process(account, options)
                entries += entry
                count(counts, entry, options)
                if (entry.failure != null) {
                    failures += 1
                    Napier.e(IdentityPublishingNotices.ACCOUNT_FAILED)
                }
                if (options.writes && failures >= options.maxFailures) {
                    stopped = true
                    break
                }
            }
            Napier.i(IdentityPublishingNotices.summary(counts))
            if (stopped) break
        }

        val exitCode = exitCodeFor(entries, stopped, options)
        Napier.i(IdentityPublishingNotices.summary(counts + ("exit_code" to exitCode)))
        Napier.i(if (stopped) IdentityPublishingNotices.RUN_STOPPED else IdentityPublishingNotices.RUN_COMPLETE)
        return IdentityPublishingReport(
            mode =
                when {
                    !options.publish -> "status"
                    options.dryRun -> "publish_dry_run"
                    else -> "publish"
                },
            directory = directoryUrl,
            accountsConsidered = accounts.size,
            stoppedAtFailureLimit = stopped,
            counts = counts,
            accounts = entries,
            exitCode = exitCode,
        )
    }

    private suspend fun process(
        account: Account,
        options: IdentityPublishingOptions,
    ): IdentityPublishingReportEntry {
        val inspection = service.inspect(account)
        if (!options.publish) {
            return entryFor(inspection, IdentityPublishAction.NONE, inspection.state, failure = null)
        }
        if (options.dryRun) {
            return entryFor(inspection, service.plannedAction(inspection, options.reconcile), inspection.state, failure = null)
        }
        val outcome = service.publish(inspection, options.reconcile, beforeWrite = pacer::awaitTurn)
        return entryFor(inspection, outcome.action, outcome.after, outcome.failure)
    }

    private fun entryFor(
        inspection: IdentityInspection,
        action: IdentityPublishAction,
        after: IdentityPublicationState,
        failure: IdentityPublishFailure?,
    ) = IdentityPublishingReportEntry(
        accountId = inspection.account.id.toString(),
        did = inspection.account.did,
        before = inspection.state.code,
        differences = inspection.differences.map(IdentityDifference::code).sorted(),
        action = action.code,
        after = after.code,
        failure = failure?.code,
        detail = failure?.detail ?: inspection.reason,
    )

    private fun count(
        counts: MutableMap<String, Int>,
        entry: IdentityPublishingReportEntry,
        options: IdentityPublishingOptions,
    ) {
        counts.merge("accounts", 1, Int::plus)
        counts.merge(entry.before, 1, Int::plus)
        if (!options.publish) return
        if (entry.action != IdentityPublishAction.NONE.code) {
            val prefix = if (options.dryRun) "would_" else ""
            counts.merge(prefix + entry.action, 1, Int::plus)
        }
        if (options.writes) counts.merge("now_${entry.after}", 1, Int::plus)
        if (entry.failure != null) counts.merge("failed", 1, Int::plus)
    }

    private fun exitCodeFor(
        entries: List<IdentityPublishingReportEntry>,
        stopped: Boolean,
        options: IdentityPublishingOptions,
    ): Int {
        val finalStates =
            entries.map { entry ->
                val code = if (options.writes) entry.after else entry.before
                IdentityPublicationState.entries.first { it.code == code }
            }
        return when {
            options.handle != null && entries.isEmpty() -> EXIT_FAILED
            stopped || entries.any { it.failure != null } -> EXIT_FAILED
            finalStates.any(IdentityPublicationState::needsAttention) -> EXIT_FAILED
            finalStates.any(IdentityPublicationState::needsWork) -> EXIT_WORK_REMAINS
            else -> EXIT_OK
        }
    }

    companion object {
        /** Every identity the run looked at is published and matches what LogDate serves. */
        const val EXIT_OK: Int = 0

        /**
         * A publish failed, the run stopped at its failure limit, an identity needs an operator, or
         * no account has the handle the run was limited to.
         */
        const val EXIT_FAILED: Int = 1

        /** The command refused to run, for example because publishing is off or the directory wasn't confirmed. */
        const val EXIT_REFUSED: Int = 2

        /** Nothing failed, but some identities still need publishing or reconciling. */
        const val EXIT_WORK_REMAINS: Int = 3
    }
}
