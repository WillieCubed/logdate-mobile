package app.logdate.wear.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import app.logdate.ui.platform.LocalLogDateHaptics
import app.logdate.ui.platform.LogDateHaptics
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.v2.ambient.WatchAmbientScope
import app.logdate.wear.v2.flow.FlowPage
import app.logdate.wear.v2.flow.FlowViewModel
import app.logdate.wear.v2.home.HomeScreen
import app.logdate.wear.v2.navigation.WearRoute
import app.logdate.wear.v2.navigation.toV2BackStack
import app.logdate.wear.v2.permissions.rememberPermissionRequester
import app.logdate.wear.v2.recorder.RecorderState
import app.logdate.wear.v2.recorder.RecorderViewModel
import app.logdate.wear.v2.recorder.TodayRecorder
import app.logdate.wear.v2.recorder.locksPaging
import app.logdate.wear.v2.theme.LogDateWearTheme
import app.logdate.wear.v2.today.TodayControls
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
 * Swiping right dismisses the top screen. Each screen's view models live as long as its entry; the
 * recorder is resolved here, outside every entry, so it belongs to the activity, and the
 * permissions it asks for are requested here. While it records, the app stays on screen in ambient
 * mode where the watch supports it.
 */
@Composable
fun WearAppV2(
    initialRequest: WearLaunchRequest?,
    newRequests: Flow<WearLaunchRequest>,
) {
    val recorder = koinViewModel<RecorderViewModel>()
    val recorderState by recorder.state.collectAsState()
    val backStack = rememberNavBackStack(*initialRequest.toV2BackStack().toTypedArray())
    LaunchedEffect(newRequests) {
        newRequests.collect { request ->
            backStack.clear()
            backStack.addAll(request.toV2BackStack())
        }
    }
    RecorderPermissionPrompts(recorder)
    WatchAmbientScope(enabled = recorderState is RecorderState.Live) {
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
                                entry<WearRoute.Home> { HomeEntry(recorder) }
                            },
                    )
                }
            }
        }
    }
}

/** Asks for the permissions the recorder needs at the moment it needs them, and gives it the answers. */
@Composable
private fun RecorderPermissionPrompts(recorder: RecorderViewModel) {
    val requester = rememberPermissionRequester(onResult = recorder::onPermissionResult)
    LaunchedEffect(recorder, requester) {
        recorder.permissionRequests.collect { permission -> requester.request(permission) }
    }
}

@Composable
private fun HomeEntry(recorder: RecorderViewModel) {
    val recorderState by recorder.state.collectAsState()
    val controls =
        remember(recorderState, recorder) {
            TodayControls(recorder = TodayRecorder(recorderState, gesture = recorder, actions = recorder))
        }
    HomeScreen(
        pagingLocked = recorderState.locksPaging,
        today = { listState -> TodayEntry(listState, controls) },
        flow = { listState -> FlowEntry(listState) },
    )
}

@Composable
private fun TodayEntry(
    listState: TransformingLazyColumnState,
    controls: TodayControls,
) {
    val viewModel = koinViewModel<TodayViewModel>()
    val state by viewModel.uiState.collectAsState()
    val handoff by viewModel.handoff.collectAsState()
    TodayPage(
        state = state,
        handoff = handoff,
        onGetPhoneApp = viewModel::getPhoneApp,
        onHandoffDismissed = viewModel::dismissHandoff,
        controls = controls,
        listState = listState,
    )
}

@Composable
private fun FlowEntry(listState: TransformingLazyColumnState) {
    val viewModel = koinViewModel<FlowViewModel>()
    val state by viewModel.uiState.collectAsState()
    FlowPage(state = state, listState = listState)
}
