@file:OptIn(ExperimentalLayoutApi::class)
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.timeline.newstuff

import androidx.compose.animation.animateBounds
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.ui.audio.MomentAudioCard
import app.logdate.ui.profiles.PersonUiState
import app.logdate.ui.theme.Spacing
import app.logdate.ui.timeline.DayPresentation
import app.logdate.ui.timeline.MomentMediaUiState
import app.logdate.ui.timeline.MomentUiState
import app.logdate.ui.timeline.TimelineDayUiState
import app.logdate.ui.timeline.TimelineMediaItemUiState
import coil3.compose.AsyncImage
import logdate.client.ui.generated.resources.Res
import logdate.client.ui.generated.resources.timeline_moment_photo_count
import logdate.client.ui.generated.resources.timeline_open_full_entry
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

@Composable
internal fun SemanticTimelineDayContent(
    item: TimelineDayUiState,
    style: TimelineDayStyle,
    layoutMode: TimelineDayLayoutMode,
    modifier: Modifier = Modifier,
    pendingMomentContent: @Composable (MarkedMoment) -> Unit,
    onOpenTextNote: (Uuid) -> Unit = {},
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
                key(moment.id) {
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
                                pendingMomentContent = pendingMomentContent,
                                onOpenTextNote = onOpenTextNote,
                            )
                        DayPresentation.STACKED ->
                            StackedMomentCard(
                                moment = moment,
                                style = style,
                                layoutMode = layoutMode,
                                modifier = itemModifier,
                                pendingMomentContent = pendingMomentContent,
                                onOpenTextNote = onOpenTextNote,
                            )
                    }
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
    pendingMomentContent: @Composable (MarkedMoment) -> Unit,
    onOpenTextNote: (Uuid) -> Unit,
) {
    if (moment.isHero) {
        HeroMomentContainer(moment = moment, style = style, layoutMode = layoutMode, modifier = modifier, onOpenTextNote = onOpenTextNote)
    } else {
        SupportingMomentContainer(
            moment = moment,
            style = style,
            layoutMode = layoutMode,
            modifier = modifier,
            pendingMomentContent = pendingMomentContent,
            onOpenTextNote = onOpenTextNote,
        )
    }
}

@Composable
private fun HeroMomentContainer(
    moment: MomentUiState,
    style: TimelineDayStyle,
    layoutMode: TimelineDayLayoutMode,
    modifier: Modifier = Modifier,
    onOpenTextNote: (Uuid) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) {
        MomentHeader(moment = moment, timeOfDay = null, color = style.accentColor)

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
                                    if (moment.media.size > 1) {
                                        " \u00b7 " + stringResource(Res.string.timeline_moment_photo_count, moment.media.size)
                                    } else {
                                        ""
                                    },
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
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .openTextEntryOnClick(moment.openTextEntryAction(onOpenTextNote)),
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
                TextQuoteContainer(text = snippet, onClick = moment.openTextEntryAction(onOpenTextNote))
            }
        }

        moment.audio?.let { audio -> MomentAudioCard(audio = audio, timeOfDay = moment.timeOfDay) }
        PersonAvatarRow(people = moment.people)
    }
}

@Composable
private fun SupportingMomentContainer(
    moment: MomentUiState,
    style: TimelineDayStyle,
    layoutMode: TimelineDayLayoutMode,
    modifier: Modifier = Modifier,
    pendingMomentContent: @Composable (MarkedMoment) -> Unit,
    onOpenTextNote: (Uuid) -> Unit,
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
        val pendingMark = moment.pendingMark
        if (pendingMark != null) {
            pendingMomentContent(pendingMark)
        } else {
            MomentHeader(moment = moment, timeOfDay = moment.timeOfDay, color = style.accentColor)

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
                        TextQuoteContainer(
                            text = moment.textSnippet.orEmpty(),
                            maxLines = 5,
                            onClick = moment.openTextEntryAction(onOpenTextNote),
                        )
                        PersonAvatarRow(people = moment.people)
                    }
                }
            } else {
                if (moment.media.isNotEmpty()) {
                    MomentMediaGrid(media = moment.media, layoutMode = layoutMode, emphasized = false)
                }
                moment.audio?.let { audio -> MomentAudioCard(audio = audio, timeOfDay = moment.timeOfDay) }
                moment.textSnippet?.let { snippet ->
                    TextQuoteContainer(text = snippet, maxLines = 6, onClick = moment.openTextEntryAction(onOpenTextNote))
                }
                PersonAvatarRow(people = moment.people)
            }
        }
    }
}

@Composable
private fun StackedMomentCard(
    moment: MomentUiState,
    style: TimelineDayStyle,
    layoutMode: TimelineDayLayoutMode,
    modifier: Modifier = Modifier,
    pendingMomentContent: @Composable (MarkedMoment) -> Unit,
    onOpenTextNote: (Uuid) -> Unit,
) {
    moment.pendingMark?.let { mark ->
        Box(modifier = modifier) { pendingMomentContent(mark) }
        return
    }
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
            MomentHeader(moment = moment, timeOfDay = moment.timeOfDay, color = style.accentColor)
            if (moment.media.isNotEmpty()) {
                MomentMediaGrid(media = moment.media, layoutMode = layoutMode, emphasized = moment.isHero)
            }
            moment.audio?.let { audio -> MomentAudioCard(audio = audio, timeOfDay = moment.timeOfDay) }
            moment.textSnippet?.let { snippet ->
                Text(
                    text = snippet,
                    style = if (moment.isHero) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    maxLines = if (layoutMode == TimelineDayLayoutMode.COMPACT) 4 else 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.openTextEntryOnClick(moment.openTextEntryAction(onOpenTextNote)),
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
    onClick: (() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(16.dp),
        modifier =
            modifier.fillMaxWidth().openTextEntryOnClick(onClick),
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

private fun MomentUiState.openTextEntryAction(onOpenTextNote: (Uuid) -> Unit): (() -> Unit)? =
    textNoteId?.let { id -> { onOpenTextNote(id) } }

@Composable
private fun Modifier.openTextEntryOnClick(onClick: (() -> Unit)?): Modifier =
    if (onClick == null) this else clickable(onClickLabel = stringResource(Res.string.timeline_open_full_entry), onClick = onClick)

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
                // A lone photo derives its height from its own width, so a portrait ratio that
                // reads well on a phone grows taller than the viewport once the column widens.
                // Widen the ratio as the column does to keep the whole photo on screen.
                aspectRatio =
                    when {
                        layoutMode == TimelineDayLayoutMode.COMPACT && emphasized -> 1.2f
                        layoutMode == TimelineDayLayoutMode.COMPACT -> 1.05f
                        emphasized -> 1.5f
                        else -> 1.35f
                    },
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
