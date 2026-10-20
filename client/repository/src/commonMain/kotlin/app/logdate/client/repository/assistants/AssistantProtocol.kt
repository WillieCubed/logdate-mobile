@file:Suppress("DEPRECATION")

package app.logdate.client.repository.assistants

import app.logdate.util.UuidSerializer
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

@Serializable
data class AssistantSearchRequest(
    val query: String,
    val limit: Int = 10,
    val fromDate: String? = null,
    val throughDate: String? = null,
    val cursor: String? = null,
) {
    override fun toString(): String = "AssistantSearchRequest([redacted])"
}

@Serializable
data class AssistantSourceReadRequest(
    @Serializable(with = UuidSerializer::class)
    val sourceId: Uuid,
    val limit: Int = 4000,
    val cursor: String? = null,
) {
    override fun toString(): String = "AssistantSourceReadRequest([redacted])"
}

/** A proposal only. Its destination is held in the grant and approval is exclusively native. */
@Serializable
data class AssistantPrepareEntryRequest(
    val text: String,
    val idempotencyKey: String,
    val sourceUrl: String? = null,
    val eventTime: String? = null,
) {
    override fun toString(): String = "AssistantPrepareEntryRequest([redacted])"
}

@Serializable
data class AssistantOperationStatusRequest(
    val operationId: String,
) {
    override fun toString(): String = "AssistantOperationStatusRequest([redacted])"
}

/** Describes the permitted local corpus only; never counts or identifies excluded material. */
@Serializable
data class AssistantCoverage(
    val partial: Boolean = true,
    val syncedThrough: Instant? = null,
    val limitations: Set<AssistantCoverageLimitation> = setOf(AssistantCoverageLimitation.LOCAL_TEXT_ONLY),
)

@Serializable
enum class AssistantCoverageLimitation {
    LOCAL_TEXT_ONLY,
    SYNC_INCOMPLETE,
    REQUESTED_RANGE_PARTIAL,
}

@Serializable
data class AssistantSearchHit(
    @Serializable(with = UuidSerializer::class)
    val sourceId: Uuid,
    val title: String?,
    val excerpt: String,
    val sourceUrl: String,
    val eventTime: Instant? = null,
    val createdAt: Instant? = null,
) {
    override fun toString(): String = "AssistantSearchHit([redacted])"
}

@Serializable
data class AssistantSearchResult(
    val sources: List<AssistantSearchHit>,
    val coverage: AssistantCoverage,
    val nextCursor: String? = null,
) {
    override fun toString(): String = "AssistantSearchResult([redacted])"
}

@Serializable
data class AssistantSourcePage(
    @Serializable(with = UuidSerializer::class)
    val sourceId: Uuid,
    val text: String,
    val sourceUrl: String,
    val coverage: AssistantCoverage,
    val eventTime: Instant? = null,
    val createdAt: Instant? = null,
    val nextCursor: String? = null,
) {
    override fun toString(): String = "AssistantSourcePage([redacted])"
}

/** State stored by the device independently of protocol sessions or request handlers. */
@Serializable
sealed interface AssistantEntryOperation {
    val operationId: String

    @Serializable
    @SerialName("pending")
    data class Pending(
        override val operationId: String,
        val reviewUrl: String,
    ) : AssistantEntryOperation {
        override fun toString(): String = "AssistantEntryOperation.Pending([redacted])"
    }

    @Serializable
    @SerialName("saved")
    data class Saved(
        override val operationId: String,
        @Serializable(with = UuidSerializer::class)
        val sourceId: Uuid,
        val sourceUrl: String,
    ) : AssistantEntryOperation {
        override fun toString(): String = "AssistantEntryOperation.Saved([redacted])"
    }

    @Serializable
    @SerialName("rejected")
    data class Rejected(
        override val operationId: String,
    ) : AssistantEntryOperation {
        override fun toString(): String = "AssistantEntryOperation.Rejected([redacted])"
    }

    @Serializable
    @SerialName("expired")
    data class Expired(
        override val operationId: String,
    ) : AssistantEntryOperation {
        override fun toString(): String = "AssistantEntryOperation.Expired([redacted])"
    }
}

