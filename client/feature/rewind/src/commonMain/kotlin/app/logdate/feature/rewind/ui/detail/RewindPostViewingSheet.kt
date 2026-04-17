@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import logdate.client.feature.rewind.generated.resources.Res
import logdate.client.feature.rewind.generated.resources.post_viewing_done
import logdate.client.feature.rewind.generated.resources.post_viewing_share
import logdate.client.feature.rewind.generated.resources.post_viewing_title
import logdate.client.feature.rewind.generated.resources.post_viewing_watch_again
import org.jetbrains.compose.resources.stringResource

/**
 * A bottom sheet shown after the user finishes watching a rewind.
 *
 * Offers three actions: share the rewind, watch it again, or dismiss. This creates
 * a natural moment for sharing (viral growth) or rewatching instead of silently
 * dropping the user back to the overview.
 *
 * @param onShare Invoked when the user taps the share button. The host is expected to
 *   dismiss the sheet itself and hand off to the stats-share pipeline.
 * @param onWatchAgain Invoked when the user taps "Watch again". The host should dismiss
 *   the sheet and bump the story's `restartKey` so the story resets to the first panel.
 * @param onDismiss Invoked when the user taps "Done" or dismisses the sheet via a
 *   swipe/scrim. The host should dismiss the sheet and exit the rewind.
 * @param modifier Modifier for the sheet container
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewindPostViewingSheet(
    onShare: () -> Unit,
    onWatchAgain: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(Res.string.post_viewing_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.post_viewing_share))
            }
            OutlinedButton(
                onClick = onWatchAgain,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.post_viewing_watch_again))
            }
            TextButton(
                onClick = onDismiss,
            ) {
                Text(stringResource(Res.string.post_viewing_done))
            }
        }
    }
}
