package app.logdate.feature.core.settings.updates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Verifies the hybrid policy that chooses immediate, flexible, or no Play update prompt. */
class AppUpdatePromptPolicyTest {
    @Test
    fun `automatic checks choose immediate when priority is high`() {
        val result =
            AppUpdatePromptPolicy.chooseFlow(
                context =
                    AppUpdatePromptContext(
                        updatePriority = 5,
                        stalenessDays = 1,
                        immediateAllowed = true,
                        flexibleAllowed = true,
                        isFlexiblePromptDeferred = false,
                    ),
                trigger = AppUpdateCheckTrigger.Automatic,
            )

        assertEquals(AppUpdateFlowType.Immediate, result)
    }

    @Test
    fun `automatic checks choose flexible when release is stale enough`() {
        val result =
            AppUpdatePromptPolicy.chooseFlow(
                context =
                    AppUpdatePromptContext(
                        updatePriority = 1,
                        stalenessDays = 3,
                        immediateAllowed = false,
                        flexibleAllowed = true,
                        isFlexiblePromptDeferred = false,
                    ),
                trigger = AppUpdateCheckTrigger.Automatic,
            )

        assertEquals(AppUpdateFlowType.Flexible, result)
    }

    @Test
    fun `automatic checks skip deferred flexible prompts`() {
        val result =
            AppUpdatePromptPolicy.chooseFlow(
                context =
                    AppUpdatePromptContext(
                        updatePriority = 3,
                        stalenessDays = 5,
                        immediateAllowed = false,
                        flexibleAllowed = true,
                        isFlexiblePromptDeferred = true,
                    ),
                trigger = AppUpdateCheckTrigger.Automatic,
            )

        assertNull(result)
    }

    @Test
    fun `manual checks bypass flexible deferral`() {
        val result =
            AppUpdatePromptPolicy.chooseFlow(
                context =
                    AppUpdatePromptContext(
                        updatePriority = 0,
                        stalenessDays = 0,
                        immediateAllowed = false,
                        flexibleAllowed = true,
                        isFlexiblePromptDeferred = true,
                    ),
                trigger = AppUpdateCheckTrigger.Manual,
            )

        assertEquals(AppUpdateFlowType.Flexible, result)
    }
}
