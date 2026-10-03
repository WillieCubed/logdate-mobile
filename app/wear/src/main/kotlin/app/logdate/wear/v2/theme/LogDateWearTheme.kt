package app.logdate.wear.v2.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

/**
 * The new watch app's theme: the system's dynamic colors where the watch has them (Wear OS 6 and
 * newer), [GraphiteColorScheme] everywhere else. Previews and screenshots pass
 * [dynamicColor] = false, since they cannot render dynamic color.
 */
@Composable
fun LogDateWearTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = remember(context, dynamicColor) { colorSchemeFor(context, dynamicColor) }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

internal fun colorSchemeFor(
    context: Context,
    dynamicColor: Boolean,
): ColorScheme {
    if (!dynamicColor) return GraphiteColorScheme
    return dynamicColorScheme(context) ?: GraphiteColorScheme
}
