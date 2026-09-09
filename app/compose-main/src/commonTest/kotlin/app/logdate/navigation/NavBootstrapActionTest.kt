package app.logdate.navigation

import app.logdate.feature.core.main.HomeRoute
import app.logdate.feature.core.navigation.BaseRoute
import app.logdate.feature.core.settings.navigation.SettingsRoute
import app.logdate.feature.editor.navigation.EntryEditorRoute
import app.logdate.feature.onboarding.navigation.OnboardingComplete
import app.logdate.feature.onboarding.navigation.OnboardingStart
import app.logdate.feature.onboarding.navigation.PersonalIntro
import kotlin.test.Test
import kotlin.test.assertEquals

class NavBootstrapActionTest {
    @Test
    fun `cold start replaces the bootstrap placeholder with home`() {
        assertEquals(
            NavBootstrapAction.ResetTo(HomeRoute),
            resolveNavBootstrapAction(
                backStack = listOf(BaseRoute),
                isOnboarded = true,
                requiresUnlock = false,
            ),
        )
    }

    @Test
    fun `rotating while writing an entry keeps the editor on the back stack`() {
        assertEquals(
            NavBootstrapAction.None,
            resolveNavBootstrapAction(
                backStack = listOf(HomeRoute, EntryEditorRoute(draftId = "draft-1")),
                isOnboarded = true,
                requiresUnlock = false,
            ),
        )
    }

    @Test
    fun `rotating deep in settings keeps the restored back stack`() {
        assertEquals(
            NavBootstrapAction.None,
            resolveNavBootstrapAction(
                backStack = listOf(HomeRoute, SettingsRoute(), SettingsRoute(settingId = "privacy")),
                isOnboarded = true,
                requiresUnlock = false,
            ),
        )
    }

    @Test
    fun `already on home needs no bootstrap`() {
        assertEquals(
            NavBootstrapAction.None,
            resolveNavBootstrapAction(
                backStack = listOf(HomeRoute),
                isOnboarded = true,
                requiresUnlock = false,
            ),
        )
    }

    @Test
    fun `finishing onboarding replaces the onboarding stack with home`() {
        assertEquals(
            NavBootstrapAction.ResetTo(HomeRoute),
            resolveNavBootstrapAction(
                backStack = listOf(OnboardingStart, PersonalIntro, OnboardingComplete),
                isOnboarded = true,
                requiresUnlock = false,
            ),
        )
    }

    @Test
    fun `cold start without onboarding starts the onboarding flow`() {
        assertEquals(
            NavBootstrapAction.ResetTo(OnboardingStart),
            resolveNavBootstrapAction(
                backStack = listOf(BaseRoute),
                isOnboarded = false,
                requiresUnlock = false,
            ),
        )
    }

    @Test
    fun `rotating mid-onboarding keeps the user on the current step`() {
        assertEquals(
            NavBootstrapAction.None,
            resolveNavBootstrapAction(
                backStack = listOf(OnboardingStart, PersonalIntro),
                isOnboarded = false,
                requiresUnlock = false,
            ),
        )
    }

    @Test
    fun `a locked app keeps the back stack it will return to after unlocking`() {
        assertEquals(
            NavBootstrapAction.None,
            resolveNavBootstrapAction(
                backStack = listOf(HomeRoute, EntryEditorRoute(draftId = "draft-1")),
                isOnboarded = true,
                requiresUnlock = true,
            ),
        )
    }

    @Test
    fun `an empty back stack falls back to home`() {
        assertEquals(
            NavBootstrapAction.ResetTo(HomeRoute),
            resolveNavBootstrapAction(
                backStack = emptyList(),
                isOnboarded = true,
                requiresUnlock = false,
            ),
        )
    }
}
