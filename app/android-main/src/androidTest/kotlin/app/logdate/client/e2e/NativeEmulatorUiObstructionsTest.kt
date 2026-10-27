package app.logdate.client.e2e

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeEmulatorUiObstructionsTest {
    @Test
    fun processSystemAnrUsesWaitAndRequiresTheDialogToDisappear() {
        val actions = mutableListOf<String>()
        val waited = mutableListOf<String>()
        assertTrue(
            dismissNativeEmulatorPlatformDialog(
                isTitlePresent = { it == "Process system isn't responding" },
                clickAction = actions::add,
                waitUntilGone = { waited.add(it) },
            ),
        )
        assertEquals(listOf("android:id/aerr_wait"), actions)
        assertEquals(listOf("Process system isn't responding"), waited)
    }

    @Test
    fun appAndUserDataDialogsStayVisible() {
        listOf("LogDate isn't responding", "LogDate keeps stopping", "Delete app data?").forEach { title ->
            assertFalse(
                dismissNativeEmulatorPlatformDialog(
                    isTitlePresent = { it == title },
                    clickAction = { error("An app or user-data dialog must not be dismissed") },
                    waitUntilGone = { error("An app or user-data dialog must remain visible") },
                ),
            )
        }
    }

    @Test
    fun existingPlatformCloseActionsRemainUnchanged() {
        listOf("Bluetooth keeps stopping", "Digital Wellbeing isn't responding").forEach { title ->
            val actions = mutableListOf<String>()
            assertTrue(
                dismissNativeEmulatorPlatformDialog(
                    isTitlePresent = { it == title },
                    clickAction = actions::add,
                    waitUntilGone = { it == title },
                ),
            )
            assertEquals(listOf("android:id/aerr_close"), actions)
        }
    }

    @Test
    fun aPlatformDialogThatRemainsVisibleFails() {
        assertFailsWith<IllegalStateException> {
            dismissNativeEmulatorPlatformDialog(
                isTitlePresent = { it == "Process system isn't responding" },
                clickAction = {},
                waitUntilGone = { false },
            )
        }
    }
}
