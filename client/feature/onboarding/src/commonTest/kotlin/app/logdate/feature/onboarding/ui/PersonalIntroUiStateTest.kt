package app.logdate.feature.onboarding.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonalIntroUiStateTest {
    @Test
    fun `an empty bio does not block setup`() {
        val state = PersonalIntroUiState(name = "Willie", bio = "")

        assertTrue(
            state.canContinueFromBio,
            "requiring a bio left anyone unwilling to write one unable to finish setting up",
        )
    }

    @Test
    fun `a name is still required`() {
        assertFalse(PersonalIntroUiState(name = "   ").canContinueFromName)
        assertTrue(PersonalIntroUiState(name = "Willie").canContinueFromName)
    }

    @Test
    fun `a rejected bio still blocks`() {
        val state = PersonalIntroUiState(bio = "x", bioError = "Too short")

        assertFalse(state.canContinueFromBio)
    }
}
