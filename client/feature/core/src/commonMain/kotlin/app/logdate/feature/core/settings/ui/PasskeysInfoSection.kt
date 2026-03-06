@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:max-line-length")

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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.MaterialContainerScope
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.create_a_passkey_2
import logdate.client.feature.core.generated.resources.create_passkey
import logdate.client.feature.core.generated.resources.delete
import logdate.client.feature.core.generated.resources.learn_more
import logdate.client.feature.core.generated.resources.passkeys
import logdate.client.feature.core.generated.resources.passkeys_are_a_quick_and_secure_way_to_sign_into_your_logdate_account
import logdate.client.feature.core.generated.resources.with_passkeys_you_dont_need_to_remember_your_password_instead_use_your_fingerprint_face_or_screen_lock_to_sign_in
import logdate.client.feature.core.generated.resources.your_passkeys
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

private const val PASSKEYS_HELP_URL = "https://logdate.app/help/passkeys"

/**
 * UI-facing metadata for a passkey credential.
 *
 * @property id Credential identifier used for revocation.
 * @property name Display name shown in the list.
 * @property device Human-readable device label.
 * @property createdAt Display string for creation time.
 * @property lastUsed Timestamp of last usage.
 */
data class PasskeyInfo(
    val id: String,
    val name: String = "Passkey",
    val device: String = "This Device",
    val createdAt: String = "Recently",
    val lastUsed: Instant = Clock.System.now(),
)

/**
 * Displays passkey details and an optional create action.
 *
 * @param showCreatePasskeyAction Whether to show the create passkey button.
 */
@Composable
fun PasskeysInfoSection(
    passkeys: List<PasskeyInfo>,
    onCreatePasskey: () -> Unit,
    onRevokePasskey: (PasskeyInfo) -> Unit = {},
    showCreatePasskeyAction: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.padding(Spacing.lg),
        ) {
            Text(
                text = stringResource(Res.string.passkeys),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(Res.string.passkeys_are_a_quick_and_secure_way_to_sign_into_your_logdate_account),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        MaterialContainer(modifier = modifier) {
            // Dynamic content based on passkeys state
            if (passkeys.isEmpty()) {
                // Empty state
                SurfaceItem(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        modifier = Modifier.padding(Spacing.lg),
                    ) {
                        Text(
                            text = stringResource(Res.string.create_a_passkey_2),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val passkeysText =
                            stringResource(
                                Res.string
                                    .with_passkeys_you_dont_need_to_remember_your_password_instead_use_your_fingerprint_face_or_screen_lock_to_sign_in,
                            )
                        Text(
                            text = passkeysText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val uriHandler = LocalUriHandler.current
                        TextButton(
                            onClick = { uriHandler.openUri(PASSKEYS_HELP_URL) },
                        ) {
                            Text(
                                text = stringResource(Res.string.learn_more),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            } else {
                // Show passkeys status
                UnsurfacedItem {
                    Text(
                        text = stringResource(Res.string.your_passkeys),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Passkey items
                passkeys.forEach { passkey ->
                    PasskeyItem(
                        passkey = passkey,
                        onRevokePasskey = onRevokePasskey,
                    )
                }
            }

            // Create passkey button (always shown)
            if (showCreatePasskeyAction) {
                SurfaceItem(
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Button(
                        onClick = onCreatePasskey,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(Res.string.create_passkey),
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
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
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column {
            // Main passkey row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = passkey.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Stored on ${passkey.device} • Last used ${formatPasskeyLastUsed(passkey.lastUsed)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { expanded = !expanded },
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse passkey options" else "Expand passkey options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Expanded content
            if (expanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Spacing.md),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = passkey.device,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Last used ${formatPasskeyLastUsed(passkey.lastUsed)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        TextButton(
                            onClick = { onRevokePasskey(passkey) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(Res.string.delete))
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun PasskeysInfoSectionEmptyPreview() {
    PasskeysInfoSection(
        passkeys = emptyList(),
        onCreatePasskey = {},
    )
}

@Preview
@Composable
private fun PasskeysInfoSectionSinglePreview() {
    PasskeysInfoSection(
        passkeys =
            listOf(
                PasskeyInfo(
                    id = "1",
                    name = "Passkey #1",
                    device = "your Pixel 7",
                    lastUsed = Instant.parse("2024-03-13T00:00:00Z"),
                ),
            ),
        onCreatePasskey = {},
    )
}

@Preview
@Composable
private fun PasskeysInfoSectionMultiplePreview() {
    PasskeysInfoSection(
        passkeys =
            listOf(
                PasskeyInfo(
                    id = "1",
                    name = "Passkey #1",
                    device = "your Pixel 7",
                    lastUsed = Instant.parse("2024-03-13T00:00:00Z"),
                ),
                PasskeyInfo(
                    id = "2",
                    name = "Passkey #2",
                    device = "Windows Device",
                    lastUsed = Instant.parse("2024-03-13T00:00:00Z"),
                ),
            ),
        onCreatePasskey = {},
    )
}
