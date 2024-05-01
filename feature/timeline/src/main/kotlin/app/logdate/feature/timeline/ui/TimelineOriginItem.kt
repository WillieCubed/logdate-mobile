package app.logdate.feature.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.feature.timeline.R
import app.logdate.ui.theme.Spacing
import app.logdate.ui.timeline.TimelineLine
import app.logdate.util.daysUntilNow
import app.logdate.util.toReadableDateShort
import kotlinx.datetime.Instant

/**
 * A timeline item that represents a user's birthday.
 */
@Composable
internal fun TimelineOriginItem(
    originDate: Instant,
) {
    val background = Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to MaterialTheme.colorScheme.surface,
            1.0f to MaterialTheme.colorScheme.primaryContainer,
        )
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(brush = background),
        verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.Top),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.sm, horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.Start),
        ) {
            TimelineLine(
                modifier = Modifier.fillMaxHeight(),
                showLine = false,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(originDate.toReadableDateShort(), style = MaterialTheme.typography.titleLarge)
            }
        }
        Column(
            Modifier
                .width(412.dp)
                .height(540.dp)
                .padding(
                    start = Spacing.xl, top = 96.dp, end = Spacing.xl, bottom = 192.dp
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl, Alignment.Top),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .width(144.dp)
                    .height(144.dp)
                    .background(color = MaterialTheme.colorScheme.primary)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.Top),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.timeline_origin_day_birthday_message),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    pluralStringResource(R.plurals.timeline_origin_day_counter, originDate.daysUntilNow),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
    }
}

@Preview
@Composable
private fun TimelineOriginItemPreview() {
    TimelineOriginItem(
        originDate = TEST_ORIGIN_DATE
    )
}

internal val TEST_ORIGIN_DATE = Instant.fromEpochMilliseconds(992649600000)