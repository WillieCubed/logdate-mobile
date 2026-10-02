package app.logdate.client.device

import app.logdate.client.device.di.appDeviceModules
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertIs

class IosAppInfoProviderCompositionTest {
    @Test
    fun `the app resolves the bundle provider rather than a placeholder`() {
        val koin = koinApplication { modules(appDeviceModules()) }.koin

        val provider = koin.get<AppInfoProvider>()

        assertIs<IosAppInfoProvider>(provider)
    }
}
