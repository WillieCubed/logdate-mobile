package app.logdate.client.sync.datalayer.v2

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.Clock

/** CLOUD links the watch to the phone's account; PAIR shares state with no account. */
@Serializable
enum class LinkMode {
    CLOUD,
    PAIR,
}

/**
 * The phone's opening message, sent with `MessageClient.sendRequest` to [WearPaths.LINK_OFFER]. Keys
 * and nonces are standard Base64. Nothing in the link exchange is ever written to a data item.
 */
@Serializable
data class LinkOffer(
    val schema: Int = WearProtocol.LINK_SCHEMA,
    val mode: LinkMode,
    val phoneNode: String,
    val phonePublicKey: String,
    val phoneNonce: String,
    val issuedAt: Long,
    val expiresAt: Long,
) {
    /** False outside the offer's own window, and always false for an offer claiming more than a minute. */
    fun isValidAt(nowMs: Long): Boolean = expiresAt - issuedAt <= WearProtocol.LINK_OFFER_TTL_MS && nowMs >= issuedAt && nowMs < expiresAt
}

/** The watch's answer to a [LinkOffer]. */
@Serializable
data class LinkAccept(
    val schema: Int = WearProtocol.LINK_SCHEMA,
    val watchNode: String,
    val watchPublicKey: String,
    val watchNonce: String,
)

/** A [LinkBundle] sealed with AES-256-GCM under the bundle key, with the transcript hash as associated data. */
@Serializable
data class SealedLinkBundle(
    val schema: Int = WearProtocol.LINK_SCHEMA,
    val iv: String,
    val ciphertext: String,
)

/**
 * What the phone gives the watch. It contains the derived identity and media keys, never the recovery
 * phrase: the phrase can also recover the account itself, and a watch must not have that power.
 */
@Serializable
data class LinkBundle(
    val mode: LinkMode,
    val accountId: String? = null,
    val serverOrigin: String? = null,
    val serverDescriptor: JsonElement? = null,
    val linkCode: String? = null,
    val linkCodeExpiresAt: Long? = null,
    val identityKey: String? = null,
    val identityKeyFingerprint: String? = null,
    val mediaKey: String? = null,
    val flags: Map<String, Boolean> = emptyMap(),
    val settings: Map<String, JsonElement> = emptyMap(),
)

/** The watch's HMAC over the transcript under the confirmation key. */
@Serializable
data class LinkConfirm(
    val schema: Int = WearProtocol.LINK_SCHEMA,
    val mac: String,
)

@Serializable
enum class LinkRefusal {
    EXPIRED_OFFER,
    REPLAYED_NONCE,
    BOUND_TO_ANOTHER_ACCOUNT,
    UNSUPPORTED,
}

@Serializable
data class LinkRefused(
    val schema: Int = WearProtocol.LINK_SCHEMA,
    val reason: LinkRefusal,
)

/**
 * Remembers the nonces of offers still valid, so the same offer cannot be accepted twice. Entries go
 * once their offer has expired, and an expired offer fails [LinkOffer.isValidAt] anyway.
 */
class LinkNonceLedger(
    private val clock: Clock = Clock.System,
) {
    private val mutex = Mutex()
    private val expiries = mutableMapOf<String, Long>()

    suspend fun accept(
        nonce: String,
        expiresAt: Long,
    ): Boolean =
        mutex.withLock {
            val now = clock.now().toEpochMilliseconds()
            expiries.entries.removeAll { it.value <= now }
            if (nonce in expiries) return@withLock false
            expiries[nonce] = expiresAt
            true
        }
}
