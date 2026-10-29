@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.timeline.newstuff

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing
import logdate.client.ui.generated.resources.Res
import logdate.client.ui.generated.resources.timeline_open_full_entry
import org.jetbrains.compose.resources.stringResource

/** Text and its pending state use the same memory-block surface and framing. */
@Composable
internal fun TimelineMemoryTextBlock(
    text: String?,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    maxLines: Int = 4,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    onClick: (() -> Unit)? = null,
) {
    val clickLabel = stringResource(Res.string.timeline_open_full_entry)
    val framing = if (emphasized) Spacing.lg else Spacing.md
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(if (emphasized) 24.dp else 16.dp),
        modifier =
            modifier.fillMaxWidth().then(
                if (onClick == null) Modifier else Modifier.clickable(onClickLabel = clickLabel, onClick = onClick),
            ),
    ) {
        if (text != null) {
            Text(
                text = text,
                style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(framing),
            )
        } else {
            Column(Modifier.padding(framing), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(0.88f, 1f, 0.62f).forEach { width ->
                    Surface(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth(width).height(12.dp),
                    ) {}
                }
            }
        }
    }
}
