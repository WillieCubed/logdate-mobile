package app.logdate.feature.remotedisplay

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.logdate.client.media.video.ExoPlayerPool
import io.github.aakira.napier.Napier
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Full-screen presentation of media on an external display.
 *
 * Shows photos or videos edge-to-edge with a black background.
 * No UI chrome — the phone acts as the controller.
 */
@OptIn(UnstableApi::class)
class MediaPresentation(
    context: Context,
    display: Display,
) : Presentation(context, display),
    KoinComponent {
    private val exoPlayerPool: ExoPlayerPool = get()

    private lateinit var container: FrameLayout
    private var currentImageView: ImageView? = null
    private var currentPlayer: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container =
            FrameLayout(context).apply {
                setBackgroundColor(Color.BLACK)
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    )
            }
        setContentView(container)
    }

    /**
     * Shows a media item on the external display.
     *
     * Replaces any currently displayed media. Supports images and videos.
     */
    fun showMedia(
        uri: Uri,
        mimeType: String,
    ) {
        container.removeAllViews()
        currentImageView = null
        releaseCurrentPlayer()

        if (mimeType.startsWith("video/")) {
            showVideo(uri)
        } else {
            showImage(uri)
        }
    }

    private fun showImage(uri: Uri) {
        val imageView =
            ImageView(context).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER,
                    )
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageURI(uri)
            }
        container.addView(imageView)
        currentImageView = imageView
    }

    private fun showVideo(uri: Uri) {
        val player =
            try {
                exoPlayerPool.acquire().apply {
                    setMediaItem(MediaItem.fromUri(uri))
                    repeatMode = Player.REPEAT_MODE_ONE
                    playWhenReady = true
                    prepare()
                    addListener(
                        object : Player.Listener {
                            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                Napier.e("Video playback error on external display", error)
                            }
                        },
                    )
                }
            } catch (e: Exception) {
                Napier.e("Failed to acquire ExoPlayer for external display", e)
                return
            }
        val playerView =
            PlayerView(context).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER,
                    )
                useController = false
                // FIT keeps portrait videos un-cropped on a 16:9 external screen;
                // the black FrameLayout background fills the unused area.
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                this.player = player
            }
        container.addView(playerView)
        currentPlayer = player
    }

    private fun releaseCurrentPlayer() {
        currentPlayer?.let { player ->
            try {
                exoPlayerPool.release(player)
            } catch (e: Exception) {
                Napier.w("Failed to return ExoPlayer to pool", e)
            }
        }
        currentPlayer = null
    }

    override fun onStop() {
        super.onStop()
        releaseCurrentPlayer()
    }
}
