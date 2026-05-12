@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import logdate.client.feature.rewind.generated.resources.Res
import logdate.client.feature.rewind.generated.resources.rewind_upgrade_chip_action
import logdate.client.feature.rewind.generated.resources.rewind_upgrade_chip_message
import org.jetbrains.compose.resources.stringResource

/**
 * A small chip that surfaces on the Rewind detail screen when the user is on a tier
 * without AI-written narratives. Explains why the Rewind looks different and gives the
 * user a one-tap path into the upgrade flow.
 *
 * Caller decides when to show it (typically gated on a tier check) and what
 * [onUpgradeClick] navigates to (typically the plan-options surface).
 */
@Composable
fun RewindUpgradeChip(
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.15f))
                .clickable(onClick = onUpgradeClick)
                .padding(PaddingValues(horizontal = 16.dp, vertical = 10.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(Res.string.rewind_upgrade_chip_message),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
        Text(
            text = stringResource(Res.string.rewind_upgrade_chip_action),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
    }
}
