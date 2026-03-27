@file:OptIn(ExperimentalLayoutApi::class)
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.timeline.newstuff

import androidx.compose.animation.animateBounds
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.formatting.asRelativeDate
import app.logdate.ui.profiles.PersonUiState
import app.logdate.ui.theme.Spacing
import app.logdate.ui.timeline.DayPresentation
import app.logdate.ui.timeline.MomentAudioUiState
import app.logdate.ui.timeline.MomentMediaUiState
import app.logdate.ui.timeline.MomentUiState
import app.logdate.ui.timeline.TimelineDayUiState
import app.logdate.ui.timeline.TimelineMediaItemUiState
import coil3.compose.AsyncImage
import logdate.client.ui.generated.resources.Res
import logdate.client.ui.generated.resources.voice_note
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SemanticTimelineDayHeader(
    item: TimelineDayUiState,
    style: TimelineDayStyle,
    layoutMode: TimelineDayLayoutMode,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier,
    ) {
        Text(
            text = item.date.asRelativeDate(),
            style = MaterialTheme.typography.labelLarge,
            color = style.accentColor,
            fontWeight = FontWeight.SemiBold,
        )
        item.supportingSummary?.let { summary ->
            Text(
                text = summary,
                style =
                    when (layoutMode) {
                        TimelineDayLayoutMode.COMPACT -> MaterialTheme.typography.titleLarge
                        TimelineDayLayoutMode.MEDIUM -> MaterialTheme.typography.headlineSmall
                        TimelineDayLayoutMode.EXPANDED -> MaterialTheme.typography.headlineMedium
                    },
                maxLines = if (layoutMode == TimelineDayLayoutMode.COMPACT) 3 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun SemanticTimelineDayContent(
    item: TimelineDayUiState,
    style: TimelineDayStyle,
    layoutMode: TimelineDayLayoutMode,
    modifier: Modifier = Modifier,
) {
    val isCompact = layoutMode == TimelineDayLayoutMode.COMPACT
    val gap =
        when (layoutMode) {
            TimelineDayLayoutMode.COMPACT -> Spacing.lg
            TimelineDayLayoutMode.MEDIUM -> Spacing.lg
            TimelineDayLayoutMode.EXPANDED -> Spacing.xl
        }
    val momentMinWidth =
        when (layoutMode) {
            TimelineDayLayoutMode.COMPACT -> 0.dp
            TimelineDayLayoutMode.MEDIUM -> 260.dp
            TimelineDayLayoutMode.EXPANDED -> 300.dp
        }

    LookaheadScope {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            modifier = modifier.fillMaxWidth(),
        ) {
            item.moments.forEach { moment ->
                val itemModifier =
                    if (moment.isHero || isCompact) {
                        Modifier
                            .fillMaxWidth()
                            .animateBounds(this@LookaheadScope)
                    } else {
                        Modifier
                            .widthIn(min = momentMinWidth)
                            .weight(1f, fill = true)
                            .animateBounds(this@LookaheadScope)
                    }

                when (item.dayPresentation) {
                    DayPresentation.FLOWING ->
                        FlowingMomentItem(
                            moment = moment,
                            style = style,
                            layoutMode = layoutMode,
                            modifier = itemModifier,
                        )
                    DayPresentation.STACKED ->
                        StackedMomentCard(
                            moment = moment,
                            style = style,
                            layoutMode = layoutMode,
                            modifier = itemModifier,
                        )
                }
            }
        }
    }
}

@Composable
private fun FlowingMomentItem(
    moment: MomentUiState,
    style: TimelineDayStyle,
    layoutMode: TimelineDayLayoutMode,
    modifier: Modifier = Modifier,
) {
    if (moment.isHero) {
        HeroMomentContainer(moment = moment, style = style, layoutMode = layoutMode, modifier = modifier)
    } else {
        SupportingMomentContainer(moment = moment, style = style, layoutMode = layoutMode, modifier = modifier)
    }
}

@Composable
private fun HeroMomentContainer(
    moment: MomentUiState,
    style: TimelineDayStyle,
    layoutMode: TimelineDayLayoutMode,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) {
        if (moment.label.isNotBlank()) {
            MomentLabel(label = moment.label, timeOfDay = null, color = style.accentColor)
        }

        if (moment.media.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))) {
                MomentMediaGrid(media = moment.media, layoutMode = layoutMode, emphasized = true)
                if (moment.places.isNotEmpty()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    ) {
                        Text(
                            text =
                                moment.places.first().title +
                                    if (moment.media.size > 1) " \u00b7 ${moment.media.size} photos" else "",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                    }
                }
            }
        }

        moment.textSnippet?.let { snippet ->
            if (moment.media.isEmpty()) {
                // Text-only hero: large quote in primaryContainer
                Surface(
                    color = style.softAccentColor,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(Spacing.lg),
                    )
                }
            } else {
                TextQuoteContainer(text = snippet)
            }
        }

        moment.audio?.let { audio -> AudioMomentCard(audio = audio, style = style) }
        PersonAvatarRow(people = moment.people)
    }
}

