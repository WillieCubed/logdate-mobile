@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.step

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private val HeroSize = 88.dp
private val HeroIconSize = 44.dp

/** A tonal circle for a [StepScaffold] hero, holding an icon, a monogram, or similar mark. */
@Composable
fun StepHero(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .size(HeroSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimaryContainer) {
            content()
        }
    }
}

/** A [StepHero] holding a single icon. The icon is decorative; the step title carries meaning. */
@Composable
fun StepHeroIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    StepHero(modifier = modifier) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(HeroIconSize))
    }
}

/**
 * The primary action of a step, which may be waiting on work it started.
 *
 * While [busy], the label keeps its place at zero alpha under a spinner, so the button never changes
 * size and nothing around it shifts. The label stays in the semantics tree for screen readers.
 */
@Composable
fun StepBusyButton(
    text: String,
    onClick: () -> Unit,
    busy: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, modifier = Modifier.alpha(if (busy) 0f else 1f))
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
            }
        }
    }
}
