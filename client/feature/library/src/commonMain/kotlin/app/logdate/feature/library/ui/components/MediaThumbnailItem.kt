@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.logdate.feature.library.ui.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.logdate.feature.library.ui.LibraryMediaItem
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import app.logdate.ui.LocalSharedTransitionScope
import coil3.compose.AsyncImage
import kotlin.uuid.Uuid

/**
 * Shared element key prefix for library media transitions.
 */
const val LIBRARY_MEDIA_TRANSITION_KEY = "library-media"

/**
 * A single thumbnail in the media grid, displaying an image preview with a video badge overlay
 * when the item is a video. Participates in shared element transitions with the detail view.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun MediaThumbnailItem(
    item: LibraryMediaItem,
    onItemClick: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

    val sharedModifier =
        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedElement(
                    rememberSharedContentState(key = "$LIBRARY_MEDIA_TRANSITION_KEY-${item.uid}"),
                    animatedVisibilityScope,
                )
            }
        } else {
            Modifier
        }

    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clickable { onItemClick(item.uid) },
    ) {
        AsyncImage(
            model = item.thumbnailUri ?: item.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = sharedModifier.fillMaxSize(),
        )
        if (item.isVideo) {
            Icon(
                imageVector = Icons.Filled.PlayCircleFilled,
                contentDescription = "Video",
                tint = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(20.dp),
            )
        }
    }
}
