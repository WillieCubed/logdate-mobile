package app.logdate.feature.onboarding.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FunButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: ButtonType = ButtonType.PRIMARY,
    icon: @Composable () -> Unit,
) {
    Button(
        onClick = onClick, modifier = modifier.height(96.dp),
        contentPadding = PaddingValues(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = when (type) {
                ButtonType.PRIMARY -> MaterialTheme.colorScheme.primaryContainer
                ButtonType.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer
                ButtonType.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer
            },
            contentColor = when (type) {
                ButtonType.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
                ButtonType.SECONDARY -> MaterialTheme.colorScheme.onSecondaryContainer
                ButtonType.TERTIARY -> MaterialTheme.colorScheme.onTertiaryContainer
            },
        ),
    ) {
        icon()
    }
}

enum class ButtonType {
    PRIMARY, SECONDARY, TERTIARY,
}