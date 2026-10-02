package app.logdate.feature.core.settings.updates

import app.logdate.client.device.AppInfo
import app.logdate.client.device.AppInfoProvider
import app.logdate.client.device.AppInfoRetrievalException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UnsupportedAppUpdateControllerTest {
    @Test
    fun `the state shows the installed version and says updates are unsupported`() {
        val provider = FixedAppInfoProvider(AppInfo(versionName = "1.4.0", versionCode = 1402, packageName = "studio.hypertext.logdate"))

        val state = UnsupportedAppUpdateController(provider).uiState.value

        assertEquals("1.4.0", state.currentVersionName)
        assertEquals(1402, state.currentVersionCode)
        assertEquals(AppUpdateStatus.Unsupported, state.status)
    }

    @Test
    fun `the controller still works when the platform cannot report its version`() {
        val state = UnsupportedAppUpdateController(FailingAppInfoProvider()).uiState.value

        assertEquals("unknown", state.currentVersionName)
        assertNull(state.currentVersionCode)
        assertEquals(AppUpdateStatus.Unsupported, state.status)
    }
}

private class FixedAppInfoProvider(
    private val appInfo: AppInfo,
) : AppInfoProvider {
    override fun getAppInfo(): AppInfo = appInfo
}

private class FailingAppInfoProvider : AppInfoProvider {
    override fun getAppInfo(): AppInfo = throw AppInfoRetrievalException("Package info unavailable")
}
