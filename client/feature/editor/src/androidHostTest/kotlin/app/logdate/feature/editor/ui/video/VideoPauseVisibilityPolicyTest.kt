package app.logdate.feature.editor.ui.video

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoPauseVisibilityPolicyTest {
    @Test
    fun `keeps playing when already visible in picture in picture`() {
        assertTrue(
            shouldKeepVideoVisibleOnPause(
                isInPictureInPictureMode = true,
                isInMultiWindowMode = false,
            ),
        )
    }

    @Test
    fun `keeps playing when visible in multi window`() {
        assertTrue(
            shouldKeepVideoVisibleOnPause(
                isInPictureInPictureMode = false,
                isInMultiWindowMode = true,
            ),
        )
    }

    @Test
    fun `does not keep playing when no visible video surface remains`() {
        assertFalse(
            shouldKeepVideoVisibleOnPause(
                isInPictureInPictureMode = false,
                isInMultiWindowMode = false,
            ),
        )
    }
}
