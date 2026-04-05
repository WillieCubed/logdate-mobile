package app.logdate.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val FOCUS_RING_WIDTH = 2.dp
private val FOCUS_RING_CORNER = 4.dp

/**
 * Shows a visible focus ring when this element is focused via keyboard.
 *
 * The ring uses the primary color at reduced opacity so it's visible
 * without being overwhelming. Only appears on keyboard focus — touch
 * focus does not show the ring (handled by ripple instead).
 */
fun Modifier.focusRing(color: Color = Color.Unspecified): Modifier =
    composed {
        val ringColor =
            if (color == Color.Unspecified) {
                MaterialTheme.colorScheme.primary
            } else {
                color
            }

        var isFocused by remember { mutableStateOf(false) }

        this
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (isFocused) {
                    Modifier.border(FOCUS_RING_WIDTH, ringColor, RoundedCornerShape(FOCUS_RING_CORNER))
                } else {
                    Modifier
                },
            )
    }

/**
 * Makes this element focusable via keyboard (Tab key) and shows a focus ring
 * when focused. Combines [focusable] and [focusRing] for convenience.
 */
fun Modifier.focusableWithRing(color: Color = Color.Unspecified): Modifier =
    this
        .focusRing(color)
        .focusable()
