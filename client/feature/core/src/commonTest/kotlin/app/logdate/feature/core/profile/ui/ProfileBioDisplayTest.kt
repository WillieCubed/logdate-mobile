package app.logdate.feature.core.profile.ui

import app.logdate.shared.model.profile.LogDateProfile
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileBioDisplayTest {
    @Test
    fun `bio editor starts from original words rather than processed text`() {
        val display =
            createProfileDisplayModel(
                localProfile =
                    LogDateProfile(
                        bio = "A polished description",
                        originalBio = "I keep a journal every morning",
                    ),
            )

        assertEquals("I keep a journal every morning", display.editableBio)
    }
}