@Composable
private fun SupportingMomentContainer(
    moment: MomentUiState,
    style: TimelineDayStyle,
    layoutMode: TimelineDayLayoutMode,
    modifier: Modifier = Modifier,
) {
    val hasSingleMedia = moment.media.size == 1
    val hasText = moment.textSnippet != null
    val useInlineLayout = hasSingleMedia && hasText
    val imageWeight =
        when (layoutMode) {
            TimelineDayLayoutMode.COMPACT -> 0.4f
            TimelineDayLayoutMode.MEDIUM -> 0.35f
            TimelineDayLayoutMode.EXPANDED -> 0.3f
        }

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) {
        if (moment.label.isNotBlank()) {
            MomentLabel(label = moment.label, timeOfDay = moment.timeOfDay, color = style.accentColor)
        }

        if (useInlineLayout) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TimelineMediaTile(
                    media = TimelineMediaItemUiState(uri = moment.media.first().uri, isVideo = moment.media.first().isVideo),
                    aspectRatio = 1f,
                    modifier = Modifier.weight(imageWeight),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.weight(1f - imageWeight),
                ) {
                    TextQuoteContainer(text = moment.textSnippet.orEmpty(), maxLines = 5)
                    PersonAvatarRow(people = moment.people)
                }
            }
        } else {
            if (moment.media.isNotEmpty()) {
                MomentMediaGrid(media = moment.media, layoutMode = layoutMode, emphasized = false)
            }
            moment.audio?.let { audio -> AudioMomentCard(audio = audio, style = style) }
            moment.textSnippet?.let { snippet -> TextQuoteContainer(text = snippet, maxLines = 6) }
            PersonAvatarRow(people = moment.people)
        }
    }
}

@Composable
private fun StackedMomentCard(
    moment: MomentUiState,
    style: TimelineDayStyle,
    layoutMode: TimelineDayLayoutMode,
    modifier: Modifier = Modifier,
) {
    val cardPadding =
        when (layoutMode) {
            TimelineDayLayoutMode.COMPACT -> Spacing.md
            TimelineDayLayoutMode.MEDIUM -> Spacing.lg
            TimelineDayLayoutMode.EXPANDED -> Spacing.xl
        }
    Surface(
        color = if (moment.isHero) style.softAccentColor else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(if (moment.isHero) 28.dp else 20.dp),
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(cardPadding),
        ) {
            if (moment.label.isNotBlank()) {
                MomentLabel(label = moment.label, timeOfDay = moment.timeOfDay, color = style.accentColor)
            }
            if (moment.media.isNotEmpty()) {
                MomentMediaGrid(media = moment.media, layoutMode = layoutMode, emphasized = moment.isHero)
            }
            moment.audio?.let { audio -> AudioMomentCard(audio = audio, style = style) }
            moment.textSnippet?.let { snippet ->
                Text(
                    text = snippet,
                    style = if (moment.isHero) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    maxLines = if (layoutMode == TimelineDayLayoutMode.COMPACT) 4 else 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PersonAvatarRow(people = moment.people)
        }
    }
}

// region MD3 Expressive components

@Composable
private fun TextQuoteContainer(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 4,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(Spacing.md),
        )
    }
}

@Composable
private fun PersonAvatarRow(
    people: List<PersonUiState>,
    modifier: Modifier = Modifier,
) {
    if (people.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        people.take(4).forEach { person ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (person.photoUri != null) {
                    AsyncImage(
                        model = person.photoUri,
                        contentDescription = person.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(24.dp).clip(CircleShape),
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = person.name.take(1).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (people.size > 4) {
            Text(
                text = "+${people.size - 4}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AudioMomentCard(
    audio: MomentAudioUiState,
    style: TimelineDayStyle,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(Spacing.md),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(style.accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = style.accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(Res.string.voice_note),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    text = audio.durationMs.toDurationLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TimelineTranscriptPreview(
                noteId = audio.noteId,
                fallbackTranscript = audio.transcript,
            )
            AudioWaveBars(accentColor = style.accentColor, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MomentLabel(
    label: String,
    timeOfDay: String?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (label.isBlank()) return
    val displayText =
        if (timeOfDay != null && !label.lowercase().contains(timeOfDay)) {
            "$label \u00b7 $timeOfDay"
        } else {
            label
        }
    Text(
        text = displayText,
        style = MaterialTheme.typography.titleSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

@Composable
internal fun MomentMediaGrid(
    media: List<MomentMediaUiState>,
    layoutMode: TimelineDayLayoutMode,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
) {
    when (media.size) {
        0 -> Unit
        1 ->
            TimelineMediaTile(
                media = TimelineMediaItemUiState(uri = media.first().uri, isVideo = media.first().isVideo),
                aspectRatio = if (emphasized) 1.2f else 1.05f,
                modifier = modifier.fillMaxWidth(),
            )
        2 ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = modifier.fillMaxWidth(),
            ) {
                TimelineMediaTile(
                    media = TimelineMediaItemUiState(uri = media[0].uri, isVideo = media[0].isVideo),
                    aspectRatio = if (emphasized) 0.95f else 1f,
                    modifier = Modifier.weight(1.2f),
                )
                TimelineMediaTile(
                    media = TimelineMediaItemUiState(uri = media[1].uri, isVideo = media[1].isVideo),
                    aspectRatio = if (layoutMode == TimelineDayLayoutMode.COMPACT) 0.95f else 1.15f,
                    modifier = Modifier.weight(1f),
                )
            }
        else ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = modifier.fillMaxWidth(),
            ) {
                TimelineMediaTile(
                    media = TimelineMediaItemUiState(uri = media.first().uri, isVideo = media.first().isVideo),
                    aspectRatio = if (emphasized) 0.9f else 1.05f,
                    modifier = Modifier.weight(1.35f),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.weight(1f),
                ) {
                    media.drop(1).take(2).forEach { item ->
                        TimelineMediaTile(
                            media = TimelineMediaItemUiState(uri = item.uri, isVideo = item.isVideo),
                            aspectRatio = 1.2f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
    }
}

// endregion
