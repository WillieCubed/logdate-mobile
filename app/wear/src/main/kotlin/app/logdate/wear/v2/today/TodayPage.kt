package app.logdate.wear.v2.today

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.OpenOnPhoneDialogDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.openOnPhoneDialogCurvedText
import androidx.wear.compose.material3.timeTextCurvedText
import androidx.wear.compose.material3.timeTextSeparator
import app.logdate.client.domain.today.TodaySummary
import app.logdate.client.repository.journals.JournalNote
import app.logdate.ui.glyphs.LogDateGlyph
import app.logdate.ui.glyphs.vector
import app.logdate.ui.streak.Campfire
import app.logdate.ui.streak.CampfirePresentation
import app.logdate.ui.streak.campfireShortDescription
import app.logdate.wear.R
import app.logdate.wear.capture.Feeling
import app.logdate.wear.v2.moments.formatClockDuration
import app.logdate.wear.v2.moments.momentClockTime
import app.logdate.wear.v2.moments.momentLabel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import kotlin.time.Duration

object TodayTags {
    const val PAGE = "today_page"
    const val CAMPFIRE = "today_campfire"
    const val SUMMARY = "today_summary"
    const val LATEST = "today_latest"
    const val GET_PHONE_APP = "today_get_phone_app"
    const val MARK = "today_mark"
    const val ADD = "today_add"
    const val RECORD = "today_record"
}

/**
 * The controls Today shows. Each appears only when its handler is set, and the change that makes a
 * control work is the change that sets its handler, so Today never shows a control that does
 * nothing.
 */
data class TodayControls(
    val onRecord: (() -> Unit)? = null,
    val onMark: (() -> Unit)? = null,
    val onAdd: (() -> Unit)? = null,
)

/**
 * Home's first page: the date in the time text, the campfire, the day's summary line, the latest
 * moment and, when the paired Android phone lacks LogDate, a card that opens its Play listing.
 * Record sits at the end as the edge button, with Mark and + above it.
 */
@Composable
fun TodayPage(
    state: TodayUiState,
    handoff: HandoffState,
    onGetPhoneApp: () -> Unit,
    onHandoffDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    controls: TodayControls = TodayControls(),
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    timeSource: TimeSource = TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat()),
) {
    val timeText: @Composable () -> Unit = { TodayTimeText(state = state, timeSource = timeSource) }
    TodayScaffold(listState = listState, timeText = timeText, onRecord = controls.onRecord) { contentPadding ->
        TodayList(
            state = state,
            handoff = handoff,
            controls = controls,
            onGetPhoneApp = onGetPhoneApp,
            listState = listState,
            contentPadding = contentPadding,
            modifier = modifier,
        )
    }
    val continueOnPhone = OpenOnPhoneDialogDefaults.text
    val curvedTextStyle = OpenOnPhoneDialogDefaults.curvedTextStyle
    OpenOnPhoneDialog(
        visible = handoff == HandoffState.SENT,
        onDismissRequest = onHandoffDismissed,
        curvedText = { openOnPhoneDialogCurvedText(text = continueOnPhone, style = curvedTextStyle) },
    )
}

/**
 * The time text Today shows: the date, when there is one, then the time. [TodayPage] places it in
 * its scaffold, which fades it in; a screenshot places it at the app level to capture it.
 */
@Composable
fun TodayTimeText(
    state: TodayUiState,
    modifier: Modifier = Modifier,
    timeSource: TimeSource = TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat()),
) {
    val dateText = (state as? TodayUiState.Ready)?.let { ready -> todayDateText(ready.date) }
    TimeText(modifier = modifier, timeSource = timeSource) { time ->
        if (dateText != null) {
            timeTextCurvedText(dateText)
            timeTextSeparator()
        }
        timeTextCurvedText(time)
    }
}

@Composable
private fun TodayScaffold(
    listState: TransformingLazyColumnState,
    timeText: @Composable () -> Unit,
    onRecord: (() -> Unit)?,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    if (onRecord == null) {
        ScreenScaffold(scrollState = listState, timeText = timeText, content = content)
        return
    }
    ScreenScaffold(scrollState = listState, edgeButton = { RecordButton(onRecord) }, timeText = timeText, content = content)
}

@Composable
private fun TodayList(
    state: TodayUiState,
    handoff: HandoffState,
    controls: TodayControls,
    onGetPhoneApp: () -> Unit,
    listState: TransformingLazyColumnState,
    contentPadding: PaddingValues,
    modifier: Modifier,
) {
    val spec = rememberTransformationSpec()
    val padding = if (startsWithCampfire(state)) contentPadding else contentPadding.belowTimeText()
    TransformingLazyColumn(state = listState, contentPadding = padding, modifier = modifier.testTag(TodayTags.PAGE)) {
        when (state) {
            TodayUiState.Loading -> Unit
            TodayUiState.Failed -> item { CenteredText(stringResource(R.string.wear_today_failed)) }
            is TodayUiState.Ready -> {
                state.campfire?.let { campfire -> item { TodayCampfire(campfire) } }
                item { CenteredText(summaryText(state.summary, state.feeling), Modifier.testTag(TodayTags.SUMMARY)) }
                state.summary.latestMoment?.let { latest -> item { LatestMoment(latest, state.timeZone) } }
                if (state.phoneCard is PhoneCard.GetApp) {
                    item {
                        GetPhoneAppCard(
                            handoff = handoff,
                            onClick = onGetPhoneApp,
                            modifier = Modifier.transformedHeight(this, spec),
                            transformation = SurfaceTransformation(spec),
                        )
                    }
                }
                if (controls.onMark != null || controls.onAdd != null) item { TodayButtonGroup(controls) }
            }
        }
    }
}

