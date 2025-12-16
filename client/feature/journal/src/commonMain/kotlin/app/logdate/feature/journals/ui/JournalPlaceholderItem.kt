package app.logdate.feature.journals.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import app.logdate.ui.common.AspectRatios
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing

@Composable
fun JournalPlaceholderItem() {
    val infiniteTransition = rememberInfiniteTransition(label = "Loading")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.7f at 500
            },
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha",
    )
    Box(
        modifier = Modifier
            .aspectRatio(AspectRatios.JOURNAL_COVER)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = JournalShape,
            )
    )
    {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.Bottom),
            horizontalAlignment = Alignment.Start,
        ) { // Actual content
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.secondary.copy(alpha = alpha),
                        MaterialTheme.shapes.small,
                    )
                    .height(24.dp)
                    .width(108.dp)
            )
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.secondary.copy(alpha = alpha),
                        MaterialTheme.shapes.small,
                    )
                    .height(16.dp)
                    .width(240.dp)
            )

        }
    }
}