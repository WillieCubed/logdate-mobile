package app.logdate.feature.core.di

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.device.identity.DefaultDeviceManager
import app.logdate.client.networking.DeviceEnrollmentApiClient
import app.logdate.client.networking.DeviceEnrollmentApiClientContract
import app.logdate.client.sync.peer.devices.AccountLinkedDevicesRepository
import app.logdate.client.sync.peer.devices.LinkedDevicesRepository
import app.logdate.client.sync.peer.devices.pinnedLinkedDeviceClients
import app.logdate.client.sync.peer.removal.WatchRemovalAccess
import app.logdate.feature.core.settings.ui.devices.DefaultDeviceApprovalAccess
import app.logdate.feature.core.settings.ui.devices.DeviceApprovalAccess
import app.logdate.feature.core.settings.ui.devices.DeviceApprovalViewModel
import app.logdate.feature.core.settings.ui.devices.DevicesViewModel
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.ServerProtocolFeature
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
        single<LinkedDevicesRepository> {
            val flags = get<FeatureFlagStore>()
            val config = get<LogDateConfigRepository>()
            AccountLinkedDevicesRepository(
                sessions = get(),
                linkingAvailable = {
                    flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) &&
                        config.getCurrentServerDescriptor()?.hasProtocolFeature(ServerProtocolFeature.DEVICE_LINKING_V1) == true
                },
                clientsFor = { bound -> pinnedLinkedDeviceClients(get(), config, bound) },
                watchRemoval = getOrNull<WatchRemovalAccess>(),
            )
        }
        viewModel { DevicesViewModel(get<DefaultDeviceManager>(), get()) }
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
