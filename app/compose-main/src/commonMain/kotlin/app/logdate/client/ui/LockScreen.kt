package app.logdate.client.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.logdate.ui.adaptive.HingeAwareOverlay
import logdate.app.composemain.generated.resources.Res
import logdate.app.composemain.generated.resources.ic_launcher_round_google_play
import logdate.app.composemain.generated.resources.lock_screen_unlock_subtitle
import logdate.app.composemain.generated.resources.lock_screen_use_passcode
import logdate.app.composemain.generated.resources.lock_screen_welcome_back
import logdate.app.composemain.generated.resources.lock_screen_welcome_back_no_name
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * A layout that overlays a [LockScreen] on top of [content] when [isLocked] is true.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun LockableContent(
    isLocked: Boolean,
    displayName: String,
    onUsePasscode: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        content()
        if (isLocked) {
            LockScreen(
                displayName = displayName,
                onUsePasscode = onUsePasscode,
            )
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun LockScreen(
    displayName: String,
    onUsePasscode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val greeting =
        if (displayName.isNotBlank()) {
            stringResource(Res.string.lock_screen_welcome_back, displayName)
        } else {
            stringResource(Res.string.lock_screen_welcome_back_no_name)
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary),
    ) {
        // Keep the obscuring background full-screen, but pin the greeting and unlock action to a
        // single physical pane so they stay reachable and clear of a foldable hinge.
        HingeAwareOverlay {
            Column(
                modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_launcher_round_google_play),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(96.dp)
                            .clip(CircleShape),
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.lock_screen_unlock_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.height(24.dp))
                FilledTonalButton(onClick = onUsePasscode) {
                    Text(stringResource(Res.string.lock_screen_use_passcode))
                }
            }
        }
    }
}
