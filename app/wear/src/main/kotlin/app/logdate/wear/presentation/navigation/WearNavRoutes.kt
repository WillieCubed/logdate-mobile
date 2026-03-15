package app.logdate.wear.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.datetime.LocalDate

data object WearHomeRoute : NavKey

data object WearAudioRecordingRoute : NavKey

data object WearQuickRecordRoute : NavKey

data object WearMoodCheckInRoute : NavKey

data object WearQuickTextRoute : NavKey

data object WearTimelineRoute : NavKey

data class WearTimelineDayDetailRoute(val date: LocalDate) : NavKey

data object WearSettingsRoute : NavKey
