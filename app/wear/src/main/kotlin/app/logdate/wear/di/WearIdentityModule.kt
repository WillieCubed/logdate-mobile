package app.logdate.wear.di

import android.content.Context
import app.logdate.client.data.account.OwnerAdoptionMover
import app.logdate.client.datastore.LogDateConfigDataSource
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.IdentityMintPolicy
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.device.crypto.NoOpIdentityKeyBackupStore
import app.logdate.client.device.crypto.platformCryptoModule
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.device.identity.userVisibleDeviceName
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.networking.DeviceLinkApiClient
import app.logdate.client.networking.httpClient
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider
import app.logdate.client.sync.peer.link.LinkCodeRedeemer
import app.logdate.client.sync.peer.link.LinkInstaller
import app.logdate.client.sync.peer.link.LinkedDeviceInstaller
import app.logdate.client.sync.peer.link.OwnerRowsMover
import app.logdate.shared.config.PinnedLogDateConfigRepository
import app.logdate.shared.config.PrivacyEpochStorage
import app.logdate.shared.config.PrivacyScopeEpoch
import org.koin.dsl.module

/**
 * Koin module for the watch's identity: the crypto its phone has, an identity key manager that never
 * creates a key of its own, and the installer that takes the phone's account link.
 */
val wearIdentityModule =
    module {
        includes(platformCryptoModule())
        single {
            val secure = get<SecureStorage>()
            PrivacyScopeEpoch(
                object : PrivacyEpochStorage {
                    override suspend fun read(): String? = secure.getString(PRIVACY_EPOCH_KEY)

                    override suspend fun write(epoch: String) = secure.putString(PRIVACY_EPOCH_KEY, epoch)
                },
            )
        }
        single { KeyDerivation(get()) }
        single {
            IdentityKeyManager(
                secureStorage = get(),
                cryptoManager = get(),
                backupStore = NoOpIdentityKeyBackupStore,
                mintPolicy = IdentityMintPolicy.NEVER,
            )
        }
        single { MediaPayloadKeyProvider(get(), get(), get(), get()) }
        single<OwnerRowsMover> { OwnerAdoptionMover(get(), get()) }
        single<LinkCodeRedeemer> {
            LinkCodeRedeemer { origin, descriptor, code, deviceName ->
                DeviceLinkApiClient(httpClient, PinnedLogDateConfigRepository(origin, descriptor)).redeem(code, deviceName)
            }
        }
        single<LinkInstaller> {
            val context = get<Context>()
            val devices = get<DeviceIdProvider>()
            val configuration = get<LogDateConfigDataSource>()
            LinkedDeviceInstaller(
                journal = get(),
                identityKeys = get(),
                mediaKeys = get(),
                owner = get(),
                config = get(),
                sessions = get(),
                redeemer = get(),
                mover = get(),
                deviceId = { devices.getDeviceId().value.toString() },
                deviceName = { context.userVisibleDeviceName() },
                persistConfiguration = { configuration.persistCurrentConfiguration() },
            )
        }
    }

/** The key the phone's device module uses for the same value. */
private const val PRIVACY_EPOCH_KEY = "diagnostic_privacy_epoch"
