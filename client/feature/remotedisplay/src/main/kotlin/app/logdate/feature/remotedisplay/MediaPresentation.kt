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
import android.widget.VideoView
import io.github.aakira.napier.Napier

/**
 * Full-screen presentation of media on an external display.
 *
 * Shows photos or videos edge-to-edge with a black background.
 * No UI chrome — the phone acts as the controller.
 */
class MediaPresentation(
    context: Context,
    display: Display,
) : Presentation(context, display) {
    private lateinit var container: FrameLayout
    private var currentImageView: ImageView? = null
    private var currentVideoView: VideoView? = null

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
        currentVideoView?.stopPlayback()
        currentVideoView = null

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
        val videoView =
            VideoView(context).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER,
                    )
                setVideoURI(uri)
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    start()
                }
                setOnErrorListener { _, what, extra ->
                    Napier.e("Video playback error on external display: what=$what, extra=$extra")
                    true
                }
            }
        container.addView(videoView)
        currentVideoView = videoView
    }

    override fun onStop() {
        super.onStop()
        currentVideoView?.stopPlayback()
    }
}
