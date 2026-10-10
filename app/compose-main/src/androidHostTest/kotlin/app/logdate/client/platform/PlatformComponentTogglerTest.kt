package app.logdate.client.platform

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformComponentTogglerTest {
    private val store = FakeFeatureFlagStore()
    private val switch = RecordingComponentSwitch()

    @Test
    fun `a component follows its flag when the toggler starts`() =
        runTest {
            store.set(FeatureFlag.QUICK_SETTINGS_TILES, true)
            PlatformComponentToggler(store, mapOf(FeatureFlag.QUICK_SETTINGS_TILES to listOf("app.Tile")), switch).start(backgroundScope)
            runCurrent()

            assertEquals(listOf("app.Tile" to true), switch.changes)
        }

    @Test
    fun `turning a flag off disables every component it owns`() =
        runTest {
            store.set(FeatureFlag.QUICK_SETTINGS_TILES, true)
            PlatformComponentToggler(
                store,
                mapOf(FeatureFlag.QUICK_SETTINGS_TILES to listOf("app.NewEntryTile", "app.RecordTile")),
                switch,
            ).start(backgroundScope)
            runCurrent()

            store.set(FeatureFlag.QUICK_SETTINGS_TILES, false)
            runCurrent()

            assertEquals(
                listOf("app.NewEntryTile" to true, "app.RecordTile" to true, "app.NewEntryTile" to false, "app.RecordTile" to false),
                switch.changes,
            )
        }

    @Test
    fun `a component that cannot be switched does not stop the others`() =
        runTest {
            store.set(FeatureFlag.RECORDING_BUBBLE, true)
            switch.failFor = "app.Missing"
            PlatformComponentToggler(store, mapOf(FeatureFlag.RECORDING_BUBBLE to listOf("app.Missing", "app.Bubble")), switch)
                .start(backgroundScope)
            runCurrent()

            assertEquals(listOf("app.Bubble" to true), switch.changes)
        }

    @Test
    fun `an unchanged flag does not switch components again`() =
        runTest {
            PlatformComponentToggler(store, mapOf(FeatureFlag.LIVE_UPDATES to listOf("app.Receiver")), switch).start(backgroundScope)
            runCurrent()
            store.set(FeatureFlag.LIVE_UPDATES, false)
            runCurrent()

            assertEquals(listOf("app.Receiver" to false), switch.changes)
        }

    private class RecordingComponentSwitch : ComponentSwitch {
        val changes = mutableListOf<Pair<String, Boolean>>()
        var failFor: String? = null

        override fun setEnabled(
            className: String,
            enabled: Boolean,
        ) {
            if (className == failFor) throw IllegalArgumentException("Unknown component $className")
            changes += className to enabled
        }
    }

    private class FakeFeatureFlagStore : FeatureFlagStore {
        private val values = MutableStateFlow<Map<FeatureFlag, Boolean>>(emptyMap())

        fun set(
            flag: FeatureFlag,
            enabled: Boolean,
        ) {
            values.value = values.value + (flag to enabled)
        }

        override fun observe(flag: FeatureFlag): Flow<Boolean> = values.map { it[flag] ?: flag.defaultEnabled }

        override suspend fun isEnabled(flag: FeatureFlag): Boolean = values.value[flag] ?: flag.defaultEnabled

        override suspend fun setEnabled(
            flag: FeatureFlag,
            enabled: Boolean,
        ) = set(flag, enabled)
    }
}
