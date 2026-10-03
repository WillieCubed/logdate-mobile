package app.logdate.feature.core.settings.ui.labs

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

internal class FakeFeatureFlagStore : FeatureFlagStore {
    private val values = MutableStateFlow<Map<FeatureFlag, Boolean>>(emptyMap())
    val writes = mutableListOf<Pair<FeatureFlag, Boolean>>()

    override fun observe(flag: FeatureFlag): Flow<Boolean> = values.map { stored -> stored[flag] ?: flag.defaultEnabled }

    override suspend fun setEnabled(
        flag: FeatureFlag,
        enabled: Boolean,
    ) {
        writes += flag to enabled
        values.update { stored -> stored + (flag to enabled) }
    }
}
