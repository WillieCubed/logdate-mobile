package app.logdate.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Modifier that applies padding to the IME (soft keyboard) height when it is visible.
 *
 * This modifier is only supported on Android.
 */
@Composable
expect fun Modifier.applyImeScroll(): Modifier

/**
 * Applies a conditional modifier based on the given [condition].
 *
 * If the [condition] is `true`, the [modifyIfTrue] will be applied to the current [Modifier].
 * If the [condition] is `false`, the [modifyIfFalse] will be applied to the current [Modifier].
 */
@Composable
fun Modifier.conditional(
    condition: Boolean,
    modifyIfTrue: @Composable Modifier.() -> Modifier,
    modifyIfFalse: @Composable Modifier.() -> Modifier
): Modifier {
    return if (condition) {
        then(modifyIfTrue(Modifier))
    } else {
        then(modifyIfFalse(Modifier))
    }
}

/**
 * Apply the given modifier if the [condition] is `true`, otherwise do nothing.
 */
@Composable
fun Modifier.conditional(
    condition: Boolean,
    modifyIfTrue: @Composable Modifier.() -> Modifier,
): Modifier {
    return if (condition) {
        then(modifyIfTrue(Modifier))
    } else {
        this
    }
}

/**
 * Modifier that applies padding to the bottom of the layout if the current item is the last in the list.
 *
 * @param currentIndex The index of the current item.
 * @param totalItems The total number of items in the list.
 * @param padding The padding to apply if the current item is the last in the list. Defaults to 80.dp bottom padding.
 */
@Composable
fun Modifier.applyPaddingIfLast(
    currentIndex: Int,
    totalItems: Int,
    padding: PaddingValues = PaddingValues(bottom = 80.dp),
): Modifier = conditional(currentIndex == totalItems - 1) {
    padding(padding)
}

/**
 * Applies standard screen styling to the composable, including an extra large shape clip.
 * 
 * This should be applied to all top-level screen composables to ensure consistent appearance
 * across the app.
 * 
 * @return A modifier with the screen styling applied
 */
@Composable
fun Modifier.applyScreenStyles(): Modifier {
    return this
        .clip(MaterialTheme.shapes.extraLarge)
}

/**
 * Applies standard content width constraints to the composable.
 * 
 * This ensures content doesn't get too wide on large screens while maintaining
 * good readability and layout consistency.
 * 
 * @return A modifier with the standard content width applied
 */
@Composable
fun Modifier.applyStandardContentWidth(): Modifier {
    return this
        .padding(horizontal = 16.dp)
}

