package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.MaterialContainerScope
import app.logdate.ui.theme.Spacing
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.compose.ui.tooling.preview.Preview

data class PasskeyInfo(
    val id: String,
    val name: String = "Passkey",
    val device: String = "This Device",
    val createdAt: String = "Recently",
    val lastUsed: Instant = Clock.System.now(),
)

@Composable
fun PasskeysInfoSection(
    passkeys: List<PasskeyInfo>,
    onCreatePasskey: () -> Unit,
    onRevokePasskey: (PasskeyInfo) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.padding(Spacing.lg)
        ) {
            Text(
                text = "Passkeys",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Passkeys are a quick and secure way to sign into your LogDate Account.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        MaterialContainer(modifier = modifier) {
            // Dynamic content based on passkeys state
            if (passkeys.isEmpty()) {
                // Empty state
                SurfaceItem(
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        modifier = Modifier.padding(Spacing.lg),
                    ) {
                        Text(
                            text = "Create a passkey",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "With passkeys, you don't need to remember your password. Instead, use your fingerprint, face, or screen lock to sign in.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Learn more",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                // Show passkeys status
                UnsurfacedItem {
                    Text(
                        text = "Your passkeys",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Passkey items
                passkeys.forEach { passkey ->
                    PasskeyItem(
                        passkey = passkey,
                        onRevokePasskey = onRevokePasskey
                    )
                }
            }

            // Create passkey button (always shown)
            SurfaceItem(
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Button(
                    onClick = onCreatePasskey,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Create Passkey",
                        modifier = Modifier.padding(start = Spacing.sm)
                    )
                }
            }
        }
    }
}

@Composable
private fun MaterialContainerScope.PasskeyItem(
    passkey: PasskeyInfo,
    onRevokePasskey: (PasskeyInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    SurfaceItem(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column {
            // Main passkey row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        text = passkey.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Stored on ${passkey.device} • Last used ${formatLastUsed(passkey.lastUsed)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { expanded = !expanded }
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse passkey options" else "Expand passkey options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanded content
            if (expanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Spacing.md),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = passkey.device,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Last used ${formatLastUsed(passkey.lastUsed)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        TextButton(
                            onClick = { onRevokePasskey(passkey) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun formatLastUsed(lastUsed: Instant): String {
    val now = Clock.System.now()
    val diffInSeconds = now.epochSeconds - lastUsed.epochSeconds

    return when {
        diffInSeconds < 60 -> "just now"
        diffInSeconds < 3600 -> "${diffInSeconds / 60} minutes ago"
        diffInSeconds < 86400 -> "${diffInSeconds / 3600} hours ago"
        diffInSeconds < 604800 -> "${diffInSeconds / 86400} days ago"
        diffInSeconds < 2592000 -> "${diffInSeconds / 604800} weeks ago"
        diffInSeconds < 31536000 -> "${diffInSeconds / 2592000} months ago"
        else -> "${diffInSeconds / 31536000} years ago"
    }
}

@Preview
@Composable
private fun PasskeysInfoSectionEmptyPreview() {
    PasskeysInfoSection(
        passkeys = emptyList(),
        onCreatePasskey = {}
    )
}

@Preview
@Composable
private fun PasskeysInfoSectionSinglePreview() {
    PasskeysInfoSection(
        passkeys = listOf(
            PasskeyInfo(
                id = "1",
                name = "Passkey #1",
                device = "your Pixel 7",
                lastUsed = Instant.parse("2024-03-13T00:00:00Z")
            )
        ),
        onCreatePasskey = {}
    )
}

@Preview
@Composable
private fun PasskeysInfoSectionMultiplePreview() {
    PasskeysInfoSection(
        passkeys = listOf(
            PasskeyInfo(
                id = "1",
                name = "Passkey #1",
                device = "your Pixel 7",
                lastUsed = Instant.parse("2024-03-13T00:00:00Z")
            ),
            PasskeyInfo(
                id = "2",
                name = "Passkey #2",
                device = "Windows Device",
                lastUsed = Instant.parse("2024-03-13T00:00:00Z")
            )
        ),
        onCreatePasskey = {}
    )
}