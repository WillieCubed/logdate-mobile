package app.logdate.ui.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemReduceMotionTest {
    @Test
    fun `disabled animator scale requests reduced motion`() {
        assertTrue(isMotionReducedByAnimatorScale(0f))
    }

    @Test
    fun `normal animator scale permits motion`() {
        assertFalse(isMotionReducedByAnimatorScale(1f))
    }
}
