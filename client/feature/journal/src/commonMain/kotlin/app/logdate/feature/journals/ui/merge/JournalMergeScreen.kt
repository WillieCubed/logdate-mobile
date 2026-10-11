@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.logdate.feature.journals.ui.merge

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.logdate.client.repository.journals.JournalMergePreview
import app.logdate.feature.journals.ui.JournalCoverTransition
import app.logdate.feature.journals.ui.LocalJournalCoverTransition
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import app.logdate.ui.LocalSharedTransitionScope
import app.logdate.ui.platform.rememberSystemReduceMotion
import app.logdate.ui.theme.Spacing
import logdate.client.feature.journal.generated.resources.Res
import logdate.client.feature.journal.generated.resources.journal_merge_back
import logdate.client.feature.journal.generated.resources.journal_merge_choose_title
import logdate.client.feature.journal.generated.resources.journal_merge_review_title
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JournalMergeScreenContent(
    state: JournalMergeUiState,
    onBack: () -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onChoose: (Uuid) -> Unit = {},
    onChangeDestination: () -> Unit = {},
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier,
    onMergeAnimationFinished: () -> Unit = {},
    onChangeSource: () -> Unit = {},
) {
    val picker = state.stage == JournalMergeStage.Picker
    val merging = state.stage is JournalMergeStage.Merging
    val title = stringResource(if (picker) Res.string.journal_merge_choose_title else Res.string.journal_merge_review_title)
    Surface(modifier.fillMaxSize().testTag("journal_merge_activity"), color = MaterialTheme.colorScheme.surfaceContainer) {
        MergeSafeRegion(Modifier.safeDrawingPadding().imePadding()) {
            Box(Modifier.widthIn(max = 1120.dp).fillMaxSize().semantics { paneTitle = title }) {
                MergeActivityBody(
                    state,
                    onQueryChange,
                    onChoose,
                    onChangeDestination,
                    onConfirm,
                    onCancel,
                    Modifier.fillMaxSize(),
                    onMergeAnimationFinished,
                    onChangeSource,
                )
                FilledTonalIconButton(
                    onClick = onBack,
                    enabled = !merging,
                    modifier = Modifier.align(Alignment.TopStart).padding(Spacing.lg).size(IconButtonDefaults.mediumContainerSize()),
                    shapes =
                        IconButtonDefaults.shapes(
                            shape = IconButtonDefaults.mediumSquareShape,
                            pressedShape = IconButtonDefaults.mediumPressedShape,
                        ),
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        stringResource(Res.string.journal_merge_back),
                        Modifier.size(IconButtonDefaults.mediumIconSize),
                    )
                }
            }
        }
    }
}

@Composable
private fun MergeActivityBody(
    state: JournalMergeUiState,
    onQueryChange: (String) -> Unit,
    onChoose: (Uuid) -> Unit,
    onChangeDestination: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier,
    onMergeAnimationFinished: () -> Unit,
    onChangeSource: () -> Unit,
) {
    if (LocalSharedTransitionScope.current == null) {
        SharedTransitionLayout(modifier) {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                MergeActivitySteps(
                    state,
                    onQueryChange,
                    onChoose,
                    onChangeDestination,
                    onConfirm,
                    onCancel,
                    Modifier.fillMaxSize(),
                    onMergeAnimationFinished,
                    onChangeSource,
                )
            }
        }
    } else {
        MergeActivitySteps(
            state,
            onQueryChange,
            onChoose,
            onChangeDestination,
            onConfirm,
            onCancel,
            modifier,
            onMergeAnimationFinished,
            onChangeSource,
        )
    }
}

@Composable
private fun MergeActivitySteps(
    state: JournalMergeUiState,
    onQueryChange: (String) -> Unit,
    onChoose: (Uuid) -> Unit,
    onChangeDestination: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier,
    onMergeAnimationFinished: () -> Unit,
    onChangeSource: () -> Unit,
) {
    val sourceScroll = rememberLazyGridState()
    val destinationScroll = rememberLazyGridState()
    val pickerScroll = if (state.pickerTarget == JournalMergePickerTarget.Source) sourceScroll else destinationScroll
    var lastPreview by remember { mutableStateOf<JournalMergePreview?>(null) }
    val preview =
        when (val stage = state.stage) {
            is JournalMergeStage.Review -> stage.preview
            is JournalMergeStage.Merging -> stage.preview
            else -> null
        }
    SideEffect { if (preview != null) lastPreview = preview }
    val navigationVisibility = LocalNavAnimatedVisibilityScope.current
    val reduced by rememberSystemReduceMotion()
    val duration = if (reduced) 0 else 350
    AnimatedContent(
        targetState = state.stage == JournalMergeStage.Picker,
        modifier = modifier.fillMaxSize(),
        transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) },
        label = "Journal merge steps",
    ) { picker ->
        val visible =
            transition.targetState == EnterExitState.Visible &&
                (navigationVisibility == null || navigationVisibility.transition.targetState == EnterExitState.Visible)
        CompositionLocalProvider(LocalJournalCoverTransition provides JournalCoverTransition(visible, duration)) {
            if (picker) {
                JournalMergeDestinationPicker(state, onQueryChange, onChoose, showSource = true, listState = pickerScroll)
            } else {
                (preview ?: lastPreview)?.let { selected ->
                    JournalMergeReviewLayout(
                        state,
                        selected,
                        onChangeDestination,
                        onConfirm,
                        onCancel,
                        onMergeAnimationFinished,
                        onChangeSource = onChangeSource,
                    )
                }
            }
        }
    }
}
