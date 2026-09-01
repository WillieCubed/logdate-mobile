@file:OptIn(ExperimentalResourceApi::class)

package app.logdate.screenshots.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.datetime.LocalDate
import app.logdate.ui.common.formatting.LocalToday
import app.logdate.ui.theme.LogDateTheme
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.LocalResourceReader

/**
 * Theme wrapper for screenshot tests that provides a custom [ResourceReader]
 * capable of loading Compose Multiplatform resources (.cvr files) from the
 * project's merged debug assets directory.
 *
 * Use this instead of [LogDateTheme] in all screenshot test composables to
 * ensure compose resources (strings, drawables) render correctly.
 */
@Composable
fun ScreenshotTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val resolvedDarkTheme = darkTheme ?: isSystemInDarkTheme()

    CompositionLocalProvider(
        LocalResourceReader provides ScreenshotResourceReader,
        // Pin "today" so relative dates are reproducible. Without this a scene showing a recent
        // date renders "Yesterday" when recorded and "May 4" the next day, and every reference
        // containing one rots on a timer with no code having changed.
        LocalToday provides SCREENSHOT_TODAY,
    ) {
        LogDateTheme(darkTheme = resolvedDarkTheme) {
            content()
        }
    }
}

/**
 * The date every screenshot renders as if it were.
 *
 * Chosen to sit just after the fixtures' own dates so "Yesterday" and weekday names still appear
 * -- pinning it far in the future would make every scene fall back to an absolute date and stop
 * exercising the relative formatting at all.
 */
private val SCREENSHOT_TODAY = LocalDate(2026, 5, 5)
