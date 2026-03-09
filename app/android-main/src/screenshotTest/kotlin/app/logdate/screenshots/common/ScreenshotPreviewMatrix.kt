package app.logdate.screenshots.common

import androidx.compose.ui.tooling.preview.Preview
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTestData.PHONE_LANDSCAPE
import app.logdate.screenshots.common.ScreenshotTestData.TABLET

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
