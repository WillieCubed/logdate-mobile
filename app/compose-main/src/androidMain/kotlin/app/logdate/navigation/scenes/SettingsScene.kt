package app.logdate.navigation.scenes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import logdate.app.composemain.generated.resources.Res
import logdate.app.composemain.generated.resources.select_a_setting_to_configure
import org.jetbrains.compose.resources.stringResource

/**
 * A placeholder composable shown in the detail pane when no specific setting is selected.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun SettingsEmptyDetailPane() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.select_a_setting_to_configure),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
