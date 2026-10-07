package app.logdate.feature.editor.ui.editor

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class EditorBackgroundSaveEffectTest {
    @Test
    fun `backgrounding saves the latest edits without waiting for the debounce`() =
        runSkikoComposeUiTest {
            val owner = TestLifecycleOwner()
            val saved = mutableListOf<String>()
            val content = mutableStateOf("Initial")
            setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    val current = content.value
                    EditorBackgroundSaveEffect { saved += current }
                }
            }
            runOnIdle {
                content.value = "Just edited"
                owner.registry.currentState = Lifecycle.State.STARTED
            }
            waitForIdle()
            assertEquals(emptyList(), saved, "Pausing alone does not stop the editor")
            runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
            assertEquals(listOf("Just edited"), saved)
        }

    @Test
    fun `returning and backgrounding again uses the updated save callback`() =
        runSkikoComposeUiTest {
            val owner = TestLifecycleOwner()
            val saved = mutableListOf<String>()
            val content = mutableStateOf("First")
            setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    val current = content.value
                    EditorBackgroundSaveEffect { saved += current }
                }
            }
            runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
            runOnIdle {
                owner.registry.currentState = Lifecycle.State.RESUMED
                content.value = "Second"
            }
            waitForIdle()
            runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
            assertEquals(listOf("First", "Second"), saved)
        }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle = registry
    }
}
