package app.logdate.screenshots.components.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.client.permissions.LocationPermissionRequiredScreen
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest

// ─── Location Permission ────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun LocationPermission_Required() {
    ScreenshotTheme {
        LocationPermissionRequiredScreen(onPermissionGranted = {})
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun LocationPermission_Required_Dark() {
    ScreenshotTheme(darkTheme = true) {
        LocationPermissionRequiredScreen(onPermissionGranted = {})
    }
}
