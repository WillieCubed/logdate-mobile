package app.logdate.feature.journals.ui.picker

import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class JournalContentPickerUiState(
    val journalTitle: String = "",
    val query: String = "",
    val groups: List<JournalContentPickerDateGroup> = emptyList(),
    val selectedItems: List<JournalContentPickerItem> = emptyList(),
    val isAdding: Boolean = false,
    val addError: String? = null,
    val addedCount: Int? = null,
)

data class JournalContentPickerDateGroup(
    val date: LocalDate,
    val items: List<JournalContentPickerItem>,
)

data class JournalContentPickerItem(
    val id: Uuid,
    val kind: JournalContentPickerItemKind,
    val timestamp: Instant,
    val title: String,
    val mediaRef: String? = null,
)

enum class JournalContentPickerItemKind {
    WRITING,
    PHOTO,
    VIDEO,
    RECORDING,
}
