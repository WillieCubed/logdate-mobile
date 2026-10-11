package app.logdate.client.sync.peer.link

import app.logdate.client.device.crypto.link.LinkHandshake
import app.logdate.client.device.crypto.link.LinkHandshakeException
import app.logdate.client.device.crypto.link.LinkKeys
import app.logdate.client.device.crypto.link.LinkTranscript
import app.logdate.client.device.crypto.link.SealedBytes
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkAccept
import app.logdate.client.sync.datalayer.v2.LinkBundle
import app.logdate.client.sync.datalayer.v2.LinkConfirm
import app.logdate.client.sync.datalayer.v2.LinkNonceLedger
import app.logdate.client.sync.datalayer.v2.LinkOffer
import app.logdate.client.sync.datalayer.v2.SealedLinkBundle
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearProtocol
import kotlin.time.Clock

/**
 * The watch's half of a link exchange: it accepts a fresh offer once, opens the bundle sealed for it,
 * and confirms with an HMAC over the transcript.
 */
class WatchLinkExchange(
    private val handshake: LinkHandshake,
    private val nonces: LinkNonceLedger,
    private val clock: Clock = Clock.System,
) {
    /** An offer this watch accepted, waiting for its bundle. */
    class Accepted internal constructor(
        val reply: LinkAccept,
        val offer: LinkOffer,
        internal val keys: LinkKeys,
        internal val transcriptHash: ByteArray,
    )

    class Opened internal constructor(
        val bundle: LinkBundle,
        val confirm: LinkConfirm,
        val pairKey: ByteArray,
        val linkId: String,
    )

    /** [phoneNode] is the node the offer came from; the transcript binds it instead of the offer's own claim. */
    suspend fun accept(
        offer: LinkOffer,
        watchNode: String,
        phoneNode: String,
    ): LinkStep<Accepted> {
        if (offer.schema != WearProtocol.LINK_SCHEMA) return LinkStep.Failed(LinkExchangeFailure.UNSUPPORTED_SCHEMA)
        if (!offer.isValidAt(clock.now().toEpochMilliseconds())) return LinkStep.Failed(LinkExchangeFailure.EXPIRED_OFFER)
        val phoneKey =
            LinkWire.decodeKey(offer.phonePublicKey)?.takeIf { it.size == 32 } ?: return LinkStep.Failed(LinkExchangeFailure.BAD_KEY)
        val phoneNonce =
            LinkWire.decodeKey(offer.phoneNonce)?.takeIf { it.size == 16 } ?: return LinkStep.Failed(LinkExchangeFailure.BAD_KEY)
        if (!nonces.accept(offer.phoneNonce, offer.expiresAt)) return LinkStep.Failed(LinkExchangeFailure.REPLAYED_NONCE)
        val keyPair = handshake.generateKeyPair()
        val nonce = handshake.newNonce()
        val hash =
            handshake.transcriptHash(
                LinkTranscript(
                    phoneNode = phoneNode,
                    watchNode = watchNode,
                    phonePublicKey = phoneKey,
                    watchPublicKey = keyPair.publicKey,
                    phoneNonce = phoneNonce,
                    watchNonce = nonce,
                    schema = WearProtocol.LINK_SCHEMA,
                ),
            )
        val keys =
            try {
                handshake.deriveKeys(keyPair.privateKey, phoneKey, hash)
            } catch (_: LinkHandshakeException) {
                return LinkStep.Failed(LinkExchangeFailure.BAD_KEY)
            } finally {
                keyPair.privateKey.fill(0)
            }
        val reply =
            LinkAccept(
                watchNode = watchNode,
                watchPublicKey = LinkWire.encodeKey(keyPair.publicKey),
                watchNonce = LinkWire.encodeKey(nonce),
            )
        return LinkStep.Ok(Accepted(reply, offer, keys, hash))
    }

    fun open(
        accepted: Accepted,
        sealed: SealedLinkBundle,
    ): LinkStep<Opened> {
        if (sealed.schema != WearProtocol.LINK_SCHEMA) return LinkStep.Failed(LinkExchangeFailure.UNSUPPORTED_SCHEMA)
        if (!accepted.offer.isValidAt(clock.now().toEpochMilliseconds())) return LinkStep.Failed(LinkExchangeFailure.EXPIRED_OFFER)
        val iv = LinkWire.decodeKey(sealed.iv) ?: return LinkStep.Failed(LinkExchangeFailure.BUNDLE_UNREADABLE)
        val ciphertext = LinkWire.decodeKey(sealed.ciphertext) ?: return LinkStep.Failed(LinkExchangeFailure.BUNDLE_UNREADABLE)
        val plaintext =
            try {
                handshake.open(accepted.keys.bundleKey, SealedBytes(iv, ciphertext), accepted.transcriptHash)
            } catch (_: LinkHandshakeException) {
                return LinkStep.Failed(LinkExchangeFailure.BUNDLE_UNREADABLE)
            }
        val bundle =
            try {
                when (val decoded = WearJson.decode(plaintext, WearProtocol.LINK_SCHEMA, LinkBundle.serializer())) {
                    is Decoded.Supported -> decoded.value
                    is Decoded.Unsupported -> return LinkStep.Failed(LinkExchangeFailure.UNSUPPORTED_SCHEMA)
                    is Decoded.Malformed -> return LinkStep.Failed(LinkExchangeFailure.MALFORMED)
                }
            } finally {
                plaintext.fill(0)
            }
        if (bundle.mode != accepted.offer.mode) return LinkStep.Failed(LinkExchangeFailure.MALFORMED)
        val mac = handshake.confirmation(accepted.keys.confirmKey, accepted.transcriptHash)
        return LinkStep.Ok(
            Opened(bundle, LinkConfirm(mac = LinkWire.encodeKey(mac)), accepted.keys.pairKey, LinkWire.encodeKey(accepted.transcriptHash)),
        )
    }
}
