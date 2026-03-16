package app.logdate.client.sync.datalayer

import app.logdate.shared.model.Journal
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * Converts [Journal] instances to and from flat string maps suitable for
 * the Wear Data Layer API's DataMap format.
 *
 * Each journal is serialized as a JSON payload alongside a uid metadata key.
 * Used by both the watch and phone sides of the Data Layer sync.
 */
class JournalDataMapper(
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        },
) {
    fun toDataMap(journal: Journal): Map<String, String> {
        val jsonPayload = json.encodeToString(Journal.serializer(), journal)
        return mapOf(
            KEY_UID to journal.id.toString(),
            KEY_JSON_PAYLOAD to jsonPayload,
        )
    }

    fun fromDataMap(map: Map<String, String>): Journal {
        val jsonPayload =
            requireNotNull(map[KEY_JSON_PAYLOAD]) {
                "DataMap missing required key: $KEY_JSON_PAYLOAD"
            }
        return json.decodeFromString(Journal.serializer(), jsonPayload)
    }

    companion object {
        const val KEY_UID = "uid"
        const val KEY_JSON_PAYLOAD = "jsonPayload"

        private const val JOURNALS_PATH_PREFIX = "/logdate/journals"

        fun journalPath(journalId: Uuid): String = "$JOURNALS_PATH_PREFIX/$journalId"

        fun journalDeletePath(journalId: Uuid): String = "$JOURNALS_PATH_PREFIX/$journalId/delete"

        fun isJournalPath(path: String): Boolean = path.startsWith(JOURNALS_PATH_PREFIX) && !path.endsWith("/delete")

        fun isDeletePath(path: String): Boolean = path.startsWith(JOURNALS_PATH_PREFIX) && path.endsWith("/delete")

        fun journalIdFromPath(path: String): Uuid {
            val segment = path.removePrefix("$JOURNALS_PATH_PREFIX/").removeSuffix("/delete")
            return Uuid.parse(segment)
        }
    }
}
