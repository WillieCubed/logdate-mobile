package app.logdate.client.intelligence.fakes

import app.logdate.client.networking.DataUsageMode
import app.logdate.client.networking.DataUsagePolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeDataUsagePolicy(
    initialMode: DataUsageMode = DataUsageMode.Unrestricted,
) : DataUsagePolicy {
    private val modeFlow = MutableStateFlow(initialMode)

    override val policy: Flow<DataUsageMode> = modeFlow

    override suspend fun currentMode(): DataUsageMode = modeFlow.value

    fun setMode(mode: DataUsageMode) {
        modeFlow.value = mode
    }
}
