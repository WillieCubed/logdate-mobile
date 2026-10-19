package app.logdate.client.sync.space

import app.logdate.client.sync.metadata.EntityType
import app.logdate.lexicon.app.logdate.crypto.Sealed
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import studio.hypertext.atproto.lexicon.data.LexiconData

/** The download checkpoint that holds the personal space's change-feed cursor. */
internal const val SPACE_CHECKPOINT = "SPACE"

/**
 * The inbox type of changes this device couldn't open or check: kept under their sealed key until
 * a recovered key or an app update can read them, never dropped.
 */
internal const val SPACE_UNREADABLE = "SPACE_UNREADABLE"

/**
 * The inbox type [entityType]'s personal-space records are staged under: one row per record, keyed
 * by its sealed key, the only name the change feed gives a deletion. A kind's records of several
 * types each have their own row, so none replaces another.
 */
internal fun spacePartsType(entityType: EntityType): String = "SPACE_${entityType.name}"

private val payloadJson = Json { ignoreUnknownKeys = true }

/**
 * A personal-space record staged in the download inbox. It starts with [PREFIX], so nothing that
 * reads the per-kind wire formats mistakes it for one of them. Once the record is applied the row
 * keeps only which record it was ([withoutContent]), so a later deletion of it can still be found.
 *
 * @property rkey the record's key in lowercase.
 * @property cid null for a deletion.
 * @property record null for a deletion and once applied.
 */
@Serializable
internal data class SpaceInboxPayload(
    val collection: String,
    val rkey: String,
    val sealedRkey: String,
    val cid: String? = null,
    val record: JsonObject? = null,
) {
    fun encode(): String = PREFIX + payloadJson.encodeToString(serializer(), this)

    fun withoutContent(): SpaceInboxPayload = copy(record = null)

    companion object {
        const val PREFIX = "SPACE1:"

        /** The staged record, or null when [payload] isn't one, such as a row a per-kind endpoint staged. */
        fun decode(payload: String): SpaceInboxPayload? {
            if (!payload.startsWith(PREFIX)) return null
            return runCatching { payloadJson.decodeFromString(serializer(), payload.removePrefix(PREFIX)) }.getOrNull()
        }
    }
}

/**
 * A change this device couldn't read, kept with its envelope so it can be opened again later.
 *
 * @property triedKeyId the sealing key this device held when it last tried, and [triedReader] what
 *   this build could read then. The record is opened again only once either changes, because
 *   nothing else can make it readable.
 */
@Serializable
internal data class SpaceUnreadablePayload(
    val sealedRkey: String,
    val cid: String,
    val record: Sealed,
    val triedKeyId: String? = null,
    val triedReader: String? = null,
) {
    fun encode(): String = PREFIX + LexiconData.json.encodeToString(serializer(), this)

    companion object {
        const val PREFIX = "SPACE1-SEALED:"

        fun decode(payload: String): SpaceUnreadablePayload? {
            if (!payload.startsWith(PREFIX)) return null
            return runCatching { LexiconData.json.decodeFromString(serializer(), payload.removePrefix(PREFIX)) }.getOrNull()
        }
    }
}
