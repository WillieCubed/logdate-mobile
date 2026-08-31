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
import logdate.client.feature.core.generated.resources.account_passkey_create_label
import logdate.client.feature.core.generated.resources.account_passkey_overview_description
import logdate.client.feature.core.generated.resources.account_passkey_sign_in_description
import logdate.client.feature.core.generated.resources.collapse_passkey_options
import logdate.client.feature.core.generated.resources.create_passkey
import logdate.client.feature.core.generated.resources.expand_passkey_options
import logdate.client.feature.core.generated.resources.learn_more
import logdate.client.feature.core.generated.resources.passkey_ends_in
import logdate.client.feature.core.generated.resources.passkey_generic_name
import logdate.client.feature.core.generated.resources.passkey_last_used
import logdate.client.feature.core.generated.resources.passkeys
import logdate.client.feature.core.generated.resources.your_passkeys
import logdate.client.ui.generated.resources.common_delete
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant
import logdate.client.ui.generated.resources.Res as UiRes

private const val PASSKEYS_HELP_URL = "https://logdate.app/help/passkeys"

/**
 * A passkey as the settings screen can currently describe it.
 *
 * The server records a nickname, a device type, and when each passkey was created and last used,
 * but no endpoint exposes them yet -- the account payload carries only credential IDs. Everything
 * beyond [id] is therefore nullable, and the UI leaves out what it does not know. This is a screen
 * someone uses to decide which credential to revoke, so a plausible guess is worse than a blank:
 * it invites revoking the wrong one.
 */
data class PasskeyInfo(
    val id: String,
    val device: String? = null,
    val createdAt: String? = null,
    val lastUsed: Instant? = null,
) {
    /** The tail of the credential ID, which is stable and distinguishes one passkey from another. */
    val shortIdentifier: String get() = id.takeLast(SHORT_IDENTIFIER_LENGTH)

    private companion object {
        const val SHORT_IDENTIFIER_LENGTH = 8
    }
}

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
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = stringResource(Res.string.passkeys),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(Res.string.account_passkey_overview_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        MaterialContainer {
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
                            text = stringResource(Res.string.account_passkey_create_label),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val passkeysText =
                            stringResource(
                                Res.string
                                    .account_passkey_sign_in_description,
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
                        text = stringResource(Res.string.passkey_generic_name),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text =
                            passkey.lastUsed
                                ?.let { stringResource(Res.string.passkey_last_used, formatPasskeyLastUsed(it)) }
                                ?: stringResource(Res.string.passkey_ends_in, passkey.shortIdentifier),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { expanded = !expanded },
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription =
                            if (expanded) {
                                stringResource(
                                    Res.string.collapse_passkey_options,
                                )
                            } else {
                                stringResource(Res.string.expand_passkey_options)
                            },
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
                        text = passkey.device ?: stringResource(Res.string.passkey_ends_in, passkey.shortIdentifier),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    passkey.lastUsed?.let { lastUsed ->
                        Text(
                            text = stringResource(Res.string.passkey_last_used, formatPasskeyLastUsed(lastUsed)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        TextButton(
                            onClick = { onRevokePasskey(passkey) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(UiRes.string.common_delete))
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
                    device = "your Pixel 7",
                    lastUsed = Instant.parse("2024-03-13T00:00:00Z"),
                ),
                PasskeyInfo(
                    id = "2",
                    device = "Windows Device",
                    lastUsed = Instant.parse("2024-03-13T00:00:00Z"),
                ),
            ),
        onCreatePasskey = {},
    )
}
