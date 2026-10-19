package app.logdate.feature.core.settings.account.recoverykey

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.feature.core.settings.account.ConnectedServer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether Account offers the recovery key: its feature flag is on and the connected server says it
 * can register and use one. A server without the capability never shows the row, so the app never
 * offers a change the server would refuse.
 */
fun recoveryKeyOffered(
    featureFlags: FeatureFlagStore,
    connectedServer: ConnectedServer,
): Flow<Boolean> =
    combine(featureFlags.observe(FeatureFlag.IDENTITY_RECOVERY_KEY), connectedServer.info) { enabled, server ->
        enabled && server.supportsRecoveryKeys
    }.distinctUntilChanged()
