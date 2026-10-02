package app.logdate.client.device

import app.logdate.client.device.di.appDeviceModules
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DesktopAppInfoProviderTest {
    @Test
    fun `the desktop app reports no version instead of a placeholder`() {
        val koin = koinApplication { modules(appDeviceModules()) }.koin

        val provider = koin.get<AppInfoProvider>()

        assertFailsWith<AppInfoRetrievalException> { provider.getAppInfo() }
    }
}
