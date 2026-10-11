package app.logdate.client.sync.peer.link

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkRefusal
import app.logdate.client.sync.datalayer.v2.LinkRefused
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearProtocol
import kotlinx.serialization.KSerializer
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Why one side stopped a link exchange. Each is logged and answered, never retried in a loop. */
enum class LinkExchangeFailure {
    MALFORMED,
    UNSUPPORTED_SCHEMA,
    EXPIRED_OFFER,
    REPLAYED_NONCE,
    BAD_KEY,
    BUNDLE_UNREADABLE,
}

/** One step of an exchange: the value it produced, or why it stopped. */
sealed interface LinkStep<out T> {
    data class Ok<T>(
        val value: T,
    ) : LinkStep<T>

    data class Failed(
        val failure: LinkExchangeFailure,
    ) : LinkStep<Nothing>
}

fun <T> LinkStep<T>.valueOrNull(): T? = (this as? LinkStep.Ok<T>)?.value

/** What the other device answered a link request with. */
sealed interface LinkAnswer<out T> {
    data class Answered<T>(
        val value: T,
    ) : LinkAnswer<T>

    data class Refused(
        val reason: LinkRefusal,
    ) : LinkAnswer<Nothing>

    data class Unreadable(
        val failure: LinkExchangeFailure,
    ) : LinkAnswer<Nothing>
}

/** Reads and writes link messages. Keys, nonces, IVs and MACs travel as standard Base64. */
@OptIn(ExperimentalEncodingApi::class)
object LinkWire {
    fun encodeKey(bytes: ByteArray): String = Base64.encode(bytes)

    fun decodeKey(text: String): ByteArray? =
        try {
            Base64.decode(text)
        } catch (_: IllegalArgumentException) {
            null
        }

    fun <T> write(
        serializer: KSerializer<T>,
        value: T,
    ): ByteArray = WearJson.encodeToBytes(serializer, value)

    fun refusal(reason: LinkRefusal): ByteArray = write(LinkRefused.serializer(), LinkRefused(reason = reason))

    /** A refusal is read first: it has only `reason`, so no answer ever reads as one. */
    fun <T> read(
        bytes: ByteArray,
        serializer: KSerializer<T>,
    ): LinkAnswer<T> {
        val refused = WearJson.decode(bytes, WearProtocol.LINK_SCHEMA, LinkRefused.serializer())
        if (refused is Decoded.Supported) return LinkAnswer.Refused(refused.value.reason)
        return when (val decoded = WearJson.decode(bytes, WearProtocol.LINK_SCHEMA, serializer)) {
            is Decoded.Supported -> LinkAnswer.Answered(decoded.value)
            is Decoded.Unsupported -> LinkAnswer.Unreadable(LinkExchangeFailure.UNSUPPORTED_SCHEMA)
            is Decoded.Malformed -> LinkAnswer.Unreadable(LinkExchangeFailure.MALFORMED)
        }
    }
}
