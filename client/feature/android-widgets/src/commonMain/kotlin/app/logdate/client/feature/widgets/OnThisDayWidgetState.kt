package app.logdate.client.feature.widgets

import kotlinx.serialization.Serializable

/**
 * Persisted state for the On This Day widget.
 *
 * Serialized to JSON via [OnThisDayWidgetStateDefinition] so Glance can render
 * the widget without invoking use cases on every composition.
 */
@Serializable
sealed interface OnThisDayWidgetState {
    /** Initial state before the first worker refresh completes. */
    @Serializable
    data object Loading : OnThisDayWidgetState

    /** No entries old enough to surface — the user hasn't journaled long enough. */
    @Serializable
    data object NewUser : OnThisDayWidgetState

    /** The worker ran but found no matching entries near today's date. */
    @Serializable
    data object NoMemoryToday : OnThisDayWidgetState

    /** A past entry worth revisiting. */
    @Serializable
    data class HasMemory(
        val dateIso: String,
        val dateFormatted: String,
        val summary: String,
        val thumbnailUri: String?,
        val audioNoteId: String? = null,
        val audioUri: String? = null,
    ) : OnThisDayWidgetState

    @Serializable
    data class FixedMemory(
        val noteId: String,
        val dateFormatted: String,
        val summary: String,
        val thumbnailUri: String?,
        val audioUri: String? = null,
    ) : OnThisDayWidgetState

    @Serializable
    data object ChooseMemory : OnThisDayWidgetState

    @Serializable
    data object MissingMemory : OnThisDayWidgetState

    @Serializable
    data class PhotoPrompt(
        val photoUri: String,
    ) : OnThisDayWidgetState

    @Serializable
    data object PhotoUnavailable : OnThisDayWidgetState

    @Serializable
    data object NewEntryReady : OnThisDayWidgetState
}
