@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.cloud_account_benefit_backup_description
import logdate.client.feature.core.generated.resources.cloud_account_benefit_backup_title
import logdate.client.feature.core.generated.resources.cloud_account_benefit_passkeys_description
import logdate.client.feature.core.generated.resources.cloud_account_benefit_passkeys_title
import logdate.client.feature.core.generated.resources.cloud_account_benefit_privacy_description
import logdate.client.feature.core.generated.resources.cloud_account_benefit_privacy_title
import logdate.client.feature.core.generated.resources.cloud_account_benefit_sync_description
import logdate.client.feature.core.generated.resources.cloud_account_benefit_sync_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CloudAccountWelcomeBenefits(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        FeatureItem(
            icon = Icons.Default.Sync,
            title = stringResource(Res.string.cloud_account_benefit_sync_title),
            description = stringResource(Res.string.cloud_account_benefit_sync_description),
        )
        FeatureItem(
            icon = Icons.Default.Key,
            title = stringResource(Res.string.cloud_account_benefit_passkeys_title),
            description = stringResource(Res.string.cloud_account_benefit_passkeys_description),
        )
        FeatureItem(
            icon = Icons.Default.Cloud,
            title = stringResource(Res.string.cloud_account_benefit_backup_title),
            description = stringResource(Res.string.cloud_account_benefit_backup_description),
        )
        FeatureItem(
            icon = Icons.Default.Lock,
            title = stringResource(Res.string.cloud_account_benefit_privacy_title),
            description = stringResource(Res.string.cloud_account_benefit_privacy_description),
        )
    }
}

@Composable
private fun FeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
