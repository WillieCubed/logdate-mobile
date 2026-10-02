package app.logdate.client.device.di

import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * The module order the app produces: the data module includes [deviceInstanceModule] before the
 * app module includes [deviceModule], so the platform bindings load first.
 */
internal fun appDeviceModules(): List<Module> =
    listOf(
        module { includes(deviceInstanceModule) },
        deviceModule,
    )

class DeviceModuleCompositionTest {
    @Test
    fun `the device modules bind AppInfoProvider once so nothing overrides the platform provider`() {
        val failure =
            runCatching {
                koinApplication {
                    allowOverride(false)
                    modules(appDeviceModules())
                }
            }.exceptionOrNull()

        assertNull(failure, "AppInfoProvider is bound more than once: $failure")
    }
}
