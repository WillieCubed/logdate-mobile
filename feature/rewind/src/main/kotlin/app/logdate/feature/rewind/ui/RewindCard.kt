@file:OptIn(ExperimentalUuidApi::class)

package app.logdate.feature.rewind.ui

import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import kotlinx.datetime.LocalDate
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Composable
internal fun RewindCard(
    id: Uuid,
    label: String,
    title: String,
    start: LocalDate,
    end: LocalDate,
    onOpenRewind: RewindOpenCallback,
    modifier: Modifier = Modifier,
    isReady: Boolean = false,
) {
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium
            )
            .padding(Spacing.lg)
            .clickable(isReady) { onOpenRewind(id) }
            .widthIn(min = 360.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalAlignment = Alignment.Start,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            horizontalAlignment = Alignment.Start,
        ) {// Title Block
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
            )
            Row {
                // TODO: Add start and end dates
                // Format to be like "November 4
//                Text(
//                    text = start.format(DateTimeFormatBuilder)
//                )
                // If the month is different, display month.
                // If the year is different from the start, display the year.
                // Otherwise, just display the date.
            }
        }
        LazyHorizontalGrid(rows = GridCells.Adaptive(minSize = 100.dp)) {
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RewindCardPreview() {
    LogDateTheme {
        RewindCard(
            id = Uuid.random(),
            label = "Rewind 2024#01",
            title = "Just another week",
            onOpenRewind = { },
            start = LocalDate(2024, 11, 4),
            end = LocalDate(2024, 11, 4),
        )
    }
}

@Composable
fun RewindCardPlaceholder(
    modifier: Modifier = Modifier,
) {
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
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium
            )
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalAlignment = Alignment.Start,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            horizontalAlignment = Alignment.Start,
        ) {// Title Block
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.secondary.copy(alpha = alpha),
                        MaterialTheme.shapes.small,
                    )
                    .padding(Spacing.sm)
                    .height(20.dp)
                    .width(108.dp)
            )
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.secondary.copy(alpha = alpha),
                        MaterialTheme.shapes.small,
                    )
                    .padding(Spacing.sm)
                    .height(32.dp)
                    .width(240.dp)
            )
        }
        LazyHorizontalGrid(rows = GridCells.Adaptive(minSize = 100.dp)) {
        }
    }
}

@Preview
@Composable
private fun RewindCardPlaceholderPreview() {
    LogDateTheme {
        RewindCardPlaceholder()
    }
}


enum class RewindBlockType {
    Image,
    Place,
}

sealed class RewindCardBlock(
    val type: RewindBlockType,
) {
    data class ImageBlock(val uri: Uri) : RewindCardBlock(RewindBlockType.Image)
    data class PlaceBlock(val place: String) : RewindCardBlock(RewindBlockType.Place)
}

@Composable
fun GridItem(block: RewindCardBlock) {
    when (block) {
        is RewindCardBlock.ImageBlock -> {

        }

        is RewindCardBlock.PlaceBlock -> {
        }
    }
}

/**
 * Callback for when a rewind is opened.
 */
@OptIn(ExperimentalUuidApi::class)
typealias RewindOpenCallback = (rewindId: Uuid) -> Unit
