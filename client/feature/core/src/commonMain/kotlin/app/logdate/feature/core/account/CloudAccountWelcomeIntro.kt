package app.logdate.feature.core.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.feature.core.settings.ui.ServerPreset
import app.logdate.feature.core.settings.ui.ServerSelectionCard
import app.logdate.feature.core.settings.ui.ServerSelectionState
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.account_cloud_sync_promotion_description
import logdate.client.feature.core.generated.resources.welcome_to_logdate
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CloudAccountWelcomeIntroPane(
    serverSelectionState: ServerSelectionState,
    onSelectServerPreset: (ServerPreset) -> Unit,
    onCustomServerUrlChange: (String) -> Unit,
    onShowCustomServerInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        CloudAccountWelcomeIntro(
            serverSelectionState = serverSelectionState,
            onSelectServerPreset = onSelectServerPreset,
            onCustomServerUrlChange = onCustomServerUrlChange,
            onShowCustomServerInfo = onShowCustomServerInfo,
        )
        CloudAccountWelcomeBenefits()
    }
}

@Composable
internal fun CloudAccountWelcomeIntro(
    serverSelectionState: ServerSelectionState,
    onSelectServerPreset: (ServerPreset) -> Unit,
    onCustomServerUrlChange: (String) -> Unit,
    onShowCustomServerInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        CloudAccountWelcomeHeader()
        ServerSelectionCard(
            serverSelectionState = serverSelectionState,
            onSelectPreset = onSelectServerPreset,
            onUpdateCustomUrl = onCustomServerUrlChange,
            onShowCustomServerInfo = onShowCustomServerInfo,
        )
    }
}

@Composable
internal fun CloudAccountWelcomeBenefits(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        FeatureItem(
            icon = Icons.Default.Sync,
            title = "Sync across devices",
            description = "Access your journals from any device that uses the same server.",
        )
        FeatureItem(
            icon = Icons.Default.Key,
            title = "Secure with passkeys",
            description = "Use your device biometrics or screen lock instead of a password.",
        )
        FeatureItem(
            icon = Icons.Default.Cloud,
            title = "Server-based backup",
            description = "Your selected server can keep your journals available across devices.",
        )
        FeatureItem(
            icon = Icons.Default.Lock,
            title = "Privacy first",
            description = "Server policies come from the server you choose.",
        )
    }
}

@Composable
private fun CloudAccountWelcomeHeader() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.padding(top = Spacing.xxl),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Box(
                modifier = Modifier.padding(Spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(Res.string.welcome_to_logdate),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.account_cloud_sync_promotion_description),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
