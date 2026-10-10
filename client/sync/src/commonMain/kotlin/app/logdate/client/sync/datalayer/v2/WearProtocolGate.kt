package app.logdate.client.sync.datalayer.v2

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Decides when this device speaks version 2.
 *
 * Version 2 is active only while this device's Labs switch is on and the other device advertises its
 * version 2 capability. A surface also needs the other device to list the feature it uses; until then
 * it does not appear, and both devices continue to use version 1.
 */
class WearProtocolGate(
    private val flagEnabled: Flow<Boolean>,
    private val peerHasV2Capability: Flow<Boolean>,
    private val peerFeatures: Flow<Set<String>>,
) {
    fun isV2Active(): Flow<Boolean> =
        combine(flagEnabled, peerHasV2Capability) { enabled, capable -> enabled && capable }.distinctUntilChanged()

    fun isFeatureActive(feature: String): Flow<Boolean> =
        combine(isV2Active(), peerFeatures) { active, features -> active && feature in features }.distinctUntilChanged()
}
