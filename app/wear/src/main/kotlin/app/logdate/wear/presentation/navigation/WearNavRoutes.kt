package app.logdate.wear.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

data object WearHomeRoute : NavKey

data object WearMoodCheckInRoute : NavKey

data object WearQuickTextRoute : NavKey

data object WearTimelineRoute : NavKey

data class WearTimelineDayDetailRoute(
    val date: LocalDate,
) : NavKey

data object WearRewindListRoute : NavKey

data object WearRewindPlaybackRoute : NavKey

data object WearHealthDashboardRoute : NavKey

data object WearRemoteCameraRoute : NavKey

data object WearSettingsRoute : NavKey

data object WearMoreRoute : NavKey

data object WearVoiceMemoriesRoute : NavKey

data class WearMemoryPlayerRoute(
    val noteId: Uuid,
) : NavKey

data object WearOnboardingRoute : NavKey
