package app.logdate.client.data.journals

import app.logdate.shared.model.EditorDraft
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Drafts and application receipts share one DataStore value so neither can commit alone. */
@Serializable
internal data class ArchiveDraftStorageState(
    val drafts: List<EditorDraft> = emptyList(),
    val appliedRecoveries: Set<String> = emptySet(),
) {
    fun encode(json: Json): String = if (appliedRecoveries.isEmpty()) json.encodeToString(drafts) else json.encodeToString(this)

    companion object {
        fun decode(
            json: Json,
            value: String?,
        ): ArchiveDraftStorageState =
            when {
                value.isNullOrBlank() -> ArchiveDraftStorageState()
                value.trimStart().startsWith("[") -> ArchiveDraftStorageState(json.decodeFromString(value))
                else -> json.decodeFromString(value)
            }
    }
}