private fun startsWithCampfire(state: TodayUiState): Boolean = state is TodayUiState.Ready && state.campfire != null

/** Text is wider than the campfire, so a list that starts with text clears the curve of the time text. */
@Composable
private fun PaddingValues.belowTimeText(): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        top = calculateTopPadding() + TIME_TEXT_CLEARANCE,
        end = calculateEndPadding(direction),
        bottom = calculateBottomPadding(),
    )
}

@Composable
private fun CenteredText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun TodayCampfire(campfire: CampfirePresentation) {
    Campfire(
        phase = campfire.phase,
        size = campfire.size,
        modifier = Modifier.size(campfireSize).testTag(TodayTags.CAMPFIRE),
        waitingForToday = campfire.isWaitingForToday,
        contentDescription = campfireShortDescription(campfire),
    )
}

@Composable
private fun LatestMoment(
    note: JournalNote,
    timeZone: TimeZone,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TodayTags.LATEST),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = momentClockTime(note.creationTimestamp, timeZone),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = momentLabel(note),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GetPhoneAppCard(
    handoff: HandoffState,
    onClick: () -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    val body = if (handoff == HandoffState.FAILED) R.string.wear_today_get_phone_app_failed else R.string.wear_today_get_phone_app_body
    TitleCard(
        onClick = onClick,
        title = { Text(stringResource(R.string.wear_today_get_phone_app_title)) },
        modifier = modifier.fillMaxWidth().testTag(TodayTags.GET_PHONE_APP),
        enabled = handoff != HandoffState.SENDING,
        transformation = transformation,
    ) {
        Text(stringResource(body))
    }
}

@Composable
private fun TodayButtonGroup(controls: TodayControls) {
    val addLabel = stringResource(R.string.wear_today_add)
    val markLabel = stringResource(R.string.wear_today_mark)
    ButtonGroup(modifier = Modifier.fillMaxWidth()) {
        controls.onMark?.let { onMark ->
            FilledTonalButton(onClick = onMark, modifier = Modifier.weight(1f).testTag(TodayTags.MARK)) {
                CenteredContent { Icon(imageVector = LogDateGlyph.MARK.vector(), contentDescription = markLabel) }
            }
        }
        controls.onAdd?.let { onAdd ->
            FilledTonalButton(
                onClick = onAdd,
                modifier = Modifier.weight(1f).testTag(TodayTags.ADD).semantics { contentDescription = addLabel },
            ) {
                CenteredContent { Text(stringResource(R.string.wear_today_add_symbol)) }
            }
        }
    }
}

@Composable
private fun RowScope.CenteredContent(content: @Composable () -> Unit) {
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun RecordButton(onRecord: () -> Unit) {
    EdgeButton(onClick = onRecord, buttonSize = EdgeButtonSize.Medium, modifier = Modifier.testTag(TodayTags.RECORD)) {
        Icon(imageVector = LogDateGlyph.RECORD.vector(), contentDescription = stringResource(R.string.wear_today_record))
    }
}

@Composable
private fun summaryText(
    summary: TodaySummary,
    feeling: Feeling?,
): String {
    val line = summaryLine(summary)
    return if (feeling == null) line else "${feeling.emoji} $line"
}

@Composable
private fun summaryLine(summary: TodaySummary): String {
    if (summary.momentCount == 0) return stringResource(R.string.wear_today_no_moments)
    val moments = pluralStringResource(R.plurals.wear_today_moments, summary.momentCount, summary.momentCount)
    val places =
        if (summary.placeCount > 0) {
            pluralStringResource(R.plurals.wear_today_places, summary.placeCount, summary.placeCount)
        } else {
            null
        }
    val voice =
        if (summary.voiceDuration > Duration.ZERO) {
            stringResource(R.string.wear_today_voice, formatClockDuration(summary.voiceDuration))
        } else {
            null
        }
    return listOfNotNull(moments, places, voice).joinToString(stringResource(R.string.wear_today_summary_separator))
}

/** [date] as a short weekday, month and day in the user's locale, such as "Sat, Oct 3". */
@Composable
private fun todayDateText(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale, date) {
        val pattern = DateFormat.getBestDateTimePattern(locale, DATE_SKELETON)
        DateTimeFormatter.ofPattern(pattern, locale).format(date.toJavaLocalDate())
    }
}

private const val DATE_SKELETON = "EEEMMMd"
private val campfireSize = 48.dp
private val TIME_TEXT_CLEARANCE = 28.dp
