package app.logdate.feature.location.timeline.ui.history

import kotlin.math.roundToInt

enum class HistoryTab { Day, Places }

enum class HistoryItemKind { Visit, Journey, Gap }

enum class HistoryMemoryKind { Text, Image, Audio, Video }

enum class HistoryEditAction { ChangePlace, ChangeActivity, ChangeTime, AddVisit, AddMemory, LinkMemory, Delete }

data class HistoryMemoryUi(
    val id: String,
    val title: String,
    val kind: HistoryMemoryKind,
    val supportingText: String = "",
    val thumbnailUri: String? = null,
    val audio: app.logdate.ui.timeline.MomentAudioUiState? = null,
)

data class HistoryItemUi(
    val id: String,
    val kind: HistoryItemKind,
    val title: String,
    val timeLabel: String,
    val supportingText: String = "",
    val memories: List<HistoryMemoryUi> = emptyList(),
)

data class HistoryPlaceUi(
    val id: String,
    val title: String,
    val supportingText: String,
    val memories: List<HistoryMemoryUi> = emptyList(),
)

data class HumanLocationHistoryState(
    val dateLabel: String,
    val daySummary: String = "",
    val items: List<HistoryItemUi> = emptyList(),
    val places: List<HistoryPlaceUi> = emptyList(),
    val selectedItemId: String? = null,
    val tab: HistoryTab = HistoryTab.Day,
    val placesQuery: String = "",
    val placesMapVisible: Boolean = false,
    val placesFilterLabel: String = "",
    val recoveryMessage: String? = null,
    val detailVisible: Boolean = false,
    val replayPlaying: Boolean = false,
    val recoveryActionLabel: String? = null,
    val selectionRevision: Long = 0,
) {
    val selectedItem: HistoryItemUi? get() = items.firstOrNull { it.id == selectedItemId }

    fun itemAtReplayPosition(position: Float): HistoryItemUi? {
        if (items.isEmpty() || position.isNaN()) return null
        return items[(position.coerceIn(0f, 1f) * items.lastIndex).roundToInt()]
    }

    fun adjacentItem(offset: Int): HistoryItemUi? {
        val index = items.indexOfFirst { it.id == selectedItemId }
        if (index < 0) return items.firstOrNull().takeIf { selectedItemId == null && offset > 0 }
        return items.getOrNull(index + offset)
    }

    fun filteredPlaces(): List<HistoryPlaceUi> = places.filter { it.title.contains(placesQuery.trim(), ignoreCase = true) }
}

class HumanLocationHistoryActions(
    val onSelectItem: (String) -> Unit = {},
    val onOpenDetail: (String) -> Unit = {},
    val onCloseDetail: () -> Unit = {},
    val onDayOffset: (Int) -> Unit = {},
    val onCalendar: () -> Unit = {},
    val onTab: (HistoryTab) -> Unit = {},
    val onPlacesQuery: (String) -> Unit = {},
    val onPlacesMapVisible: (Boolean) -> Unit = {},
    val onPlacesFilter: () -> Unit = {},
    val onOpenPlace: (String) -> Unit = {},
    val onOpenMemory: (String) -> Unit = {},
    val onEdit: (String?, HistoryEditAction) -> Unit = { _, _ -> },
    val onReplayPlaying: ((Boolean) -> Unit)? = null,
    val onRecover: (() -> Unit)? = null,
)
