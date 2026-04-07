@file:Suppress("ktlint:standard:max-line-length")

package app.logdate.feature.rewind.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.rewind.GetRewindUseCase
import app.logdate.client.sharing.RewindQuote
import app.logdate.client.sharing.RewindQuoteCardRenderer
import app.logdate.client.sharing.RewindStatsSummary
import app.logdate.client.sharing.RewindStatsSummaryRenderer
import app.logdate.client.sharing.SharingLauncher
import app.logdate.feature.rewind.ui.detail.RewindShareRequest
import app.logdate.feature.rewind.ui.detail.RewindShareVisual
import app.logdate.feature.rewind.ui.detail.RewindStatsShareRequest
import app.logdate.shared.model.Rewind
import app.logdate.shared.model.RewindContent
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * ViewModel managing rewind detail screen state and data.
 *
 * Handles loading individual rewind data and transforming it into story panels
 * for the Instagram Stories-like interface. Currently provides sample data
 * while the backend implementation is completed.
 *
 * ## Architecture:
 * - **Data Source**: GetRewindUseCase for rewind content
 * - **State Management**: Reactive StateFlow for UI state updates
 * - **Error Handling**: Graceful fallbacks for missing or invalid data
 *
 * ## Current Implementation:
 * Uses the domain layer's GetRewindUseCase to fetch rewind data and transforms
 * it into UI-ready panels for the story-like interface.
 *
 * @param getRewindUseCase Use case for retrieving rewind data
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RewindDetailViewModel(
    private val getRewindUseCase: GetRewindUseCase,
    private val sharingLauncher: SharingLauncher,
    private val quoteCardRenderer: RewindQuoteCardRenderer,
    private val statsSummaryRenderer: RewindStatsSummaryRenderer,
//    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val rewindIdState = MutableStateFlow<Uuid?>(null)
    private val latestRewind = MutableStateFlow<Rewind?>(null)

    /**
     * The most recently loaded rewind, or null before [loadRewind] resolves. The composable
     * reads this to format date ranges for the stats summary share — keeping locale-aware
     * formatting in the UI layer where compose resources live.
     */
    val currentRewind: StateFlow<Rewind?> = latestRewind

//    private val rewindData = savedStateHandle.toRoute<RewindDetailRoute>()

    val uiState: StateFlow<RewindDetailUiState> =
        rewindIdState
            .flatMapLatest { rewindId ->
                if (rewindId == null) {
                    // Return a specific RewindNotSelected error state when rewindId is null
                    flowOf(RewindDetailUiState.Error.RewindNotSelected)
                } else {
                    // Use the rewindId to fetch data
                    getRewindUseCase(rewindId)
                        .map { rewind ->
                            // Cache the raw rewind so stats summary share can read its metadata
                            // without re-fetching.
                            latestRewind.value = rewind
                            val panels = transformRewindToStoryPanels(rewind)

                            if (panels.isEmpty()) {
                                RewindDetailUiState.Error.EmptyContent
                            } else {
                                RewindDetailUiState.Success(panels = panels)
                            }
                        }.catch { e ->
                            Napier.e("Error loading rewind", e)
                            emit(RewindDetailUiState.Error.LoadingFailed)
                        }
                }
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                RewindDetailUiState.Loading,
            )

    /**
     * Loads rewind data for the specified ID.
     *
     * @param rewindId The unique identifier of the rewind to load
     */
    fun loadRewind(rewindId: Uuid) {
        rewindIdState.value = rewindId
    }

    /**
     * Shares a rewind panel through the platform share sheet.
     *
     * When the request's visual is a [RewindShareVisual.Quote], the view model first asks the
     * [RewindQuoteCardRenderer] to turn the user's words into a styled image so apps that
     * prefer media (Stories, Notes, Photos) get something visual to attach. If rendering is
     * unavailable the share still goes through with text only.
     */
    fun sharePanel(request: RewindShareRequest) {
        viewModelScope.launch {
            val resolvedMedia =
                when (val visual = request.visual) {
                    is RewindShareVisual.ExistingMedia -> visual.uri
                    is RewindShareVisual.Quote -> quoteCardRenderer.render(visual.toRewindQuote())
                    RewindShareVisual.None -> null
                }
            sharingLauncher.shareContent(
                text = request.text,
                mediaUris = resolvedMedia?.let { listOf(it) } ?: emptyList(),
                title = request.title,
                chooserTitle = request.chooserTitle,
            )
        }
    }

    private fun RewindShareVisual.Quote.toRewindQuote(): RewindQuote =
        RewindQuote(text = text, dateLabel = dateLabel, accentSeed = accentSeed)

    /**
     * Shares the current rewind as a single stats summary card.
     *
     * Reads the cached latest [Rewind], hands it (along with the localized labels resolved by
     * the composable) to the [RewindStatsSummaryRenderer], and then launches the system share
     * sheet with whatever URI the renderer produces. Falls back to the request's plain text
     * when the renderer is unavailable or returns null.
     */
    fun shareRewindStats(request: RewindStatsShareRequest) {
        val rewind = latestRewind.value ?: return
        viewModelScope.launch {
            val summary = buildStatsSummary(rewind, request)
            val mediaUri = statsSummaryRenderer.render(summary)
            sharingLauncher.shareContent(
                text = request.text,
                mediaUris = mediaUri?.let { listOf(it) } ?: emptyList(),
                title = request.title,
                chooserTitle = request.chooserTitle,
            )
        }
    }

    private fun buildStatsSummary(
        rewind: Rewind,
        request: RewindStatsShareRequest,
    ): RewindStatsSummary {
        val textCount = rewind.content.count { it is RewindContent.TextNote }
        val photoCount = rewind.content.count { it is RewindContent.Image || it is RewindContent.Video }
        val peopleCount = rewind.metadata?.peopleHighlighted?.size ?: 0

        val counters =
            buildList {
                if (textCount > 0) add(RewindStatsSummary.Counter(request.entriesLabel, textCount))
                if (photoCount > 0) add(RewindStatsSummary.Counter(request.photosLabel, photoCount))
                if (peopleCount > 0) add(RewindStatsSummary.Counter(request.peopleLabel, peopleCount))
            }

        val highlights =
            buildList {
                rewind.metadata
                    ?.detectedActivities
                    ?.firstOrNull()
                    ?.name
                    ?.let { activity ->
                        add(
                            RewindStatsSummary.Highlight(
                                heading = request.themeHeadingLabel,
                                value = activity.lowercase().replace('_', ' '),
                            ),
                        )
                    }
                rewind.metadata?.locationSummary?.primaryLocation?.let { location ->
                    add(
                        RewindStatsSummary.Highlight(
                            heading = request.locationHeadingLabel,
                            value = location,
                        ),
                    )
                }
            }

        return RewindStatsSummary(
            title = rewind.title,
            subtitle = request.subtitle,
            counters = counters,
            highlights = highlights,
            accentSeed = rewind.uid.hashCode(),
        )
    }

    /**
     * Transforms a Rewind domain model into UI panel states.
     *
     * This method converts domain-level Rewind data into a sequence of UI panels
     * for the Instagram Stories-like interface. It handles different content types
     * appropriately and creates a cohesive story flow.
     *
     * @param rewind The domain Rewind model to transform
     * @return List of UI panel states in presentation order
     */
    private fun transformRewindToStoryPanels(rewind: Rewind): List<RewindPanelUiState> {
        val panels = mutableListOf<RewindPanelUiState>()

        // 1. Add title panel
        panels.add(
            SubtitledRewindPanelUiState(
                title = rewind.title,
                subtitle = "From ${
                    formatDateRange(
                        rewind.startDate,
                        rewind.endDate,
                    )
                }\nYour journey through time and memories",
                backgroundUri = null,
            ),
        )

        // 2. Transform content items to panels
        val contentPanels =
            rewind.content.map { content ->
                when (content) {
                    is RewindContent.TextNote -> {
                        TextNoteRewindPanelUiState(
                            sourceId = content.sourceId,
                            timestamp = content.timestamp,
                            content = content.content,
                            dateFormatted = formatTimestamp(content.timestamp),
                            background =
                                RewindPanelBackgroundSpec(
                                    color = 0xFF1A1A1A, // Dark gray background by default
                                ),
                        )
                    }

                    is RewindContent.Image -> {
                        ImageRewindPanelUiState(
                            sourceId = content.sourceId,
                            timestamp = content.timestamp,
                            imageUri = content.uri,
                            caption = content.caption,
                            dateFormatted = formatTimestamp(content.timestamp),
                        )
                    }

                    is RewindContent.Video -> {
                        // For now, treat videos like images - video playback support can be added later
                        ImageRewindPanelUiState(
                            sourceId = content.sourceId,
                            timestamp = content.timestamp,
                            imageUri = content.uri,
                            caption = content.caption,
                            dateFormatted = formatTimestamp(content.timestamp),
                        )
                    }

                    is RewindContent.NarrativeContext -> {
                        NarrativeContextRewindPanelUiState(
                            sourceId = content.sourceId,
                            timestamp = content.timestamp,
                            contextText = content.contextText,
                            backgroundImageUri = content.backgroundImage,
                        )
                    }

                    is RewindContent.Transition -> {
                        TransitionRewindPanelUiState(
                            sourceId = content.sourceId,
                            timestamp = content.timestamp,
                            transitionText = content.transitionText,
                        )
                    }
                }
            }

        panels.addAll(contentPanels)

        // 3. Add statistics panels if we have enough content to generate them
        if (rewind.content.isNotEmpty()) {
            // Count text notes
            val textNoteCount = rewind.content.count { it is RewindContent.TextNote }
            if (textNoteCount > 0) {
                panels.add(
                    BigStatisticRewindPanelUiState(
                        title = "Journal Entries",
                        statistic = textNoteCount.toString(),
                        units = "entries",
                        description = "You captured thoughts and memories $textNoteCount times this week. Each entry is a window into this moment in your life.",
                        background =
                            RewindPanelBackgroundSpec(
                                color = 0xFF9C27B0, // Purple
                            ),
                    ),
                )
            }

            // Count images
            val imageCount = rewind.content.count { it is RewindContent.Image }
            if (imageCount > 0) {
                panels.add(
                    BigStatisticRewindPanelUiState(
                        title = "Images Captured",
                        statistic = imageCount.toString(),
                        units = "photos",
                        description = "You preserved $imageCount visual memories this week. Each image tells a unique story from your perspective.",
                        background =
                            RewindPanelBackgroundSpec(
                                color = 0xFF2196F3, // Blue
                            ),
                    ),
                )
            }
        }

        // 4. Add closing panel
        panels.add(
            BasicTextRewindPanelUiState(
                text = "This week brought new experiences, personal growth, and countless small moments that make life beautiful. Here's to next week's adventures!",
                background =
                    RewindPanelBackgroundSpec(
                        color = 0xFFFF9800, // Orange
                    ),
            ),
        )

        return panels
    }

    /**
     * Formats a timestamp into a human-readable date string.
     *
     * @param timestamp The timestamp to format
     * @return Formatted date string (e.g., "Tuesday, Nov 19")
     */
    private fun formatTimestamp(timestamp: Instant): String {
        val localDateTime = timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
        val month =
            when (localDateTime.month) {
                Month.JANUARY -> "Jan"
                Month.FEBRUARY -> "Feb"
                Month.MARCH -> "Mar"
                Month.APRIL -> "Apr"
                Month.MAY -> "May"
                Month.JUNE -> "Jun"
                Month.JULY -> "Jul"
                Month.AUGUST -> "Aug"
                Month.SEPTEMBER -> "Sep"
                Month.OCTOBER -> "Oct"
                Month.NOVEMBER -> "Nov"
                Month.DECEMBER -> "Dec"
            }

        return "$month ${localDateTime.day}, ${localDateTime.year}"
    }

    /**
     * Formats a date range into a human-readable string.
     *
     * @param startDate Start of the date range
     * @param endDate End of the date range
     * @return Formatted date range string (e.g., "November 18-24, 2024")
     */
    private fun formatDateRange(
        startDate: Instant,
        endDate: Instant,
    ): String {
        val startLocal = startDate.toLocalDateTime(TimeZone.currentSystemDefault())
        val endLocal = endDate.toLocalDateTime(TimeZone.currentSystemDefault())

        val startMonth =
            when (startLocal.month) {
                Month.JANUARY -> "January"
                Month.FEBRUARY -> "February"
                Month.MARCH -> "March"
                Month.APRIL -> "April"
                Month.MAY -> "May"
                Month.JUNE -> "June"
                Month.JULY -> "July"
                Month.AUGUST -> "August"
                Month.SEPTEMBER -> "September"
                Month.OCTOBER -> "October"
                Month.NOVEMBER -> "November"
                Month.DECEMBER -> "December"
            }

        // If same month
        if (startLocal.month == endLocal.month && startLocal.year == endLocal.year) {
            return "$startMonth ${startLocal.day}-${endLocal.day}, ${startLocal.year}"
        }

        // Different months
        val endMonth =
            when (endLocal.month) {
                Month.JANUARY -> "January"
                Month.FEBRUARY -> "February"
                Month.MARCH -> "March"
                Month.APRIL -> "April"
                Month.MAY -> "May"
                Month.JUNE -> "June"
                Month.JULY -> "July"
                Month.AUGUST -> "August"
                Month.SEPTEMBER -> "September"
                Month.OCTOBER -> "October"
                Month.NOVEMBER -> "November"
                Month.DECEMBER -> "December"
            }

        return "$startMonth ${startLocal.day} - $endMonth ${endLocal.day}, ${endLocal.year}"
    }

    /**
     * Creates sample story panels for demonstration purposes.
     *
     * @return List of sample rewind panels
     */
    private fun createSamplePanels(): List<RewindPanelUiState> =
        listOf(
            SubtitledRewindPanelUiState(
                title = "A Week to Remember",
                subtitle = "From November 18-24, 2024\nYour journey through time and memories",
                backgroundUri = null,
            ),
            BigStatisticRewindPanelUiState(
                title = "Steps Taken",
                statistic = "47,832",
                units = "steps",
                description = "You walked the equivalent of 22 miles this week - that's impressive dedication to staying active!",
                background =
                    RewindPanelBackgroundSpec(
                        color = 0xFF4CAF50, // Green
                    ),
            ),
            BasicTextRewindPanelUiState(
                text = "You visited 3 new coffee shops this week and tried 2 new types of pastries. Your adventurous spirit is inspiring!",
                background =
                    RewindPanelBackgroundSpec(
                        color = 0xFF2196F3, // Blue
                    ),
            ),
            BigStatisticRewindPanelUiState(
                title = "Journal Entries",
                statistic = "5",
                units = "entries",
                description = "You captured thoughts and memories 5 times this week. Each entry is a window into this moment in your life.",
                background =
                    RewindPanelBackgroundSpec(
                        color = 0xFF9C27B0, // Purple
                    ),
            ),
            SubtitledRewindPanelUiState(
                title = "Most Active Day",
                subtitle = "Thursday was your busiest day with 12,847 steps and 3 journal entries. You were unstoppable!",
                backgroundUri = null,
            ),
            BasicTextRewindPanelUiState(
                text = "This week brought new experiences, personal growth, and countless small moments that make life beautiful. Here's to next week's adventures!",
                background =
                    RewindPanelBackgroundSpec(
                        color = 0xFFFF9800, // Orange
                    ),
            ),
        )
}
