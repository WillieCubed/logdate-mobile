package app.logdate.client.sync.space

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

private val payloadJson = Json { ignoreUnknownKeys = true }

/**
 * A personal-space record staged in the download inbox under its kind. It starts with [PREFIX], so
 * nothing that reads the per-kind wire formats mistakes it for one of them.
 *
 * @property cid null for a deletion.
 * @property record null for a deletion.
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

    companion object {
        const val PREFIX = "SPACE1:"

        /** The staged record, or null when [payload] isn't one, such as a row a per-kind endpoint staged. */
        fun decode(payload: String): SpaceInboxPayload? {
            if (!payload.startsWith(PREFIX)) return null
            return runCatching { payloadJson.decodeFromString(serializer(), payload.removePrefix(PREFIX)) }.getOrNull()
        }
    }
}

/** A change this device couldn't read, kept with its envelope so it can be opened again later. */
@Serializable
internal data class SpaceUnreadablePayload(
    val sealedRkey: String,
    val cid: String,
    val record: Sealed,
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
