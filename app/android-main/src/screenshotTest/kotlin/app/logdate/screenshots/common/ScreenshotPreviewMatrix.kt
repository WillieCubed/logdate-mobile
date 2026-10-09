package app.logdate.screenshots.common

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
import app.logdate.screenshots.common.ScreenshotTestData.BUBBLE
import app.logdate.screenshots.common.ScreenshotTestData.COMPACT_PHONE
import app.logdate.screenshots.common.ScreenshotTestData.DESKTOP_WINDOW
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTestData.PHONE_LANDSCAPE
import app.logdate.screenshots.common.ScreenshotTestData.SPLIT_MEDIUM
import app.logdate.screenshots.common.ScreenshotTestData.TABLET
import app.logdate.screenshots.common.ScreenshotTestData.TABLET_PORTRAIT

/**
 * Standard screenshot preview matrix for route and screen coverage.
 *
 * Matches the requested baseline set:
 * - phone light
 * - phone dark
 * - landscape
 * - tablet
 */
@Preview(name = "Phone", showBackground = true, device = PHONE)
@Preview(
    name = "Phone Dark",
    showBackground = true,
    device = PHONE,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "Landscape", showBackground = true, device = PHONE_LANDSCAPE)
@Preview(name = "Tablet", showBackground = true, device = TABLET)
annotation class ScreenshotPreviewMatrix

/**
 * Focused large-screen preview matrix for adaptive regression coverage.
 */
@Preview(name = "Split Medium", showBackground = true, device = SPLIT_MEDIUM)
@Preview(name = "Tablet Landscape", showBackground = true, device = TABLET)
@Preview(name = "Tablet Portrait", showBackground = true, device = TABLET_PORTRAIT)
@Preview(name = "Desktop Window", showBackground = true, device = DESKTOP_WINDOW)
annotation class LargeScreenAuditPreviewMatrix

/**
 * Preview matrix for Android OS integration surfaces: shortcuts, tiles, notifications, widgets,
 * bubbles, and the screens they open.
 *
 * Adds the configurations people meet those surfaces in on top of the standard matrix: a compact
 * phone, right-to-left text, 200% text, and a dynamic color scheme taken from a wallpaper. Every
 * OS integration feature records its Lane 1 references with this matrix; see
 * `docs/testing/screenshot-tests.md`.
 */
@Preview(name = "Phone", showBackground = true, device = PHONE)
@Preview(
    name = "Phone Dark",
    showBackground = true,
    device = PHONE,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "Compact Phone", showBackground = true, device = COMPACT_PHONE)
@Preview(name = "Landscape", showBackground = true, device = PHONE_LANDSCAPE)
@Preview(name = "Tablet", showBackground = true, device = TABLET)
@Preview(name = "RTL", showBackground = true, device = PHONE, locale = "ar")
@Preview(name = "Large Text", showBackground = true, device = PHONE, fontScale = 2f)
@Preview(
    name = "Dynamic Color",
    showBackground = true,
    device = PHONE,
    wallpaper = Wallpapers.GREEN_DOMINATED_EXAMPLE,
)
annotation class OsIntegrationPreviewMatrix

/**
 * A floating bubble-sized window, for OS integration screens that can open inside an app bubble.
 * Apply it alongside [OsIntegrationPreviewMatrix].
 */
@Preview(name = "Bubble", showBackground = true, device = BUBBLE)
annotation class BubbleWindowPreview
