package app.logdate.wear.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.presentation.home.WearHomeScreen
import app.logdate.wear.presentation.mood.MoodCheckInScreen
import app.logdate.wear.presentation.navigation.WearHomeRoute
import app.logdate.wear.presentation.navigation.WearMoodCheckInRoute
import app.logdate.wear.presentation.navigation.WearOnboardingRoute
import app.logdate.wear.presentation.navigation.WearQuickRecordRoute
import app.logdate.wear.presentation.navigation.WearQuickTextRoute
import app.logdate.wear.presentation.navigation.WearRewindListRoute
import app.logdate.wear.presentation.navigation.WearRewindPlaybackRoute
import app.logdate.wear.presentation.navigation.WearSettingsRoute
import app.logdate.wear.presentation.navigation.WearTimelineDayDetailRoute
import app.logdate.wear.presentation.navigation.WearTimelineRoute
import app.logdate.wear.presentation.onboarding.WearOnboardingScreen
import app.logdate.wear.presentation.onboarding.isOnboardingComplete
import app.logdate.wear.presentation.quicktext.QuickTextLauncher
import app.logdate.wear.presentation.recording.WearRecordingScreen
import app.logdate.wear.presentation.rewind.WearRewindListScreen
import app.logdate.wear.presentation.rewind.WearRewindPlaybackScreen
import app.logdate.wear.presentation.rewind.WearRewindViewModel
import app.logdate.wear.presentation.settings.WearSettingsScreen
import app.logdate.wear.presentation.theme.LogDateTheme
import app.logdate.wear.presentation.timeline.WearDayDetailScreen
import app.logdate.wear.presentation.timeline.WearTimelineScreen
import app.logdate.wear.presentation.timeline.WearTimelineViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)
        setContent {
            WearApp()
        }
    }
}

@Composable
fun WearApp() {
    LogDateTheme {
        val context = LocalContext.current
        val startRoute: NavKey = if (isOnboardingComplete(context)) {
            WearHomeRoute
        } else {
            WearOnboardingRoute
        }
        val backStack = remember { mutableStateListOf(startRoute) }
        val navigateBack: () -> Unit = {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }
        }

        NavDisplay(
            backStack = backStack,
            onBack = navigateBack,
            entryProvider = entryProvider {
                entry<WearOnboardingRoute> {
                    WearOnboardingScreen(
                        onComplete = {
                            backStack.clear()
                            backStack.add(WearHomeRoute)
                        },
                    )
                }
                entry<WearHomeRoute> {
                    WearHomeScreen(
                        onNavigateToMoodCheckIn = {
                            backStack.add(WearMoodCheckInRoute)
                        },
                        onNavigateToQuickText = {
                            backStack.add(WearQuickTextRoute)
                        },
                        onNavigateToTimeline = {
                            backStack.add(WearTimelineRoute)
                        },
                        onNavigateToSettings = {
                            backStack.add(WearSettingsRoute)
                        },
                    )
                }
                entry<WearQuickRecordRoute> {
                    WearRecordingScreen(onNavigateBack = navigateBack)
                }
                entry<WearMoodCheckInRoute> {
                    MoodCheckInScreen(
                        onNavigateBack = navigateBack,
                        onNavigateToVoiceNote = {
                            navigateBack()
                            backStack.add(WearQuickRecordRoute)
                        },
                    )
                }
                entry<WearQuickTextRoute> {
                    val notesRepository = koinInject<JournalNotesRepository>()
                    QuickTextLauncher(
                        notesRepository = notesRepository,
                        onDone = navigateBack,
                    )
                }
                entry<WearTimelineRoute> {
                    val timelineViewModel = koinViewModel<WearTimelineViewModel>()
                    WearTimelineScreen(
                        onNavigateBack = navigateBack,
                        onNavigateToDay = { date ->
                            timelineViewModel.selectDay(date)
                            backStack.add(WearTimelineDayDetailRoute(date))
                        },
                        viewModel = timelineViewModel,
                    )
                }
                entry<WearTimelineDayDetailRoute> {
                    val timelineViewModel = koinViewModel<WearTimelineViewModel>()
                    WearDayDetailScreen(viewModel = timelineViewModel)
                }
                entry<WearRewindListRoute> {
                    val rewindViewModel = koinViewModel<WearRewindViewModel>()
                    WearRewindListScreen(
                        viewModel = rewindViewModel,
                        onSelectRewind = { uid ->
                            rewindViewModel.selectRewind(uid)
                            backStack.add(WearRewindPlaybackRoute)
                        },
                    )
                }
                entry<WearRewindPlaybackRoute> {
                    val rewindViewModel = koinViewModel<WearRewindViewModel>()
                    WearRewindPlaybackScreen(
                        viewModel = rewindViewModel,
                        onExit = navigateBack,
                    )
                }
                entry<WearSettingsRoute> {
                    WearSettingsScreen(onNavigateBack = navigateBack)
                }
            },
        )
    }
}
