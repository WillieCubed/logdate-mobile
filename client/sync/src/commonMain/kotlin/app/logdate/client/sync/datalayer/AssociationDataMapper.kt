package app.logdate.client.sync.datalayer

import kotlin.uuid.Uuid

/**
 * Converts journal-content association pairs to and from flat string maps
 * suitable for the Wear Data Layer API's DataMap format.
 *
 * Associations are simple journalId + contentId pairs. No JSON payload is
 * needed since the data is just two UUIDs.
 */
class AssociationDataMapper {
    fun toDataMap(
        journalId: Uuid,
        contentId: Uuid,
    ): Map<String, String> =
        mapOf(
            KEY_JOURNAL_ID to journalId.toString(),
            KEY_CONTENT_ID to contentId.toString(),
        )

    fun fromDataMap(map: Map<String, String>): Pair<Uuid, Uuid> {
        val journalId =
            requireNotNull(map[KEY_JOURNAL_ID]) {
                "DataMap missing required key: $KEY_JOURNAL_ID"
            }
        val contentId =
            requireNotNull(map[KEY_CONTENT_ID]) {
                "DataMap missing required key: $KEY_CONTENT_ID"
            }
        return Uuid.parse(journalId) to Uuid.parse(contentId)
    }

    companion object {
        const val KEY_JOURNAL_ID = "journalId"
        const val KEY_CONTENT_ID = "contentId"

        private const val ASSOCIATIONS_PATH_PREFIX = "/logdate/associations"
        private const val ID_SEPARATOR = "::"

        fun associationPath(
            journalId: Uuid,
            contentId: Uuid,
        ): String = "$ASSOCIATIONS_PATH_PREFIX/$journalId$ID_SEPARATOR$contentId"

        fun associationDeletePath(
            journalId: Uuid,
            contentId: Uuid,
        ): String = "$ASSOCIATIONS_PATH_PREFIX/$journalId$ID_SEPARATOR$contentId/delete"

        fun isAssociationPath(path: String): Boolean = path.startsWith(ASSOCIATIONS_PATH_PREFIX) && !path.endsWith("/delete")

        fun isDeletePath(path: String): Boolean = path.startsWith(ASSOCIATIONS_PATH_PREFIX) && path.endsWith("/delete")

        fun idsFromPath(path: String): Pair<Uuid, Uuid> {
            val segment = path.removePrefix("$ASSOCIATIONS_PATH_PREFIX/").removeSuffix("/delete")
            val parts = segment.split(ID_SEPARATOR)
            require(parts.size == 2) { "Invalid association path: $path" }
            return Uuid.parse(parts[0]) to Uuid.parse(parts[1])
        }
    }
}
