package app.logdate.feature.editor.ui.video

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoPauseVisibilityPolicyTest {
    @Test
    fun keepsPlayingWhenAlreadyVisibleInPictureInPicture() {
        assertTrue(
            shouldKeepVideoVisibleOnPause(
                isInPictureInPictureMode = true,
                isInMultiWindowMode = false,
            ),
        )
    }

    @Test
    fun keepsPlayingWhenVisibleInMultiWindow() {
        assertTrue(
            shouldKeepVideoVisibleOnPause(
                isInPictureInPictureMode = false,
                isInMultiWindowMode = true,
            ),
        )
    }

    @Test
    fun doesNotKeepPlayingWhenNoVisibleVideoSurfaceRemains() {
        assertFalse(
            shouldKeepVideoVisibleOnPause(
                isInPictureInPictureMode = false,
                isInMultiWindowMode = false,
            ),
        )
    }
}
