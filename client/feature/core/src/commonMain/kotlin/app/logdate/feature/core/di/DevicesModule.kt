package app.logdate.feature.core.di

import app.logdate.client.device.identity.DefaultDeviceManager
import app.logdate.client.networking.DeviceEnrollmentApiClient
import app.logdate.client.networking.DeviceEnrollmentApiClientContract
import app.logdate.feature.core.settings.ui.devices.DefaultDeviceApprovalAccess
import app.logdate.feature.core.settings.ui.devices.DeviceApprovalAccess
import app.logdate.feature.core.settings.ui.devices.DeviceApprovalViewModel
import app.logdate.feature.core.settings.ui.devices.DevicesViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Dependency injection module for the devices management feature.
 *
 * Connecting a device also needs a platform `DeviceTransferSealer`, bound by each platform's core
 * feature module that offers the "Connect a device" action.
 */
val devicesModule: Module =
    module {
        viewModel { DevicesViewModel(get<DefaultDeviceManager>()) }
        single<DeviceEnrollmentApiClientContract> { DeviceEnrollmentApiClient(get(), get()) }
        factory<DeviceApprovalAccess> { DefaultDeviceApprovalAccess(get(), get(), get()) }
        viewModel {
            DeviceApprovalViewModel(
                enrollmentApi = get(),
                sessionStorage = get(),
                access = get(),
                sealer = get(),
            )
        }
    }
