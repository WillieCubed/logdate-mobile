package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.voice_notes_settings
import logdate.client.feature.core.generated.resources.voice_notes_settings_intro
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class VoiceNotesSettingsLayoutTest {
    @Test
    fun `unavailable Quiet Save preserves the first settings item position`() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            val showVoiceNotes = mutableStateOf(false)
            setContent {
                LogDateTheme {
                    if (showVoiceNotes.value) {
                        VoiceNotesSettingsContent(VoiceNotesSettingsViewModel.UiState(), {}, {}, {})
                    } else {
                        SettingsScaffold(title = stringResource(Res.string.voice_notes_settings), onBack = {}) {
                            item {
                                Text(
                                    text = stringResource(Res.string.voice_notes_settings_intro),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                                )
                            }
                        }
                    }
                }
            }
            val intro = onNodeWithText("Voice notes work without anything extra.", substring = true)
            val expectedBounds = intro.fetchSemanticsNode().boundsInRoot
            runOnIdle { showVoiceNotes.value = true }
            assertEquals(expectedBounds, intro.fetchSemanticsNode().boundsInRoot)
        }
}
