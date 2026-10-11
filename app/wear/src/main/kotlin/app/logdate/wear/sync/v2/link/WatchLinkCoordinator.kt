package app.logdate.wear.sync.v2.link

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkAccept
import app.logdate.client.sync.datalayer.v2.LinkConfirm
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.LinkOffer
import app.logdate.client.sync.datalayer.v2.LinkRefusal
import app.logdate.client.sync.datalayer.v2.SealedLinkBundle
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.peer.link.LinkExchangeFailure
import app.logdate.client.sync.peer.link.LinkStep
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.link.WatchLinkExchange
import io.github.aakira.napier.Napier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The watch's side of linking. The phone always opens; this answers its offer and its bundle, records
 * the link, and marks it stale when the watch moves to another phone.
 *
 * One exchange runs at a time and a new offer replaces one that never reached its bundle. Every
 * refusal is logged with the reason the phone receives.
 */
class WatchLinkCoordinator(
    private val exchange: WatchLinkExchange,
    private val store: WatchLinkStore,
    private val v2Enabled: suspend () -> Boolean,
    private val localNodeId: suspend () -> String?,
) {
    private val mutex = Mutex()
    private var pending: Pending? = null

    private class Pending(
        val phoneNode: String,
        val accepted: WatchLinkExchange.Accepted,
    )

    /** The answer to a request at [path], or null when [path] is not a link request. */
    suspend fun answer(
        sourceNodeId: String,
        path: String,
        payload: ByteArray,
    ): ByteArray? =
        when (path) {
            WearPaths.LINK_OFFER -> mutex.withLock { answerOffer(sourceNodeId, payload) }
            WearPaths.LINK_BUNDLE -> mutex.withLock { answerBundle(sourceNodeId, payload) }
            else -> null
        }

    suspend fun onNodeMigrated(oldNodeId: String) {
        mutex.withLock {
            if (pending?.phoneNode == oldNodeId) pending = null
            if (store.markStale(oldNodeId)) {
                Napier.w("This watch moved to another phone; it waits for that phone to link it again")
            }
        }
    }

    private suspend fun answerOffer(
        sourceNodeId: String,
        payload: ByteArray,
    ): ByteArray {
        if (!v2Enabled()) return refuse(LinkRefusal.UNSUPPORTED, "the redesigned watch app is off")
        val offer =
            when (val decoded = WearJson.decode(payload, WearProtocol.LINK_SCHEMA, LinkOffer.serializer())) {
                is Decoded.Supported -> decoded.value
                is Decoded.Unsupported -> return refuse(LinkRefusal.UNSUPPORTED, "the offer uses schema ${decoded.schema}")
                is Decoded.Malformed -> return refuse(LinkRefusal.UNSUPPORTED, decoded.reason)
            }
        if (!acceptsMode(offer.mode)) return refuse(LinkRefusal.UNSUPPORTED, "this watch build cannot link to an account")
        val watchNode = localNodeId() ?: return refuse(LinkRefusal.UNSUPPORTED, "this watch's node is unknown")
        return when (val step = exchange.accept(offer, watchNode = watchNode, phoneNode = sourceNodeId)) {
            is LinkStep.Ok -> {
                pending = Pending(sourceNodeId, step.value)
                LinkWire.write(LinkAccept.serializer(), step.value.reply)
            }
            is LinkStep.Failed -> refuse(step.failure.toRefusal(), "the offer failed its ${step.failure} check")
        }
    }

    private suspend fun answerBundle(
        sourceNodeId: String,
        payload: ByteArray,
    ): ByteArray {
        if (!v2Enabled()) {
            pending = null
            return refuse(LinkRefusal.UNSUPPORTED, "the redesigned watch app is off")
        }
        val current =
            pending?.takeIf { it.phoneNode == sourceNodeId }
                ?: return refuse(LinkRefusal.EXPIRED_OFFER, "no offer from this phone is open")
        pending = null
        val sealed =
            when (val decoded = WearJson.decode(payload, WearProtocol.LINK_SCHEMA, SealedLinkBundle.serializer())) {
                is Decoded.Supported -> decoded.value
                is Decoded.Unsupported -> return refuse(LinkRefusal.UNSUPPORTED, "the bundle uses schema ${decoded.schema}")
                is Decoded.Malformed -> return refuse(LinkRefusal.UNSUPPORTED, decoded.reason)
            }
        val opened =
            when (val step = exchange.open(current.accepted, sealed)) {
                is LinkStep.Ok -> step.value
                is LinkStep.Failed -> return refuse(step.failure.toRefusal(), "the bundle failed its ${step.failure} check")
            }
        return when (opened.bundle.mode) {
            LinkMode.PAIR -> {
                store.savePairing(sourceNodeId, opened.pairKey)
                Napier.i("This watch is paired with its phone")
                LinkWire.write(LinkConfirm.serializer(), opened.confirm)
            }
            LinkMode.CLOUD -> refuse(LinkRefusal.UNSUPPORTED, "this watch build cannot link to an account")
        }
    }

    private fun acceptsMode(mode: LinkMode): Boolean = mode == LinkMode.PAIR

    private fun refuse(
        reason: LinkRefusal,
        why: String,
    ): ByteArray {
        Napier.w("Refused the phone's link request with $reason: $why")
        return LinkWire.refusal(reason)
    }

    private fun LinkExchangeFailure.toRefusal(): LinkRefusal =
        when (this) {
            LinkExchangeFailure.EXPIRED_OFFER -> LinkRefusal.EXPIRED_OFFER
            LinkExchangeFailure.REPLAYED_NONCE -> LinkRefusal.REPLAYED_NONCE
            else -> LinkRefusal.UNSUPPORTED
        }

    companion object {
        fun handles(path: String): Boolean = path == WearPaths.LINK_OFFER || path == WearPaths.LINK_BUNDLE
    }
}
