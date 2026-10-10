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
    LaunchedEffect(state.stage) {
        (state.stage as? JournalMergeStage.Complete)?.let { onJournalMerged(it.operation) }
    }
    PlatformBackHandler(enabled = state.stage is JournalMergeStage.Review || state.stage is JournalMergeStage.Merging) {
        viewModel.changeDestination()
    }
    if (enabled != true) return
    JournalMergeScreenContent(
        state = state,
        onBack = { if (state.stage is JournalMergeStage.Review) viewModel.changeDestination() else onBack() },
        onQueryChange = viewModel::updateQuery,
        onChoose = viewModel::choose,
        onChangeDestination = viewModel::changeDestination,
        onConfirm = viewModel::confirm,
        onCancel = onBack,
    )
}
