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
import app.logdate.wear.location.WearLocationCaptureCoordinator
import app.logdate.wear.presentation.camera.WearRemoteCameraScreen
import app.logdate.wear.presentation.camera.WearRemoteCameraViewModel
import app.logdate.wear.presentation.home.WearHomeScreen
import app.logdate.wear.presentation.memories.WearMemoryPlayerScreen
import app.logdate.wear.presentation.memories.WearVoiceMemoriesScreen
import app.logdate.wear.presentation.mood.MoodCheckInScreen
import app.logdate.wear.presentation.more.WearMoreScreen
import app.logdate.wear.presentation.navigation.WearHomeRoute
import app.logdate.wear.presentation.navigation.WearMemoryPlayerRoute
import app.logdate.wear.presentation.navigation.WearMoodCheckInRoute
import app.logdate.wear.presentation.navigation.WearMoreRoute
import app.logdate.wear.presentation.navigation.WearOnboardingRoute
import app.logdate.wear.presentation.navigation.WearQuickTextRoute
import app.logdate.wear.presentation.navigation.WearRemoteCameraRoute
import app.logdate.wear.presentation.navigation.WearSettingsRoute
import app.logdate.wear.presentation.navigation.WearTimelineDayDetailRoute
import app.logdate.wear.presentation.navigation.WearTimelineRoute
import app.logdate.wear.presentation.navigation.WearVoiceMemoriesRoute
import app.logdate.wear.presentation.navigation.wearRouteFor
import app.logdate.wear.presentation.onboarding.WearOnboardingScreen
import app.logdate.wear.presentation.onboarding.isOnboardingComplete
import app.logdate.wear.presentation.quicktext.QuickTextLauncher
import app.logdate.wear.presentation.settings.WearSettingsScreen
import app.logdate.wear.presentation.theme.LogDateTheme
import app.logdate.wear.presentation.timeline.WearDayDetailScreen
import app.logdate.wear.presentation.timeline.WearTimelineScreen
import app.logdate.wear.presentation.timeline.WearTimelineViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val TILE_ROUTE_EXTRA = "tile_route"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)
        setContent {
            WearApp(initialRouteName = intent?.getStringExtra(TILE_ROUTE_EXTRA))
        }
    }
}

@Composable
fun WearApp(initialRouteName: String? = null) {
    LogDateTheme {
        val context = LocalContext.current
        val startRoute: NavKey =
            if (isOnboardingComplete(context)) {
                wearRouteFor(initialRouteName)
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
            entryProvider =
                entryProvider {
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
                            onNavigateToMemories = {
                                backStack.add(WearVoiceMemoriesRoute)
                            },
                            onNavigateToMore = {
                                backStack.add(WearMoreRoute)
                            },
                        )
                    }
                    entry<WearVoiceMemoriesRoute> {
                        WearVoiceMemoriesScreen(onOpenMemory = { noteId -> backStack.add(WearMemoryPlayerRoute(noteId)) })
                    }
                    entry<WearMemoryPlayerRoute> { route ->
                        WearMemoryPlayerScreen(noteId = route.noteId)
                    }
                    entry<WearMoreRoute> {
                        WearMoreScreen(
                            onNavigateToQuickText = { backStack.add(WearQuickTextRoute) },
                            onNavigateToTimeline = { backStack.add(WearTimelineRoute) },
                            onNavigateToSettings = { backStack.add(WearSettingsRoute) },
                        )
                    }
                    entry<WearMoodCheckInRoute> {
                        MoodCheckInScreen(
                            onNavigateBack = navigateBack,
                            // The recorder is the Home screen; it never starts recording on its own. Replacing the
                            // stack also reaches Home when a tile or complication opened the check-in directly.
                            onNavigateToVoiceNote = {
                                backStack.clear()
                                backStack.add(WearHomeRoute)
                            },
                        )
                    }
                    entry<WearQuickTextRoute> {
                        val notesRepository = koinInject<JournalNotesRepository>()
                        val locationCaptureCoordinator = koinInject<WearLocationCaptureCoordinator>()
                        QuickTextLauncher(
                            notesRepository = notesRepository,
                            locationCaptureCoordinator = locationCaptureCoordinator,
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
                    entry<WearTimelineDayDetailRoute> { route ->
                        val timelineViewModel = koinViewModel<WearTimelineViewModel>()
                        WearDayDetailScreen(
                            date = route.date,
                            viewModel = timelineViewModel,
                        )
                    }
                    entry<WearRemoteCameraRoute> {
                        WearRemoteCameraScreen(
                            viewModel = koinViewModel<WearRemoteCameraViewModel>(),
                            onNavigateBack = navigateBack,
                        )
                    }
                    entry<WearSettingsRoute> {
                        WearSettingsScreen(onNavigateBack = navigateBack)
                    }
                },
        )
    }
}
