package app.logdate.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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