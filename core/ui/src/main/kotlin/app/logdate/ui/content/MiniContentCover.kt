package app.logdate.ui.content

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter


@Composable
fun ImageContentCover(
    modifier: Modifier = Modifier,
    imageUri: String? = null,
) {
    MiniContentCover(
        shape = CoverShape.SQUARE,
        size = CoverSize.MEDIUM,
        modifier = modifier,
    ) {
        if (imageUri != null) {
            Image(
                painter = rememberAsyncImagePainter(imageUri),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun MiniContentCover(
    shape: CoverShape,
    size: CoverSize,
    modifier: Modifier = Modifier,
    children: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .applyCoverMask(shape, size)
            .applyCoverSize(shape, size)
            .background(MaterialTheme.colorScheme.primary)
    ) {
        children()
    }
}

private fun Modifier.applyCoverMask(shape: CoverShape, size: CoverSize): Modifier {
    val cornerSize = when (size) {
        CoverSize.SMALL -> 4.dp
        CoverSize.MEDIUM -> 8.dp
    }
    val shapeMask = when (shape) {
        CoverShape.STORY -> RoundedCornerShape(cornerSize)
        CoverShape.JOURNAL -> RoundedCornerShape(topEnd = cornerSize, bottomEnd = cornerSize)
        CoverShape.PROFILE -> CircleShape
        CoverShape.SQUARE -> RoundedCornerShape(cornerSize)
    }
    return clip(shapeMask)
}

private fun Modifier.applyCoverSize(shape: CoverShape, size: CoverSize): Modifier {
    val sizeDimensions = when (size) {
        CoverSize.SMALL -> {
            when (shape) {
                CoverShape.STORY,
                CoverShape.JOURNAL,
                    -> width(30.dp).aspectRatio(3 / 4f)

                CoverShape.PROFILE -> width(32.dp).aspectRatio(1f)
                CoverShape.SQUARE -> width(32.dp).aspectRatio(1f)
            }
        }

        CoverSize.MEDIUM -> {
            when (shape) {
                CoverShape.STORY,
                CoverShape.JOURNAL,
                    -> width(40.dp).aspectRatio(3 / 4f)

                CoverShape.PROFILE -> width(40.dp).aspectRatio(1f)
                CoverShape.SQUARE -> width(40.dp).aspectRatio(1f)
            }
        }
    }
    return sizeDimensions
}

enum class CoverShape {
    /**
     * Corresponding to a story cover.
     */
    STORY,

    /**
     * Corresponding to a journal.
     */
    JOURNAL,

    /**
     * Corresponding to a person's or group's profile.
     */
    PROFILE,

    /**
     * Any other type of content.
     */
    SQUARE,
}

enum class CoverSize {
    SMALL,
    MEDIUM,
}

@Preview
@Composable
private fun MiniContentCoverPreview_Story_Small() {
    MiniContentCover(
        shape = CoverShape.STORY,
        size = CoverSize.SMALL,
    )
}

@Preview
@Composable
private fun MiniContentCoverPreview_Profile_Small() {
    MiniContentCover(
        shape = CoverShape.PROFILE,
        size = CoverSize.SMALL,
    )
}

@Preview
@Composable
private fun MiniContentCoverPreview_Journal_Small() {
    MiniContentCover(
        shape = CoverShape.JOURNAL,
        size = CoverSize.SMALL,
    )
}

@Preview
@Composable
private fun MiniContentCoverPreview_Square_Small() {
    MiniContentCover(
        shape = CoverShape.SQUARE,
        size = CoverSize.SMALL,
    )
}

@Preview
@Composable
private fun MiniContentCoverPreview_Story_Medium() {
    MiniContentCover(
        shape = CoverShape.STORY,
        size = CoverSize.MEDIUM,
    )
}

@Preview
@Composable
private fun MiniContentCoverPreview_Profile_Medium() {
    MiniContentCover(
        shape = CoverShape.PROFILE,
        size = CoverSize.MEDIUM,
    )
}

@Preview
@Composable
private fun MiniContentCoverPreview_Journal_Medium() {
    MiniContentCover(
        shape = CoverShape.JOURNAL,
        size = CoverSize.MEDIUM,
    )
}

@Preview
@Composable
private fun MiniContentCoverPreview_Square_Medium() {
    MiniContentCover(
        shape = CoverShape.SQUARE,
        size = CoverSize.MEDIUM,
    )
}