sealed interface AssistantDecodeResult<out T> {
    class Decoded<T>(
        val value: T,
    ) : AssistantDecodeResult<T>

    data object Invalid : AssistantDecodeResult<Nothing>
}

/** Stable failures carry no submitted content or information about forbidden sources. */
@Serializable
enum class AssistantOperationError {
    LOCKED,
    OFFLINE,
    EXPIRED,
    REVOKED,
    INSUFFICIENT_SCOPE,
    UNSUPPORTED_CONTENT,
    INVALID_INPUT,
    FRESHNESS_UNAVAILABLE,
    OUTCOME_UNKNOWN,
}

@Serializable
data class AssistantOperationFailure(
    val reason: AssistantOperationError,
)

object AssistantProtocol {
    private val json =
        Json {
            ignoreUnknownKeys = false
            isLenient = false
            coerceInputValues = false
            encodeDefaults = true
            classDiscriminator = "status"
        }

    fun decodeSearch(input: String): AssistantDecodeResult<AssistantSearchRequest> = decode(input)

    fun decodeSourceRead(input: String): AssistantDecodeResult<AssistantSourceReadRequest> = decode(input)

    fun decodePrepareEntry(input: String): AssistantDecodeResult<AssistantPrepareEntryRequest> = decode(input)

    fun decodeOperationStatus(input: String): AssistantDecodeResult<AssistantOperationStatusRequest> = decode(input)

    fun encodeCapability(capability: AssistantCapability): String = json.encodeToString(capability)

    fun encodeEntryOperation(operation: AssistantEntryOperation): String {
        require(AssistantProtocolLimits.opaqueIdFits(operation.operationId, AssistantProtocolLimits.OPAQUE_ID_LENGTH)) {
            "Invalid operation response bounds"
        }
        val link =
            when (operation) {
                is AssistantEntryOperation.Pending -> operation.reviewUrl
                is AssistantEntryOperation.Saved -> operation.sourceUrl
                else -> null
            }
        require(link == null || boundedLink(link)) { "Invalid operation response bounds" }
        return json.encodeToString(operation)
    }

    fun encodeSearchResult(result: AssistantSearchResult): String {
        require(
            result.sources.size <= AssistantProtocolLimits.SEARCH_SOURCES &&
                result.sources.all {
                    AssistantProtocolLimits.textFits(it.excerpt, AssistantProtocolLimits.EXCERPT_CODE_POINTS) &&
                        (it.title == null || AssistantProtocolLimits.textFits(it.title, AssistantProtocolLimits.TITLE_CODE_POINTS)) &&
                        boundedLink(it.sourceUrl)
                } &&
                boundedCursor(result.nextCursor),
        ) { "Invalid search response bounds" }
        return json.encodeToString(result)
    }

    fun encodeSourcePage(result: AssistantSourcePage): String {
        require(
            AssistantProtocolLimits.textFits(result.text, AssistantProtocolLimits.SOURCE_CODE_POINTS) &&
                boundedLink(result.sourceUrl) &&
                boundedCursor(result.nextCursor),
        ) { "Invalid source response bounds" }
        return json.encodeToString(result)
    }

    private fun boundedLink(value: String): Boolean =
        value.isNotBlank() &&
            AssistantProtocolLimits.textFits(value, AssistantProtocolLimits.URL_CODE_POINTS) &&
            value.none { it.isWhitespace() || it.code < 32 || it.code == 127 }

    private fun boundedCursor(value: String?): Boolean =
        value == null || AssistantProtocolLimits.opaqueIdFits(value, AssistantProtocolLimits.CURSOR_LENGTH)

    private inline fun <reified T> decode(input: String): AssistantDecodeResult<T> =
        try {
            AssistantDecodeResult.Decoded(json.decodeFromString<T>(input))
        } catch (_: SerializationException) {
            AssistantDecodeResult.Invalid
        } catch (_: IllegalArgumentException) {
            AssistantDecodeResult.Invalid
        }
}
