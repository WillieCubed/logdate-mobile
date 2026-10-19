package app.logdate.feature.core.settings.account.recoverykey

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.permissions.PasskeyManager
import app.logdate.feature.core.settings.account.ConnectedServer
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * Whether Account offers the recovery key: its feature flag is on, the connected server says it can
 * register and use one, and this device can confirm a change with a passkey, which every change
 * needs. Without all three the row is hidden and the page shows that it is not available, so the
 * app never offers a change that would be refused.
 */
fun recoveryKeyOffered(
    featureFlags: FeatureFlagStore,
    connectedServer: ConnectedServer,
    passkeysSupported: Flow<Boolean>,
): Flow<Boolean> =
    combine(
        featureFlags.observe(FeatureFlag.IDENTITY_RECOVERY_KEY),
        connectedServer.info,
        passkeysSupported,
    ) { enabled, server, passkeys ->
        enabled && server.supportsRecoveryKeys && passkeys
    }.distinctUntilChanged()

/** Whether this device can show a passkey prompt. A failed check is logged and counts as no. */
fun passkeysSupported(passkeyManager: PasskeyManager): Flow<Boolean> =
    flow {
        val supported =
            try {
                passkeyManager.getCapabilities().isSupported
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Napier.w("Could not check whether this device supports passkeys", error)
                false
            }
        emit(supported)
    }
