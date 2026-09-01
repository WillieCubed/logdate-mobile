@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing

/**
 * What a [MessageBanner] shows. Kept nullable at the call site (rather than a separate
 * `visible: Boolean`) so [MessageBanner] can render the fade/slide-out using the last known
 * content instead of going blank the instant the source state disappears.
 */
data class BannerContent(
    val message: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val action: Pair<String, () -> Unit>? = null,
    val dismissible: Boolean = false,
)

/**
 * Tonal, animated banner for surfacing something that needs the user's attention. Shared shell
 * behind [app.logdate.ui.sync.SyncErrorBanner] and other feature-specific banners so they share
 * one visual and motion language instead of each hand-rolling their own.
 */
@Composable
fun MessageBanner(
    content: BannerContent?,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
) {
    AnimatedVisibility(
        visible = content != null,
        // Slight bounce on enter — MD3 Expressive's voice. Snappy on exit so it doesn't linger.
        enter =
            slideInVertically(
                initialOffsetY = { -it },
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
            ) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(),
        modifier = modifier,
    ) {
        if (content == null) return@AnimatedVisibility
        Surface(
            color = content.containerColor,
            contentColor = content.contentColor,
            // MD3 extra-large container radius (28dp). Uniform rounding reads cleaner here
            // than the asymmetric variant — the banner already has plenty of presence from
            // its tonal color and width; bending the corners adds noise without meaning.
            shape = MaterialTheme.shapes.extraLarge,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Icon(
                    imageVector = content.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                    Text(
                        text = content.message,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                content.action?.let { (label, onAction) ->
                    TextButton(onClick = onAction) {
                        Text(label)
                    }
                }
                if (content.dismissible) {
                    TextButton(onClick = onDismiss) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}
