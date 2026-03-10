package app.logdate.screenshots.common

import androidx.compose.ui.tooling.preview.Preview
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
