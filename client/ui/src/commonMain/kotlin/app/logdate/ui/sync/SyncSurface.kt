@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.sync

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing

/**
 * Sync chip lives in the trailing slot of any TopAppBar. Quiet by default — composes nothing
 * for [SyncPresentation.Hidden]. Tones scale with severity:
 *
 * - `Syncing` → primary container with a continuously rotating sync glyph
 * - `Pending` → secondary container with a count
 * - `NetworkError` → tertiary container with a count and a "cloud off" glyph
 *
 * The chip never speaks for `AuthError` / `StorageError` / `ConflictError` — those promote to
 * a banner instead. This split is deliberate: chip = ambient progress, banner = needs the
 * user to act.
 */
@Composable
fun SyncIndicatorChip(
    presentation: SyncPresentation,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val visual = presentation.toChipVisual() ?: return
    AnimatedVisibility(
        visible = true,
        enter =
            fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                expandHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut() + shrinkHorizontally(),
        modifier = modifier,
    ) {
        Surface(
            onClick = onClick,
            color = visual.containerColor,
            contentColor = visual.contentColor,
            // Full pill — the conventional MD3 chip shape. An asymmetric squircle was tried
            // first but read as off-kilter without earning the visual interest.
            shape = CircleShape,
            modifier = Modifier.widthIn(min = 32.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs + 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (visual.showSpinner) {
                    RotatingSyncIcon(tint = visual.contentColor)
                } else if (visual.icon != null) {
                    Icon(
                        imageVector = visual.icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = visual.label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Banner for severities that require a user choice — auth lapsed, quota hit, conflicts to
 * resolve. Sits *under* the TopAppBar inside the Scaffold body, so it inherits content insets
 * and never collides with the system status bar (the original bug).
 *
 * On medium/expanded windows, callers should constrain width to ~560dp and center; on
 * compact, full content width is correct. Both modes are honored via the parent's [modifier].
 */
@Composable
fun SyncErrorBanner(
    presentation: SyncPresentation,
    modifier: Modifier = Modifier,
    onAction: (SyncAction) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val visual = presentation.toBannerVisual()
    AnimatedVisibility(
        visible = visual != null,
        // Slight bounce on enter — MD3 Expressive's voice. Snappy on exit so it doesn't linger.
        enter =
            slideInVertically(
                initialOffsetY = { -it },
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
            ) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(),
        modifier = modifier,
    ) {
        if (visual == null) return@AnimatedVisibility
        Surface(
            color = visual.containerColor,
            contentColor = visual.contentColor,
            // MD3 extra-large container radius (28dp). Uniform rounding reads cleaner here
            // than the asymmetric variant — the banner already has plenty of presence from
            // its tonal color and width; bending the corners adds noise without meaning.
            shape = MaterialTheme.shapes.extraLarge,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Icon(
                    imageVector = visual.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                    Text(
                        text = visual.message,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                visual.action?.let { (label, syncAction) ->
                    TextButton(onClick = { onAction(syncAction) }) {
                        Text(label)
                    }
                }
                if (visual.dismissible) {
                    TextButton(onClick = onDismiss) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}

private data class ChipVisual(
    val label: String,
    val icon: ImageVector?,
    val showSpinner: Boolean,
    val containerColor: Color,
    val contentColor: Color,
)

private data class BannerVisual(
    val message: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val action: Pair<String, SyncAction>?,
    val dismissible: Boolean,
)

@Composable
private fun SyncPresentation.toChipVisual(): ChipVisual? {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        SyncPresentation.Hidden,
        SyncPresentation.AuthError,
        is SyncPresentation.StorageError,
        is SyncPresentation.ConflictError,
        -> null

        is SyncPresentation.Syncing ->
            ChipVisual(
                label = if (pendingCount > 0) "Syncing $pendingCount" else "Syncing",
                icon = null,
                showSpinner = true,
                containerColor = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
            )

        is SyncPresentation.Pending ->
            ChipVisual(
                label = "$pendingCount waiting",
                icon = Icons.Filled.CloudSync,
                showSpinner = false,
                containerColor = scheme.secondaryContainer,
                contentColor = scheme.onSecondaryContainer,
            )

        is SyncPresentation.NetworkError ->
            ChipVisual(
                label = if (pendingCount > 0) "Offline · $pendingCount" else "Offline",
                icon = Icons.Filled.CloudOff,
                showSpinner = false,
                containerColor = scheme.tertiaryContainer,
                contentColor = scheme.onTertiaryContainer,
            )
    }
}

@Composable
private fun SyncPresentation.toBannerVisual(): BannerVisual? {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        SyncPresentation.Hidden,
        is SyncPresentation.Syncing,
        is SyncPresentation.Pending,
        is SyncPresentation.NetworkError,
        -> null

        SyncPresentation.AuthError ->
            BannerVisual(
                message = "Your session expired. Sign in again to keep your data backed up.",
                icon = Icons.Filled.Lock,
                // Use errorContainer *tone* but not the harshest; balanced for a calm prompt.
                containerColor = scheme.errorContainer,
                contentColor = scheme.onErrorContainer,
                action = "Sign in" to SyncAction.SignIn,
                dismissible = false,
            )

        is SyncPresentation.StorageError -> {
            val storageDetail =
                if (pendingCount > 0) {
                    "$pendingCount items can't upload."
                } else {
                    "Manage your plan to keep syncing."
                }
            BannerVisual(
                message = "Cloud storage is full. $storageDetail",
                icon = Icons.Filled.Storage,
                containerColor = scheme.errorContainer,
                contentColor = scheme.onErrorContainer,
                action = "Manage" to SyncAction.ManageStorage,
                dismissible = true,
            )
        }

        is SyncPresentation.ConflictError ->
            BannerVisual(
                message =
                    if (conflictCount == 1) {
                        "An edit conflict needs your review."
                    } else {
                        "$conflictCount edit conflicts need your review."
                    },
                icon = Icons.AutoMirrored.Filled.MergeType,
                containerColor = scheme.tertiaryContainer,
                contentColor = scheme.onTertiaryContainer,
                action = "Review" to SyncAction.ReviewConflicts,
                dismissible = false,
            )
    }
}

/**
 * Continuously-rotating sync glyph used in [SyncIndicatorChip] for the `Syncing` state.
 * Replaces a `CircularProgressIndicator` because the latter renders as a tiny arc fragment
 * in static screenshot snapshots — this icon snapshots cleanly at the initial frame
 * (rotation = 0) and animates the same way on device.
 */
@Composable
private fun RotatingSyncIcon(tint: Color) {
    val transition = rememberInfiniteTransition(label = "sync-icon")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1_500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "sync-icon-rotation",
    )
    Icon(
        imageVector = Icons.Filled.Sync,
        contentDescription = null,
        tint = tint,
        modifier =
            Modifier
                .size(14.dp)
                .graphicsLayer { rotationZ = rotation },
    )
}
