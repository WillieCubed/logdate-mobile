package app.logdate.feature.core.di

import app.logdate.client.device.identity.DefaultDeviceManager
import app.logdate.feature.core.settings.ui.devices.DevicesViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Dependency injection module for the devices management feature.
 */
val devicesModule: Module = module {
    viewModel { DevicesViewModel(get<DefaultDeviceManager>()) }
}