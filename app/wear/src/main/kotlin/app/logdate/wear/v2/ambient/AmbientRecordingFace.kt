package app.logdate.wear.v2.ambient

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.AmbientTickEffect
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.logdate.wear.R
import kotlin.time.Duration

object AmbientTags {
    const val FACE = "ambient_recording_face"
}

/** How far ambient content moves, in dp, so the same pixels don't stay lit minute after minute. */
data class BurnInShift(
    val x: Int,
    val y: Int,
) {
    companion object {
        val NONE = BurnInShift(0, 0)
    }
}

/** One of four places a few pixels apart, a new one at each ambient tick. */
internal fun burnInShift(tick: Int): BurnInShift = BURN_IN_SHIFTS[Math.floorMod(tick, BURN_IN_SHIFTS.size)]

private val BURN_IN_SHIFTS = listOf(BurnInShift(0, 0), BurnInShift(4, 0), BurnInShift(4, 4), BurnInShift(0, 4))

/**
 * "Recording" and the whole minutes recorded, white on black. The minutes change at each ambient
 * tick, once a minute; with [burnInProtection] the text also moves to a new place at each tick.
 */
@Composable
fun AmbientRecordingFace(
    elapsed: Duration,
    burnInProtection: Boolean,
    modifier: Modifier = Modifier,
) {
    val currentElapsed by rememberUpdatedState(elapsed)
    var minutes by remember { mutableLongStateOf(elapsed.inWholeMinutes) }
    var tick by remember { mutableIntStateOf(0) }
    LocalAmbientModeManager.current?.AmbientTickEffect {
        minutes = currentElapsed.inWholeMinutes
        tick += 1
    }
    val shift = if (burnInProtection) burnInShift(tick) else BurnInShift.NONE
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black).testTag(AmbientTags.FACE),
        contentAlignment = Alignment.Center,
    ) {
        Column(modifier = Modifier.offset(x = shift.x.dp, y = shift.y.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.wear_today_recorder_recording),
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = pluralStringResource(R.plurals.wear_today_recorder_ambient_minutes, minutes.toInt(), minutes.toInt()),
                color = Color.White,
                style = MaterialTheme.typography.numeralSmall,
            )
        }
    }
}
