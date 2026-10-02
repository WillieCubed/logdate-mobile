package app.logdate.client.device

import android.content.Context
import app.logdate.client.device.di.appDeviceModules
import io.mockk.mockk
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertIs

class AndroidAppInfoProviderCompositionTest {
    @Test
    fun `the app resolves the package manager provider rather than a placeholder`() {
        val koin =
            koinApplication {
                androidContext(mockk<Context>())
                modules(appDeviceModules())
            }.koin

        val provider = koin.get<AppInfoProvider>()

        assertIs<AndroidAppInfoProvider>(provider)
    }
}
