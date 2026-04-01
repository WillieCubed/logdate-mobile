package app.logdate.navigation.routes.core

import androidx.navigation3.runtime.NavKey
import app.logdate.util.UuidSerializer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * The starting navigation route, used to stall during app startup.
 */
@Serializable
data object NavigationStart : NavKey

/**
 * Routes for the onboarding flow
 */
@Serializable
data object OnboardingStart : NavKey

@Serializable
data object PersonalIntroRoute : NavKey

@Serializable
data object OnboardingSignIn : NavKey

@Serializable
data object OnboardingEntryRoute : NavKey

@Serializable
data object OnboardingImportRoute : NavKey

@Serializable
data object OnboardingAppOverviewRoute : NavKey

@Serializable
data object OnboardingMemorySelectionRoute : NavKey

@Serializable
data object OnboardingAccountCreationRoute : NavKey

@Serializable
data object OnboardingBirthdayRoute : NavKey

@Serializable
data object OnboardingRecommendationsRoute : NavKey

@Serializable
data object OnboardingDayBoundariesRoute : NavKey

@Serializable
data object OnboardingLocationTimelineRoute : NavKey

@Serializable
data object OnboardingNotificationsRoute : NavKey

@Serializable
data object OnboardingCompleteRoute : NavKey

@Serializable
data object OnboardingWelcomeBackRoute : NavKey

/**
 * Routes for the timeline feature
 */
@Serializable
data object TimelineListRoute : NavKey

@Serializable
data object LocationRoute : NavKey

@Serializable
data class TimelineDetail(
    val day: LocalDate,
) : NavKey

/**
 * Route for the search feature
 */
@Serializable
data object SearchRoute : NavKey

/**
 * Routes for the journal feature
 */
@Serializable
data object JournalList : NavKey

@Serializable
data object NewJournalRoute : NavKey

@Serializable
data class JournalDetail(
    /**
     * The unique identifier for the journal.
     */
    @Serializable(with = UuidSerializer::class)
    val id: Uuid,
) : NavKey

/**
 * Navigation route for the journal settings screen.
 */
@Serializable
data class JournalSettings(
    /**
     * The unique identifier for the journal being configured.
     */
    @Serializable(with = UuidSerializer::class)
    val journalId: Uuid,
) : NavKey

/**
 * Navigation route for the journal sharing screen.
 */
@Serializable
data class ShareJournal(
    /**
     * The unique identifier for the journal being shared.
     */
    @Serializable(with = UuidSerializer::class)
    val journalId: Uuid,
) : NavKey

/**
 * Routes for the editor feature.
 *
 * @param id An existing journal entry to edit, or null for a new entry.
 * @param draftId A draft to resume, or null when not opening a draft.
 * @param journalIds Journals to pre-select for the new entry. Empty means use defaults.
 */
@Serializable
data class EntryEditor(
    val id: Uuid? = null,
    val draftId: Uuid? = null,
    val journalIds: List<
        @Serializable(with = UuidSerializer::class)
        Uuid,
    > = emptyList(),
) : NavKey

/**
 * Navigation route for opening a note by its ID.
 *
 * @param id The note's unique identifier.
 * @param journalId The journal the note was opened from, or null when opened
 *   from a non-journal context (e.g. timeline, search). When present, the
 *   viewer shows the journal's accent color and prev/next navigation.
 */
@Serializable
data class NoteViewerRoute(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid,
    @Serializable(with = UuidSerializer::class)
    val journalId: Uuid? = null,
) : NavKey

/**
 * Routes for the library feature
 */
@Serializable
data object LibraryListRoute : NavKey

@Serializable
data class LibraryMediaDetailRoute(
    @Serializable(with = UuidSerializer::class)
    val mediaId: Uuid,
) : NavKey

/**
 * Routes for the postcards feature
 */
@Serializable
data object PostcardsCollectionRoute : NavKey

