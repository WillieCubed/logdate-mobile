@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.layout.EntryPageRailMaxWidth
import app.logdate.ui.theme.Spacing
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.retake
import logdate.client.feature.editor.generated.resources.use_photo
import logdate.client.feature.editor.generated.resources.use_video
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CameraReviewActions(
    mediaType: CapturedMediaType,
    onRetake: () -> Unit,
    onUse: () -> Unit,
    modifier: Modifier = Modifier,
    safeInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(safeInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .cameraCreationClearance(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier
                    .widthIn(max = EntryPageRailMaxWidth)
                    .fillMaxWidth()
                    .padding(Spacing.lg)
                    .testTag("camera_review_surface"),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                Modifier.padding(Spacing.lg),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onRetake, Modifier.weight(1f)) { Text(stringResource(Res.string.retake)) }
                Spacer(Modifier.width(16.dp))
                Button(onUse, Modifier.weight(1f)) {
                    Text(stringResource(if (mediaType == CapturedMediaType.PHOTO) Res.string.use_photo else Res.string.use_video))
                }
            }
        }
    }
}
