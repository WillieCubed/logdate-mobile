package app.logdate.client.sync.v2.link

import app.logdate.client.sync.datalayer.v2.LinkAccept
import app.logdate.client.sync.datalayer.v2.LinkConfirm
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.LinkOffer
import app.logdate.client.sync.datalayer.v2.LinkRefusal
import app.logdate.client.sync.datalayer.v2.SealedLinkBundle
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.peer.link.LinkAnswer
import app.logdate.client.sync.peer.link.LinkExchangeFailure
import app.logdate.client.sync.peer.link.LinkStep
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.link.PhoneLinkExchange
import app.logdate.client.sync.peer.state.StateDocument
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Links this phone's watches. While the switch is on and a watch advertises version 2, it decides per
 * watch with [PhoneLinkDecision], runs the offer and bundle requests, and records the outcome.
 *
 * Exchanges run one at a time. A pairing is confirmed to the person once; an account link once the
 * watch reports it installed (Task 5).
 */
class PhoneLinkInitiator(
    private val v2Enabled: Flow<Boolean>,
    private val watchCapable: Flow<Boolean>,
    private val watchDocuments: Flow<Map<String, StateDocument>>,
    private val targets: LinkTargets,
    private val requester: LinkRequester,
    private val exchange: PhoneLinkExchange,
    private val store: PhoneWatchLinkStore,
    private val notifier: WatchLinkNotifier,
    private val localNodeId: suspend () -> String?,
    private val bundles: LinkBundleSource = PairOnlyBundleSource,
    private val clock: Clock = Clock.System,
) {
    private data class Trigger(
        val active: Boolean,
        val documents: Map<String, StateDocument>,
    )

    private sealed interface ExchangeOutcome {
        class Linked(
            val pairKey: ByteArray,
        ) : ExchangeOutcome

        data class Failed(
            val failure: PhoneLinkFailure,
        ) : ExchangeOutcome
    }

    private val mutex = Mutex()
    private val latest = MutableStateFlow(Trigger(active = false, documents = emptyMap()))

    fun start(scope: CoroutineScope): Job =
        scope.launch {
            combine(v2Enabled, watchCapable, bundles.changes()) { enabled, capable, _ -> enabled && capable }
                .collectLatest { active ->
                    latest.value = latest.value.copy(active = active)
                    if (active) reconcileWhileActive()
                }
        }

    private suspend fun reconcileWhileActive() =
        coroutineScope {
            val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
            launch {
                store.observe().collectLatest { records ->
                    val dueTimes =
                        records.values
                            .filter { it.failure != null }
                            .map { it.nextAttemptAt }
                            .distinct()
                            .sorted()
                    dueTimes.forEach { due ->
                        delay((due - clock.now().toEpochMilliseconds()).coerceAtLeast(0L))
                        retries.emit(Unit)
                    }
                }
            }
            combine(watchDocuments, retries.onStart { emit(Unit) }) { documents, _ -> documents }
                .conflate()
                .collect { documents ->
                    latest.value = Trigger(active = true, documents = documents)
                    reconcile(documents)
                }
        }

    private suspend fun isActive(): Boolean = v2Enabled.first() && watchCapable.first()

    /** Decides again for the latest state, such as when a watch's capability arrives. */
    suspend fun reconcileNow() {
        val trigger = latest.value
        if (trigger.active) reconcile(trigger.documents)
    }

    suspend fun reconcile(documents: Map<String, StateDocument>) {
        mutex.withLock {
            if (!isActive()) return@withLock
            val reachable = reachableTargets() ?: return@withLock
            reachable.forEach { target ->
                if (!isActive()) return@withLock
                reconcileTarget(target, documents[target.nodeId])
            }
        }
    }

    private suspend fun reachableTargets(): List<LinkTarget>? =
        try {
            targets.reachable()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Could not list the watches to link; linking is tried again on the next change", e)
            null
        }

    private suspend fun reconcileTarget(
        target: LinkTarget,
        document: StateDocument?,
    ) {
        val now = clock.now().toEpochMilliseconds()
        val record = store.get(target.nodeId)
        val plan = bundles.plan(document?.watch?.features.orEmpty())
        when (val decision = PhoneLinkDecision.decide(record, plan, document, now)) {
            PhoneLinkDecision.Wait -> Unit
            PhoneLinkDecision.Exchange -> runExchange(target, plan, document, now)
            PhoneLinkDecision.Confirm -> confirm(target, checkNotNull(record).mode, checkNotNull(document), now)
            is PhoneLinkDecision.WatchProblem -> recordWatchProblem(target, plan, decision.failure, checkNotNull(document), now)
        }
    }

    private suspend fun recordWatchProblem(
        target: LinkTarget,
        plan: LinkPlan,
        failure: PhoneLinkFailure,
        document: StateDocument,
        now: Long,
    ) {
        val record = store.recordFailure(target, plan, failure, at = now, seenWatchDocAt = document.writtenAt)
        Napier.w("${target.displayName} could not install the account link: $failure; it is tried again after ${record.nextAttemptAt}")
        if (failure.needsAttention && store.claimAttention(target.nodeId, failure)) notifier.needsAttention(target, failure)
    }

    private suspend fun runExchange(
        target: LinkTarget,
        plan: LinkPlan,
        document: StateDocument?,
        now: Long,
    ) {
        val seen = document?.writtenAt ?: 0L
        val outcome = exchangeWith(target, plan)
        if (!isActive()) return
        when (outcome) {
            is ExchangeOutcome.Linked -> {
                store.recordSuccess(target, plan, outcome.pairKey, at = now, seenWatchDocAt = seen)
                Napier.i("Linked ${target.displayName} in ${plan.mode} mode")
                if (plan.mode == LinkMode.PAIR) confirm(target, plan.mode, document, now)
            }
            is ExchangeOutcome.Failed -> {
                val record = store.recordFailure(target, plan, outcome.failure, at = now, seenWatchDocAt = seen)
                Napier.w("Linking ${target.displayName} failed with ${outcome.failure}; it is tried again after ${record.nextAttemptAt}")
                if (outcome.failure.needsAttention &&
                    store.claimAttention(target.nodeId, outcome.failure)
                ) {
                    notifier.needsAttention(target, outcome.failure)
                }
            }
        }
    }

    private suspend fun confirm(
        target: LinkTarget,
        mode: LinkMode,
        document: StateDocument?,
        now: Long,
    ) {
        if (store.get(target.nodeId)?.confirmedAt == null) notifier.linked(target, mode)
        store.markConfirmed(target.nodeId, at = now, seenWatchDocAt = document?.writtenAt ?: 0L)
    }

    private suspend fun exchangeWith(
        target: LinkTarget,
        plan: LinkPlan,
    ): ExchangeOutcome {
        val phoneNode = localNodeId() ?: return ExchangeOutcome.Failed(PhoneLinkFailure.PHONE_NODE_UNKNOWN)
        val started = exchange.start(plan.mode, phoneNode)
        val offerAnswer =
            requester
                .request(target.nodeId, WearPaths.LINK_OFFER, LinkWire.write(LinkOffer.serializer(), started.offer))
                .getOrElse { return unreachable(target, it) }
        val accept =
            when (val answer = LinkWire.read(offerAnswer, LinkAccept.serializer())) {
                is LinkAnswer.Answered -> answer.value
                is LinkAnswer.Refused -> return ExchangeOutcome.Failed(answer.reason.toFailure())
                is LinkAnswer.Unreadable -> return ExchangeOutcome.Failed(PhoneLinkFailure.ANSWER_UNREADABLE)
            }
        if (!isActive()) return ExchangeOutcome.Failed(PhoneLinkFailure.WATCH_CANNOT_LINK)
        val bundle =
            when (val result = bundles.bundle(plan)) {
                is LinkBundleResult.Ready -> result.bundle
                is LinkBundleResult.Unavailable -> return ExchangeOutcome.Failed(result.failure)
            }
        val sealed =
            when (val step = exchange.seal(started, accept, target.nodeId, bundle)) {
                is LinkStep.Ok -> step.value
                is LinkStep.Failed -> return ExchangeOutcome.Failed(step.failure.toFailure())
            }
        return finish(target, sealed)
    }

    private suspend fun finish(
        target: LinkTarget,
        sealed: PhoneLinkExchange.Sealed,
    ): ExchangeOutcome {
        if (!isActive()) return ExchangeOutcome.Failed(PhoneLinkFailure.WATCH_CANNOT_LINK)
        val bundleAnswer =
            requester
                .request(target.nodeId, WearPaths.LINK_BUNDLE, LinkWire.write(SealedLinkBundle.serializer(), sealed.bundle))
                .getOrElse { return unreachable(target, it) }
        val confirm =
            when (val answer = LinkWire.read(bundleAnswer, LinkConfirm.serializer())) {
                is LinkAnswer.Answered -> answer.value
                is LinkAnswer.Refused -> return ExchangeOutcome.Failed(answer.reason.toFailure())
                is LinkAnswer.Unreadable -> return ExchangeOutcome.Failed(PhoneLinkFailure.ANSWER_UNREADABLE)
            }
        if (!exchange.verify(sealed, confirm)) return ExchangeOutcome.Failed(PhoneLinkFailure.CONFIRMATION_MISMATCH)
        return ExchangeOutcome.Linked(sealed.pairKey)
    }

    private fun unreachable(
        target: LinkTarget,
        error: Throwable,
    ): ExchangeOutcome {
        Napier.w("${target.displayName} did not answer a link request", error)
        return ExchangeOutcome.Failed(PhoneLinkFailure.WATCH_UNREACHABLE)
    }

    private fun LinkRefusal.toFailure(): PhoneLinkFailure =
        when (this) {
            LinkRefusal.EXPIRED_OFFER, LinkRefusal.REPLAYED_NONCE -> PhoneLinkFailure.OFFER_EXPIRED
            LinkRefusal.BOUND_TO_ANOTHER_ACCOUNT -> PhoneLinkFailure.WATCH_BOUND_TO_ANOTHER_ACCOUNT
            LinkRefusal.UNSUPPORTED -> PhoneLinkFailure.WATCH_CANNOT_LINK
        }

    private fun LinkExchangeFailure.toFailure(): PhoneLinkFailure =
        when (this) {
            LinkExchangeFailure.EXPIRED_OFFER, LinkExchangeFailure.REPLAYED_NONCE -> PhoneLinkFailure.OFFER_EXPIRED
            else -> PhoneLinkFailure.ANSWER_UNREADABLE
        }
}
