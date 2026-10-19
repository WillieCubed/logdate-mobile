package app.logdate.feature.core

import kotlin.test.Test
import kotlin.test.assertEquals

class GlobalAppUiLoadedStateTest {
    @Test
    fun `only successful authentication or disabled app lock allows journal access`() {
        AppAuthState.entries.forEach { authState ->
            val state = GlobalAppUiLoadedState(isOnboarded = true, authState = authState)
            val allowed = authState == AppAuthState.AUTHENTICATED || authState == AppAuthState.NO_PROMPT_NEEDED

            assertEquals(allowed, state.isAppUnlocked, authState.name)
            assertEquals(!allowed, state.requiresUnlock, authState.name)
        }
    }
}
