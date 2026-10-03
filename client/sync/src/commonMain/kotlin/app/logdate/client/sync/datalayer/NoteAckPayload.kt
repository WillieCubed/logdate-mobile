package app.logdate.client.sync.datalayer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * The payload of the phone's acknowledgement of a watch note: the version of the note it stored, as
 * the epoch milliseconds of the note's `lastUpdated`.
 *
 * The watch clears a note from its outbox only when its own copy is no newer than this, so an
 * acknowledgement that arrives late cannot clear a change made since. Phones that predate this payload
 * send an empty message, and watches that predate it ignore the payload.
 */
@Serializable
data class NoteAckPayload(
    val lastUpdated: Long,
) {
    fun encode(): ByteArray = json.encodeToString(serializer(), this).encodeToByteArray()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun of(lastUpdated: Instant): NoteAckPayload = NoteAckPayload(lastUpdated.toEpochMilliseconds())

        /**
         * Reads an acknowledgement's payload: null for an empty one, as phones that predate versioned
         * acknowledgements send. Throws [IllegalArgumentException] for one that is not a note
         * acknowledgement.
         */
        fun decode(bytes: ByteArray): NoteAckPayload? {
            if (bytes.isEmpty()) return null
            return json.decodeFromString(serializer(), bytes.decodeToString())
        }
    }
}
