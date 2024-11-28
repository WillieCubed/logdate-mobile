package app.logdate.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch

/**
 * A chip that displays the person's initials in a circle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonIcon(name: String) {
    val initials by remember(name) {
        derivedStateOf {
            // TODO: Validate initializing logic
            name.split(" ").mapNotNull { it.firstOrNull() }.joinToString("")
        }
    }
    val tooltipScope = rememberCoroutineScope()
    val tooltipState = rememberTooltipState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(name) } },
        state = tooltipState,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    tooltipScope.launch {
                        tooltipState.show()
                    }
                },
        ) {
            Text(
                initials,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.Center),
            )
        }
    }
}

/**
 * A chip that displays the person's profile photo in a circle.
 */
@Composable
fun PersonIcon(photoUri: String, name: String) {
    val photoModel = ImageRequest.Builder(LocalPlatformContext.current)
        .data(photoUri)
        .apply {
            // TODO: Add placeholder
            crossfade(true)
//                    placeholder(R.drawable.ic_image_placeholder)
//                    error(R.drawable.ic_image_error)
        }.build()
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        AsyncImage(
            model = photoModel,
            contentDescription = "Photo of $name",
            modifier = Modifier.size(48.dp),
        )
    }
}

