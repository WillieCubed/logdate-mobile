package app.logdate.feature.core.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun CloudAccountWelcomeScreen(
    onContinue: () -> Unit,
    onSignIn: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    CloudAccountWelcomeContent(
        onContinue = onContinue,
        onSignIn = onSignIn,
        onSkip = onSkip,
        modifier = modifier
    )
}

@Composable
private fun CloudAccountWelcomeContent(
    onContinue: () -> Unit,
    onSignIn: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.lg)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            Spacer(modifier = Modifier.height(Spacing.xxl))
            
            // LogDate Logo/Branding
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Box(
                    modifier = Modifier.padding(Spacing.lg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = "LogDate Cloud",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            
            // Title and description
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = "Welcome to LogDate Cloud",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "Connect your account to sync your journals, access them from anywhere, and unlock powerful cloud features.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Features list
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                FeatureItem(
                    icon = Icons.Default.Sync,
                    title = "Sync Across Devices",
                    description = "Access your journals from any device, anywhere"
                )
                
                FeatureItem(
                    icon = Icons.Default.Key,
                    title = "Secure with Passkeys",
                    description = "No passwords needed – use your fingerprint or face"
                )
                
                FeatureItem(
                    icon = Icons.Default.Cloud,
                    title = "Cloud Backup",
                    description = "Never lose your memories with automatic backup"
                )
                
                FeatureItem(
                    icon = Icons.Default.Lock,
                    title = "Privacy First",
                    description = "Your data is encrypted and belongs to you"
                )
            }
        }
        
        // Action buttons
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create New Account")
            }
            
            OutlinedButton(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign In to LogDate Cloud")
            }
            
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Skip for now")
            }
        }
    }
}

@Composable
private fun FeatureItem(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview
@Composable
private fun CloudAccountWelcomeScreenPreview() {
    MaterialTheme {
        Surface {
            CloudAccountWelcomeContent(
                onContinue = {},
                onSignIn = {},
                onSkip = {}
            )
        }
    }
}