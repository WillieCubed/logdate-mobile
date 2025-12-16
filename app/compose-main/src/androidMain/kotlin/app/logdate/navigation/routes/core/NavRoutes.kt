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
data object OnboardingCompleteRoute : NavKey

@Serializable
data object OnboardingWelcomeBackRoute : NavKey

/**
 * Routes for the timeline feature
 */
@Serializable
data object TimelineListRoute : NavKey

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
 * Routes for the editor feature
 */
@Serializable
data class EntryEditor(
    val id: Uuid? = null
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
 * Routes for the settings feature
 */

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
 * Navigation route for destructive actions like resetting the app or deleting data.
 */
@Serializable
data object DangerZoneSettingsRoute : NavKey

/**
 * Route key for the full-screen birthday settings screen.
 */
@Serializable
data object BirthdaySettingsRoute : NavKey