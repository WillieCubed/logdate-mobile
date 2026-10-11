@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.journals.ui.merge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.repository.journals.JournalMergeOperation
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.ui.common.PlatformBackHandler
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun JournalMergeScreen(
    sourceId: Uuid,
    pendingOperationId: Uuid? = null,
    onBack: () -> Unit,
    onJournalMerged: (JournalMergeOperation) -> Unit,
    flags: FeatureFlagStore = koinInject(),
    config: LogDateConfigRepository = koinInject(),
    viewModel: JournalMergeViewModel = koinViewModel(),
) {
    val enabled by produceState<Boolean?>(null, flags, config, pendingOperationId) {
        if (pendingOperationId != null) {
            value = true
        } else {
            journalMergeAvailability(flags, config).collect { value = it }
        }
    }
    LaunchedEffect(enabled, sourceId, pendingOperationId) {
        if (enabled == true) viewModel.open(sourceId, pendingOperationId)
        if (enabled == false) onBack()
    }
    val state by viewModel.uiState.collectAsState()
    val back: () -> Unit = {
        when {
            state.stage == JournalMergeStage.Picker && state.editingSelection -> viewModel.restoreReview()
            state.stage is JournalMergeStage.Review -> viewModel.changeDestination(returnToReview = false)
            state.stage == JournalMergeStage.Picker -> onBack()
        }
    }
    PlatformBackHandler(enabled = state.stage != JournalMergeStage.Picker || state.editingSelection, onBack = back)
    if (enabled != true) return
    JournalMergePresentation(state, onJournalMerged) { presented, onSettled ->
        JournalMergeScreenContent(
            state = presented,
            onBack = back,
            onQueryChange = viewModel::updateQuery,
            onChoose = viewModel::choose,
            onChangeDestination = { viewModel.changeDestination() },
            onChangeSource = viewModel::changeSource,
            onConfirm = viewModel::confirm,
            onCancel = onBack,
            onMergeAnimationFinished = onSettled,
        )
    }
}