@Serializable
data class PostcardEditorRoute(
    @Serializable(with = UuidSerializer::class)
    val postcardId: Uuid? = null,
    @Serializable(with = UuidSerializer::class)
    val sourceMomentRef: Uuid? = null,
) : NavKey

@Serializable
data class PostcardViewerRoute(
    @Serializable(with = UuidSerializer::class)
    val postcardId: Uuid,
) : NavKey

/**
 * Routes for the rewind feature
 */
@Serializable
data object RewindList : NavKey

/**
 * Represents a detail view for a specific rewind entry.
 */
@Serializable
data class RewindDetailRoute(
    /**
     * The unique identifier for the rewind entry.
     */
    @Serializable(with = UuidSerializer::class)
    val id: Uuid,
) : NavKey

/**
 * Navigation route for the main settings overview screen.
 */
@Serializable
data object SettingsOverviewRoute : NavKey

/**
 * Navigation route for account management settings.
 */
@Serializable
data object AccountSettingsRoute : NavKey

/**
 * Navigation route for privacy and security settings.
 */
@Serializable
data object PrivacySettingsRoute : NavKey

/**
 * Navigation route for data and storage settings.
 */
@Serializable
data object DataSettingsRoute : NavKey

/**
 * Navigation route for connected devices settings.
 */
@Serializable
data object DevicesSettingsRoute : NavKey

/**
 * Navigation route for Android notification settings.
 */
@Serializable
data object NotificationsSettingsRoute : NavKey

/**
 * Navigation route for memories personalization settings.
 */
@Serializable
data object MemoriesSettingsRoute : NavKey

/**
 * Navigation route for library settings screen.
 */
@Serializable
data object LibrarySettingsRoute : NavKey

/**
 * Navigation route for the recommendations detail settings screen.
 */
@Serializable
data object RecommendationSettingsRoute : NavKey

/**
 * Navigation route for the streak settings detail screen.
 */
@Serializable
data object StreakSettingsRoute : NavKey

@Serializable
data object TimelineSettingsRoute : NavKey

@Serializable
data object DayBoundarySettingsRoute : NavKey

/**
 * Navigation route for the reset settings hub screen.
 */
@Serializable
data object ResetSettingsRoute : NavKey

/**
 * Navigation route for the clear data detail screen.
 */
@Serializable
data object ClearDataSettingsRoute : NavKey

/**
 * Navigation route for the reset app detail screen.
 */
@Serializable
data object ResetAppSettingsRoute : NavKey

/**
 * Navigation route for advanced settings (server configuration, developer options).
 */
@Serializable
data object AdvancedSettingsRoute : NavKey

/**
 * Navigation route for the sync and backup settings screen.
 */
@Serializable
data object SyncSettingsRoute : NavKey

/**
 * Navigation route for the export and import settings screen.
 */
@Serializable
data object ExportSettingsRoute : NavKey

/**
 * Navigation route for location tracking options detail screen.
 */
@Serializable
data object LocationTrackingOptionsRoute : NavKey

/**
 * Navigation route for location update interval detail screen.
 */
@Serializable
data object LocationIntervalRoute : NavKey

/**
 * Navigation route for location advanced settings detail screen.
 */
@Serializable
data object LocationAdvancedRoute : NavKey

/**
 * Navigation route for the birthday personalization detail screen.
 */
@Serializable
data object BirthdaySettingsRoute : NavKey

/**
 * Navigation route for the watch settings hub screen.
 */
@Serializable
data object WatchSettingsRoute : NavKey

/**
 * Navigation route for watch sync settings detail screen.
 */
@Serializable
data object WatchSyncSettingsRoute : NavKey

/**
 * Navigation route for watch notification settings detail screen.
 */
@Serializable
data object WatchNotificationSettingsRoute : NavKey

/**
 * Navigation route for watch troubleshooting detail screen.
 */
@Serializable
data object WatchTroubleshootingRoute : NavKey
