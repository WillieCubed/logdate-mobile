package app.logdate.wear.launch

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * The latest value of [flag] for code that can't suspend, such as a notification builder. It reads
 * false until [start] has read the flag, and stays false when the flag can't be read.
 */
class FeatureSwitch(
    private val featureFlags: FeatureFlagStore,
    private val flag: FeatureFlag,
) {
    private val current = MutableStateFlow(false)

    val state: StateFlow<Boolean> = current.asStateFlow()

    val isOn: Boolean get() = current.value

    /** Follows the flag in [scope] for the life of the app. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            featureFlags
                .observe(flag)
                .catch { error ->
                    Napier.e("Couldn't read ${flag.name}; treating it as off", error)
                    emit(false)
                }.collect { enabled -> current.value = enabled }
        }
    }
}
