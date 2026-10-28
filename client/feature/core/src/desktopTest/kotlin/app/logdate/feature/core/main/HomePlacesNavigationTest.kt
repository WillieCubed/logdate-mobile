package app.logdate.feature.core.main

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.ui.theme.LogDateTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class HomePlacesNavigationTest {
    @Test
    fun `saved Places selection waits for preference load and disabling returns to Timeline`() =
        runDesktopComposeUiTest {
            val flags = DelayedFlags()
            var home by mutableStateOf(true)
            setContent {
                LogDateTheme(dynamicColor = false) {
                    val holder = rememberSaveableStateHolder()
                    if (home) {
                        holder.SaveableStateProvider("home") {
                            val navigation = rememberHomeNavigation(true, flags)
                            Column {
                                Text("Selected: ${navigation.selected}")
                                navigation.destinations.forEach { destination ->
                                    Button(onClick = { navigation.select(destination) }) { Text(destination.name) }
                                }
                            }
                        }
                    }
                }
            }
            onNodeWithText("LocationHistory").assertDoesNotExist()
            runOnIdle { flags.enabled.value = true }
            onNodeWithText("LocationHistory").performClick()
            onNodeWithText("Selected: LocationHistory").assertExists()
            runOnIdle { home = false }
            waitForIdle()
            runOnIdle {
                flags.enabled.value = null
                home = true
            }
            onNodeWithText("LocationHistory").assertDoesNotExist()
            runOnIdle { flags.enabled.value = true }
            onNodeWithText("Selected: LocationHistory").assertExists()
            runOnIdle { flags.enabled.value = false }
            onNodeWithText("Selected: Timeline").assertExists()
            onNodeWithText("LocationHistory").assertDoesNotExist()
            onNodeWithText("Rewind").assertExists()
        }

    private class DelayedFlags : FeatureFlagStore {
        val enabled = MutableStateFlow<Boolean?>(null)

        override fun observe(flag: FeatureFlag): Flow<Boolean> = enabled.filterNotNull()

        override suspend fun setEnabled(
            flag: FeatureFlag,
            enabled: Boolean,
        ) {
            this.enabled.value = enabled
        }
    }
}
