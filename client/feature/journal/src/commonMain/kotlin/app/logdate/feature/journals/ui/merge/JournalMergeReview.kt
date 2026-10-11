@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.journals.ui.merge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.client.repository.journals.JournalMergePreview
import app.logdate.ui.theme.Spacing
import logdate.client.feature.journal.generated.resources.Res
import logdate.client.feature.journal.generated.resources.journal_merge_cancel
import logdate.client.feature.journal.generated.resources.journal_merge_changed
import logdate.client.feature.journal.generated.resources.journal_merge_confirm
import logdate.client.feature.journal.generated.resources.journal_merge_failed
import logdate.client.feature.journal.generated.resources.journal_merge_merging
import logdate.client.feature.journal.generated.resources.journal_merge_recovery_safety
import logdate.client.feature.journal.generated.resources.journal_merge_safety
import logdate.client.feature.journal.generated.resources.journal_merge_unavailable
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun JournalMergeIdentityComparison(
    preview: JournalMergePreview,
    merging: Boolean,
    modifier: Modifier = Modifier,
    onMergeAnimationFinished: () -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxWidth().testTag("journal_merge_identities")) {
        val bounded = constraints.hasBoundedHeight
        JournalMergeBooks(
            preview,
            merging,
            if (bounded) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            onMergeAnimationFinished,
        )
    }
}

@Composable
internal fun JournalMergeConsequences(
    recovery: Boolean,
    modifier: Modifier = Modifier,
    spacious: Boolean = false,
) {
    Surface(
        modifier.fillMaxWidth().testTag("journal_merge_consequences"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Icon(Icons.Outlined.Info, null, Modifier.size(24.dp).testTag("journal_merge_info"))
            Text(
                stringResource(if (recovery) Res.string.journal_merge_recovery_safety else Res.string.journal_merge_safety),
                Modifier.weight(1f),
                style = if (spacious) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
internal fun JournalMergeActions(
    state: JournalMergeUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    spacious: Boolean = false,
) {
    val merging = state.stage is JournalMergeStage.Merging
    val failure = remember { BringIntoViewRequester() }
    LaunchedEffect(state.error) {
        if (state.error == JournalMergeError.Failed) failure.bringIntoView()
    }
    Column(
        modifier.fillMaxWidth().testTag("journal_merge_actions").bringIntoViewRequester(failure),
        verticalArrangement = Arrangement.spacedBy(if (spacious) Spacing.lg else Spacing.sm),
    ) {
        state.error?.takeUnless { it == JournalMergeError.Changed }?.let { JournalMergeFeedback(it) }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            val mergeLabel = stringResource(if (merging) Res.string.journal_merge_merging else Res.string.journal_merge_confirm)
            val cancelLabel = stringResource(Res.string.journal_merge_cancel)
            val style = MaterialTheme.typography.titleMedium
            val mergeWidth =
                with(density) {
                    measurer
                        .measure(AnnotatedString(mergeLabel), style)
                        .size.width
                        .toDp()
                } + 48.dp
            val cancelWidth =
                with(density) {
                    measurer
                        .measure(AnnotatedString(cancelLabel), style)
                        .size.width
                        .toDp()
                } + 32.dp
            val primary: @Composable (Modifier) -> Unit = { buttonModifier ->
                Button(onClick = onConfirm, enabled = !merging, modifier = buttonModifier.heightIn(min = 56.dp)) {
                    Text(mergeLabel, style = style)
                }
            }
            val cancel: @Composable (Modifier) -> Unit = { buttonModifier ->
                TextButton(onClick = onCancel, enabled = !merging, modifier = buttonModifier.heightIn(min = 48.dp)) {
                    Text(cancelLabel, style = style)
                }
            }
            if (maxWidth >= mergeWidth + cancelWidth + Spacing.sm) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    cancel(Modifier.widthIn(min = cancelWidth))
                    primary(Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    primary(Modifier.fillMaxWidth())
                    cancel(Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}

@Composable
internal fun JournalMergeFeedback(
    error: JournalMergeError,
    modifier: Modifier = Modifier,
) {
    JournalMergeNotice(
        stringResource(
            when (error) {
                JournalMergeError.Failed -> Res.string.journal_merge_failed
                JournalMergeError.Changed -> Res.string.journal_merge_changed
                JournalMergeError.Unavailable -> Res.string.journal_merge_unavailable
            },
        ),
        error = true,
        modifier = modifier,
    )
}

@Composable
internal fun JournalMergeNotice(
    text: String,
    error: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier.semantics { if (error) liveRegion = LiveRegionMode.Polite },
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
