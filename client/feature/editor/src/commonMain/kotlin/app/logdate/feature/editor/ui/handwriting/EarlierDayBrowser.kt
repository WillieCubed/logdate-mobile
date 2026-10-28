@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.handwriting

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.logdate.client.repository.journals.JournalNote
import app.logdate.feature.editor.ui.layout.LocalEntryContentInsets
import app.logdate.feature.editor.ui.layout.clipEntryContentToSafeWindow
import app.logdate.ui.foldable.FoldableSplitLayout
import app.logdate.ui.foldable.calculateFoldableSplitLayout
import app.logdate.ui.foldable.relativeTo
import app.logdate.ui.foldable.rememberFoldableLayoutInfo
import app.logdate.ui.foldable.rememberWindowOrigin
import app.logdate.util.localTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.entry_page_earlier_today
import logdate.client.feature.editor.generated.resources.entry_page_later_entries
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

@Composable
internal fun EarlierDayHeading(modifier: Modifier = Modifier) {
    val date =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
    Row(
        modifier.heightIn(min = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(date.day.toString(), style = MaterialTheme.typography.displaySmall)
                Text(date.dayOfWeek.name.take(3), style = MaterialTheme.typography.labelSmall)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(Res.string.entry_page_earlier_today),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.year}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A reading destination over the same day; the draft remains owned by the editor behind it. */
@Composable
internal fun EarlierDayBrowser(
    notes: List<JournalNote>,
    scroll: ScrollState,
) {
    val (origin, trackOrigin) = rememberWindowOrigin()
    BoxWithConstraints(Modifier.fillMaxSize().then(trackOrigin).background(MaterialTheme.colorScheme.surfaceDim)) {
        val split =
            calculateFoldableSplitLayout(
                maxWidth,
                maxHeight,
                rememberFoldableLayoutInfo().relativeTo(origin),
            ) as? FoldableSplitLayout.Vertical
        Box(Modifier.fillMaxSize().verticalScroll(scroll).testTag("entry_day_browser"), contentAlignment = Alignment.TopCenter) {
            if (split != null && notes.size > 1) {
                val midpoint = (notes.size + 1) / 2
                Row(Modifier.fillMaxWidth()) {
                    BrowserEntries(
                        notes.take(midpoint),
                        Modifier.width(split.leftPane.width),
                    ) { EarlierDayHeading(Modifier.padding(horizontal = 16.dp)) }
                    Box(Modifier.width(split.hingeBounds.width))
                    BrowserEntries(notes.drop(midpoint), Modifier.width(split.rightPane.width)) {
                        Column(Modifier.heightIn(min = 96.dp).padding(horizontal = 16.dp), verticalArrangement = Arrangement.Center) {
                            Text(
                                stringResource(Res.string.entry_page_later_entries),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            } else {
                BrowserEntries(
                    notes,
                    Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                ) { EarlierDayHeading(Modifier.padding(horizontal = 16.dp)) }
            }
        }
    }
}

@Composable
private fun BrowserEntries(
    notes: List<JournalNote>,
    modifier: Modifier,
    heading: @Composable () -> Unit,
) {
    Column(
        modifier.padding(
            top = 104.dp + LocalEntryContentInsets.current.asPaddingValues().calculateTopPadding(),
            bottom =
                16.dp + LocalEntryContentInsets.current.asPaddingValues().calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        heading()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            notes.forEachIndexed { index, note ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape =
                        RoundedCornerShape(
                            topStart = if (index == 0) 16.dp else 4.dp,
                            topEnd = if (index == 0) 16.dp else 4.dp,
                            bottomStart =
                                if (index ==
                                    notes.lastIndex
                                ) {
                                    16.dp
                                } else {
                                    4.dp
                                },
                            bottomEnd = if (index == notes.lastIndex) 16.dp else 4.dp,
                        ),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp).clipEntryContentToSafeWindow(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            note.creationTimestamp.localTime,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        EarlierEntryContent(note)
                    }
                }
            }
        }
    }
}
