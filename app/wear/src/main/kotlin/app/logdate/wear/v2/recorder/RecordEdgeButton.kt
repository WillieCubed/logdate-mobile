package app.logdate.wear.v2.recorder

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.LocalContentColor
import app.logdate.ui.tokens.LogDateMotion
import app.logdate.ui.tokens.RecordShape
import app.logdate.wear.R
import app.logdate.wear.v2.recorder.gesture.recordGesture
import app.logdate.wear.v2.recorder.gesture.startLocked
import app.logdate.wear.v2.today.TodayTags

/**
 * Record as Today's edge button. While armed, a touch goes through [recordGesture]; otherwise a
 * click does what the button shows. While a hold slides toward the lock its shape follows the
 * finger from the record circle to a square; once locked, Today takes the button away and Stop
 * comes in the list.
 */
@Composable
internal fun RecordEdgeButton(recorder: TodayRecorder) {
    val model = recorder.state.recordButton()
    val onClick = recordButtonClick(model.action, recorder)
    val morph by animateFloatAsState(targetValue = model.morph, animationSpec = LogDateMotion.quick(), label = "recordMorph")
    EdgeButton(
        onClick = onClick,
        modifier =
            Modifier
                .testTag(TodayTags.RECORD)
                .recordGesture(
                    armed = model.armed,
                    listener = recorder.gesture,
                    description = stringResource(model.descriptionRes),
                    startLabel = stringResource(R.string.wear_today_recorder_start),
                    onClick = onClick,
                    enabled = model.enabled,
                ),
        buttonSize = EdgeButtonSize.Medium,
        enabled = model.enabled,
    ) {
        RecordMorphGlyph(progress = morph)
    }
}

private fun recordButtonClick(
    action: RecordButtonAction,
    recorder: TodayRecorder,
): () -> Unit =
    when (action) {
        RecordButtonAction.START -> recorder.gesture::startLocked
        RecordButtonAction.RETRY_SAVE -> recorder.actions::retry
        RecordButtonAction.NONE -> NoClick
    }

@Composable
private fun RecordMorphGlyph(progress: Float) {
    val color = LocalContentColor.current
    val path = remember { Path() }
    Canvas(modifier = Modifier.size(RECORD_GLYPH_SIZE)) {
        drawPath(path = RecordShape.outline(progress = progress, size = size.minDimension, path = path), color = color)
    }
}

private val NoClick: () -> Unit = {}
private val RECORD_GLYPH_SIZE = 24.dp
