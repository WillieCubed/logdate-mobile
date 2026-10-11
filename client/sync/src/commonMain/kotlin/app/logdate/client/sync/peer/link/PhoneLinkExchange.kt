package app.logdate.client.sync.peer.link

import app.logdate.client.device.crypto.link.LinkHandshake
import app.logdate.client.device.crypto.link.LinkHandshakeException
import app.logdate.client.device.crypto.link.LinkKeyPair
import app.logdate.client.device.crypto.link.LinkTranscript
import app.logdate.client.sync.datalayer.v2.LinkAccept
import app.logdate.client.sync.datalayer.v2.LinkBundle
import app.logdate.client.sync.datalayer.v2.LinkConfirm
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.LinkOffer
import app.logdate.client.sync.datalayer.v2.SealedLinkBundle
import app.logdate.client.sync.datalayer.v2.WearProtocol
import kotlin.time.Clock

/**
 * The phone's half of a link exchange: it opens with an offer, seals the bundle under the key both
 * devices derive, and checks the watch's confirmation.
 */
class PhoneLinkExchange(
    private val handshake: LinkHandshake,
    private val clock: Clock = Clock.System,
) {
    /** An offer on its way. The single-use private key stays inside and is never written anywhere. */
    class Started internal constructor(
        val offer: LinkOffer,
        internal val keyPair: LinkKeyPair,
        internal val nonce: ByteArray,
    )

    /** A sealed bundle, with what the phone needs to check the confirmation and store after a pairing. */
    class Sealed internal constructor(
        val bundle: SealedLinkBundle,
        val pairKey: ByteArray,
        internal val confirmKey: ByteArray,
        internal val transcriptHash: ByteArray,
    ) {
        /** Public identity of this exchange; it contains no private key material. */
        val linkId: String get() = LinkWire.encodeKey(transcriptHash)
    }

    fun start(
        mode: LinkMode,
        phoneNode: String,
    ): Started {
        val keyPair = handshake.generateKeyPair()
        val nonce = handshake.newNonce()
        val now = clock.now().toEpochMilliseconds()
        val offer =
            LinkOffer(
                mode = mode,
                phoneNode = phoneNode,
                phonePublicKey = LinkWire.encodeKey(keyPair.publicKey),
                phoneNonce = LinkWire.encodeKey(nonce),
                issuedAt = now,
                expiresAt = now + WearProtocol.LINK_OFFER_TTL_MS,
            )
        return Started(offer, keyPair, nonce)
    }

    /**
     * Seals [bundle] for the watch at [watchNode], the node the offer was sent to. The transcript names
     * that node rather than the one the answer claims, so an answer relayed from another node cannot open it.
     */
    fun seal(
        started: Started,
        accept: LinkAccept,
        watchNode: String,
        bundle: LinkBundle,
    ): LinkStep<Sealed> {
        if (!started.offer.isValidAt(clock.now().toEpochMilliseconds())) return LinkStep.Failed(LinkExchangeFailure.EXPIRED_OFFER)
        if (accept.schema != WearProtocol.LINK_SCHEMA) return LinkStep.Failed(LinkExchangeFailure.UNSUPPORTED_SCHEMA)
        if (bundle.mode != started.offer.mode) return LinkStep.Failed(LinkExchangeFailure.MALFORMED)
        val watchKey =
            LinkWire.decodeKey(accept.watchPublicKey)?.takeIf { it.size == 32 } ?: return LinkStep.Failed(LinkExchangeFailure.BAD_KEY)
        val watchNonce =
            LinkWire.decodeKey(accept.watchNonce)?.takeIf { it.size == 16 } ?: return LinkStep.Failed(LinkExchangeFailure.BAD_KEY)
        val hash =
            handshake.transcriptHash(
                LinkTranscript(
                    phoneNode = started.offer.phoneNode,
                    watchNode = watchNode,
                    phonePublicKey = started.keyPair.publicKey,
                    watchPublicKey = watchKey,
                    phoneNonce = started.nonce,
                    watchNonce = watchNonce,
                    schema = WearProtocol.LINK_SCHEMA,
                ),
            )
        val keys =
            try {
                handshake.deriveKeys(started.keyPair.privateKey, watchKey, hash)
            } catch (_: LinkHandshakeException) {
                return LinkStep.Failed(LinkExchangeFailure.BAD_KEY)
            }
        val plaintext = LinkWire.write(LinkBundle.serializer(), bundle)
        val sealed =
            try {
                handshake.seal(keys.bundleKey, plaintext, hash)
            } finally {
                plaintext.fill(0)
            }
        val wire = SealedLinkBundle(iv = LinkWire.encodeKey(sealed.iv), ciphertext = LinkWire.encodeKey(sealed.ciphertext))
        return LinkStep.Ok(Sealed(wire, keys.pairKey, keys.confirmKey, hash))
    }

    fun verify(
        sealed: Sealed,
        confirm: LinkConfirm,
    ): Boolean {
        if (confirm.schema != WearProtocol.LINK_SCHEMA) return false
        val mac = LinkWire.decodeKey(confirm.mac) ?: return false
        return handshake.verifyConfirmation(sealed.confirmKey, sealed.transcriptHash, mac)
    }
}
