package app.logdate.feature.journals.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.dp
import app.logdate.shared.model.Journal
import app.logdate.ui.theme.Spacing
import kotlinx.datetime.Clock
import org.jetbrains.compose.ui.tooling.preview.Preview
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
    modifier: Modifier = Modifier,
) {
    if (journals.isEmpty()) {
        return
    }

    val listState = rememberLazyListState()

    // Calculate which item is the center/focused item
    val centralItemIndex by remember {
        derivedStateOf {
            val firstItemIndex = listState.firstVisibleItemIndex
            val firstItemOffset = listState.firstVisibleItemScrollOffset

            // If we're scrolled more than halfway through the first visible item,
            // the next item is more central
            if (firstItemOffset > 120) {
                (firstItemIndex + 1).coerceAtMost(journals.lastIndex)
            } else {
                firstItemIndex
            }
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        contentPadding = PaddingValues(
            horizontal = Spacing.xxxl,
            vertical = Spacing.lg
        )
    ) {
        itemsIndexed(journals) { index, item ->
            val isCentralItem = index == centralItemIndex

            // Scale animation for the focused item
            val targetScale = if (isCentralItem) 1.15f else 0.9f

            val scale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing
                ),
                label = "Cover Scale Animation"
            )

            when (item) {
                is JournalListItemUiState.ExistingJournal -> {
                    AnimatedContent(true) {
                        JournalCover(
                            journal = item.data,
                            onClick = { onOpenJournal(item.data.id) },
                            backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier
                                .scale(scale)
                                .widthIn(min = 240.dp)
                                .heightIn(max = 400.dp),
                        )
                    }
                }

                is JournalListItemUiState.CreateJournalPlaceholder -> {
                    CreateJournalPlaceholder(
                        onClick = onCreateJournal,
                        modifier = Modifier
                            .scale(scale)
                            .widthIn(min = 240.dp)
                            .heightIn(max = 400.dp),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun JournalCoverFlowPreview() {
    JournalCoverFlowCarousel(
        journals = listOf(
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