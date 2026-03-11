@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.journals.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.logdate.shared.model.Journal
import app.logdate.ui.common.AspectRatios
import app.logdate.ui.theme.Spacing
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * A simple horizontal carousel that displays journal covers.
 * The currently focused journal appears larger than the others.
 */
@Composable
fun JournalCoverFlowCarousel(
    journals: List<JournalListItemUiState>,
    onOpenJournal: JournalClickCallback,
    onCreateJournal: () -> Unit,
    maxCardHeight: Dp = Dp.Unspecified,
    modifier: Modifier = Modifier,
) {
    if (journals.isEmpty()) {
        return
    }

    val listState = rememberLazyListState()

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cardMinWidth = 132.dp
        val cardMaxWidth = 208.dp
        val maxCardScale = 1.15f

        val widthFromViewport = (maxWidth * 0.52f).coerceIn(cardMinWidth, cardMaxWidth)
        val widthFromHeight =
            if (maxCardHeight != Dp.Unspecified) {
                val verticalPadding = Spacing.sm * 2
                val baseCardHeight =
                    ((maxCardHeight - verticalPadding).coerceAtLeast(1.dp) / maxCardScale)
                (baseCardHeight * AspectRatios.JOURNAL_COVER).coerceIn(cardMinWidth, cardMaxWidth)
            } else {
                cardMaxWidth
            }
        val carouselCardWidth = minOf(widthFromViewport, widthFromHeight)
        val horizontalContentPadding = (maxWidth * 0.08f).coerceIn(Spacing.md, Spacing.xl)

        // Continuously scale cards based on proximity to the viewport center.
        val scaleByIndex by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                val viewportHalfWidth =
                    ((layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2f).coerceAtLeast(1f)

                layoutInfo.visibleItemsInfo.associate { itemInfo ->
                    val itemCenter = itemInfo.offset + (itemInfo.size / 2f)
                    val normalizedDistance = (abs(itemCenter - viewportCenter) / viewportHalfWidth).coerceIn(0f, 1f)
                    val proximity = 1f - normalizedDistance
                    val easedProximity = proximity * proximity
                    val scale = 0.88f + (maxCardScale - 0.88f) * easedProximity

                    itemInfo.index to scale
                }
            }
        }

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            contentPadding =
                PaddingValues(
                    horizontal = horizontalContentPadding,
                    vertical = Spacing.sm,
                ),
        ) {
            itemsIndexed(journals) { index, item ->
                val targetScale = scaleByIndex[index] ?: 0.88f

                val scale by animateFloatAsState(
                    targetValue = targetScale,
                    animationSpec =
                        tween(
                            durationMillis = 300,
                            easing = FastOutSlowInEasing,
                        ),
                    label = "Cover Scale Animation",
                )

                when (item) {
                    is JournalListItemUiState.ExistingJournal -> {
                        AnimatedContent(true) {
                            JournalCover(
                                journal = item.data,
                                onClick = { onOpenJournal(item.data.id) },
                                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier =
                                    Modifier
                                        .width(carouselCardWidth)
                                        .scale(scale),
                            )
                        }
                    }

                    is JournalListItemUiState.CreateJournalPlaceholder -> {
                        CreateJournalPlaceholder(
                            onClick = onCreateJournal,
                            modifier =
                                Modifier
                                    .width(carouselCardWidth)
                                    .scale(scale),
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun JournalCoverFlowPreview() {
    JournalCoverFlowCarousel(
        journals =
            listOf(
                Journal(
                    id = Uuid.random(),
                    title = "Journal 1",
                    created = Clock.System.now(),
                    description = "Journal 1 description",
                    isFavorited = false,
                    lastUpdated = Clock.System.now(),
                ),
                Journal(
                    id = Uuid.random(),
                    title = "Journal 2",
                    created = Clock.System.now(),
                    description = "Journal 2 description",
                    isFavorited = false,
                    lastUpdated = Clock.System.now(),
                ),
                Journal(
                    id = Uuid.random(),
                    title = "Journal 3",
                    created = Clock.System.now(),
                    description = "Journal 3 description",
                    isFavorited = false,
                    lastUpdated = Clock.System.now(),
                ),
            ).map { JournalListItemUiState.ExistingJournal(it) },
        onOpenJournal = {},
        onCreateJournal = {},
    )
}
