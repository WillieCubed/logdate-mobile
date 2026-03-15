package app.logdate.client.sync.datalayer

import app.logdate.client.repository.journals.JournalNote
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * Converts [JournalNote] instances to and from flat string maps suitable for
 * the Wear Data Layer API's DataMap format.
 *
 * Each note is serialized as a JSON payload alongside type and uid metadata keys.
 * Used by both the watch and phone sides of the Data Layer sync.
 */
class NoteDataMapper(
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        },
) {
    /**
     * Serializes a [JournalNote] into a flat key-value map for Data Layer transport.
     */
    fun toDataMap(note: JournalNote): Map<String, String> {
        val jsonPayload = json.encodeToString(JournalNote.serializer(), note)
        return mapOf(
            KEY_UID to note.uid.toString(),
            KEY_NOTE_TYPE to note.type.name,
            KEY_JSON_PAYLOAD to jsonPayload,
        )
    }

    /**
     * Deserializes a [JournalNote] from a flat key-value map received via Data Layer.
     */
    fun fromDataMap(map: Map<String, String>): JournalNote {
        val jsonPayload =
            requireNotNull(map[KEY_JSON_PAYLOAD]) {
                "DataMap missing required key: $KEY_JSON_PAYLOAD"
            }
        return json.decodeFromString(JournalNote.serializer(), jsonPayload)
    }

    companion object {
        const val KEY_UID = "uid"
        const val KEY_NOTE_TYPE = "noteType"
        const val KEY_JSON_PAYLOAD = "jsonPayload"

        private const val NOTES_PATH_PREFIX = "/logdate/notes"

        /** Data Layer path for a specific note. */
        fun notePath(noteId: Uuid): String = "$NOTES_PATH_PREFIX/$noteId"

        /** Data Layer path signaling deletion of a specific note. */
        fun noteDeletePath(noteId: Uuid): String = "$NOTES_PATH_PREFIX/$noteId/delete"

        /** Returns true if [path] is a note data path (not a delete path). */
        fun isNotePath(path: String): Boolean = path.startsWith(NOTES_PATH_PREFIX) && !path.endsWith("/delete")

        /** Returns true if [path] is a note deletion path. */
        fun isDeletePath(path: String): Boolean = path.endsWith("/delete")

        /** Extracts the note UUID from a note data path. */
        fun noteIdFromPath(path: String): Uuid {
            val segment = path.removePrefix("$NOTES_PATH_PREFIX/").removeSuffix("/delete")
            return Uuid.parse(segment)
        }
    }
}
