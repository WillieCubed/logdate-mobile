package app.logdate.wear.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import app.logdate.ui.platform.LocalLogDateHaptics
import app.logdate.ui.platform.LogDateHaptics
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.v2.navigation.WearRoute
import app.logdate.wear.v2.navigation.toV2BackStack
import app.logdate.wear.v2.theme.LogDateWearTheme
import app.logdate.wear.v2.today.TodayPage
import app.logdate.wear.v2.today.TodayViewModel
import kotlinx.coroutines.flow.Flow
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

object WearAppV2Tags {
    const val SHELL = "wear_app_v2_shell"
}

/**
 * The redesigned watch app, shown while WATCH_EXPERIENCE_V2 is on.
 *
 * [initialRequest] picks the first back stack; a recreated activity restores the one it saved
 * instead. Each item of [newRequests] replaces the back stack, as a cold launch from a tile does.
 * Swiping right dismisses the top screen. Each screen's view models live as long as its entry.
 */
@Composable
fun WearAppV2(
    initialRequest: WearLaunchRequest?,
    newRequests: Flow<WearLaunchRequest>,
) {
    val backStack = rememberNavBackStack(*initialRequest.toV2BackStack().toTypedArray())
    LaunchedEffect(newRequests) {
        newRequests.collect { request ->
            backStack.clear()
            backStack.addAll(request.toV2BackStack())
        }
    }
    LogDateWearTheme {
        CompositionLocalProvider(LocalLogDateHaptics provides koinInject<LogDateHaptics>()) {
            AppScaffold(modifier = Modifier.testTag(WearAppV2Tags.SHELL)) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryDecorators =
                        listOf(
                            rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                            rememberViewModelStoreNavEntryDecorator<NavKey>(),
                        ),
                    sceneStrategies = listOf(rememberSwipeDismissableSceneStrategy<NavKey>()),
                    entryProvider =
                        entryProvider {
                            entry<WearRoute.Home> { TodayEntry() }
                        },
                )
            }
        }
    }
}

@Composable
private fun TodayEntry() {
    val viewModel = koinViewModel<TodayViewModel>()
    val state by viewModel.uiState.collectAsState()
    val handoff by viewModel.handoff.collectAsState()
    TodayPage(
        state = state,
        handoff = handoff,
        onGetPhoneApp = viewModel::getPhoneApp,
        onHandoffDismissed = viewModel::dismissHandoff,
    )
}
