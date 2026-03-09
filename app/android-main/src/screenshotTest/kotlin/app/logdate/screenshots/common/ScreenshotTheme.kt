@file:OptIn(ExperimentalResourceApi::class)

package app.logdate.screenshots.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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

    CompositionLocalProvider(LocalResourceReader provides ScreenshotResourceReader) {
        LogDateTheme(darkTheme = resolvedDarkTheme) {
            content()
        }
    }
}
