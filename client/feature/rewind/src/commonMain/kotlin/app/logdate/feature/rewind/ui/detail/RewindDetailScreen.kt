package app.logdate.feature.rewind.ui.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.feature.rewind.ui.RewindDetailUiState
import app.logdate.feature.rewind.ui.RewindDetailViewModel
import app.logdate.feature.rewind.ui.totalPanels
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.uuid.Uuid

/**
 * The main screen to interact with a Rewind.
 */
@Composable
internal fun RewindDetailScreen(
    rewindId: Uuid,
    onExitRewind: () -> Unit,
    viewModel: RewindDetailViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigationState = rememberRewindNavigationState()

    LaunchedEffect(rewindId) {
        when (uiState) {
            is RewindDetailUiState.Loading -> {}
            is RewindDetailUiState.Success -> {
                navigationState.setPanelCount((uiState as RewindDetailUiState.Success).totalPanels)
            }

            is RewindDetailUiState.Error -> {
                Napier.e(tag = "RewindDetailScreen", message = "Error loading Rewind")
            }
        }
    }

    RewindWrapperLayout(
        onBack = navigationState::navigateBack,
        onForward = navigationState::navigateForward,
        mainContent = {
        }
    )
}

@Composable
internal fun RewindWrapperLayout(
    onBack: () -> Unit,
    onForward: () -> Unit,
    mainContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
//    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    // If the width is medium or larger, show buttons to navigate between panels
    Box(modifier = modifier) {

    }
}

/**
 *
 */
@Composable
internal fun RewindPanelWrapper() {

}


/**
 * A panel that can be swiped away.
 *
 * @param onDismiss Callback when the panel is dismissed (swiped away).
 */
@Composable
fun SwipeableRewindPanel(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val panelScope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val maxWidth = constraints.maxWidth.toFloat()
        val maxHeight = constraints.maxHeight.toFloat()

        // Track if the card has been dismissed
        var isDismissed by remember { mutableStateOf(false) }

        // Animated offset for the card position
        val offset = remember {
            Animatable(
                initialValue = Offset.Zero,
                typeConverter = Offset.VectorConverter,
            )
        }

        // Calculate rotation based on horizontal offset
        val rotation = (offset.value.x / maxWidth) * 45f  // Max rotation of 45 degrees

        // Calculate scale based on vertical movement
        val scale = 1f - (offset.value.y.absoluteValue / maxHeight).coerceIn(0f, 0.2f)

        // Reset animation when panel changes
        LaunchedEffect(content) {
            isDismissed = false
            offset.snapTo(Offset.Zero)
        }

        if (!isDismissed) {
            Card(
                modifier = Modifier
                    .fillMaxSize(0.9f) // Card takes 90% of the screen
                    .offset {
                        IntOffset(
                            offset.value.x.roundToInt(),
                            offset.value.y.roundToInt()
                        )
                    }
                    .graphicsLayer {
                        rotationZ = rotation
                        scaleX = scale
                        scaleY = scale
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                panelScope.launch {
                                    offset.snapTo(
                                        Offset(
                                            offset.value.x + dragAmount.x,
                                            offset.value.y + dragAmount.y,
                                        )
                                    )
                                }
                                change.consume()
                            },
                            onDragEnd = {
                                panelScope.launch {
                                    // Calculate velocity and distance to determine if should dismiss
                                    val offsetX = offset.value.x
                                    val offsetY = offset.value.y

                                    // Dismiss threshold - 40% of screen width
                                    val shouldDismiss = offsetX.absoluteValue > maxWidth * 0.4f ||
                                            offsetY.absoluteValue > maxHeight * 0.4f

                                    if (shouldDismiss) {
                                        // Animate to edge of screen
                                        val targetX = maxWidth * 1.5f * (offsetX.sign)
                                        val targetY = maxHeight * 1.5f * (offsetY.sign)

                                        offset.animateTo(
                                            targetValue = Offset(targetX, targetY),
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessLow,
                                            )
                                        )
                                        isDismissed = true
                                        onDismiss()
                                    } else {
                                        // Animate back to center
                                        offset.animateTo(
                                            targetValue = Offset.Zero,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium,
                                            )
                                        )
                                    }
                                }
                            }
                        )
                    },
                elevation = CardDefaults.elevatedCardElevation(
                    defaultElevation = (8 - (offset.value.y.absoluteValue / maxHeight * 4))
                        .coerceAtLeast(1f).dp
                )
            ) {
                content()
            }
        }
    }
}