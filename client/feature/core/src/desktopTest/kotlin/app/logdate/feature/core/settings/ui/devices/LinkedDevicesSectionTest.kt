@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui.devices

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import app.logdate.client.sync.peer.devices.LinkedDevicesFailure
import app.logdate.client.text.toUiText
import app.logdate.ui.theme.LogDateTheme
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.devices_platform_wear_os
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LinkedDevicesSectionTest {
    private val watch =
        LinkedDeviceUiState(
            id = "watch-session",
            name = "Pixel Watch 3".toUiText(),
            platformName = Res.string.devices_platform_wear_os,
            linkedAtFormatted = "Oct 3, 2026, 9:41 AM".toUiText(),
            lastSeenFormatted = "Oct 16, 2026, 8:02 AM".toUiText(),
            pairedWatch = false,
            erasesData = true,
        )

    @Test
    fun `a linked device shows its platform and times`() =
        runComposeUiTest {
            setContent { Section(LinkedDevicesUiState.Listed(listOf(watch))) }

            onNodeWithText("Pixel Watch 3").assertExists()
            onNodeWithText("Wear OS").assertExists()
            onNodeWithText("Linked Oct 3, 2026, 9:41 AM · Last seen Oct 16, 2026, 8:02 AM").assertExists()
        }

    @Test
    fun `remove asks first and then removes that device`() =
        runComposeUiTest {
            val removed = mutableListOf<String>()
            setContent { Section(LinkedDevicesUiState.Listed(listOf(watch)), onRemove = { removed += it }) }

            onNode(hasText("Remove") and hasClickAction()).performClick()
            assertTrue(removed.isEmpty())
            onNodeWithText("Remove Device").assertExists()
            onAllNodesWithText("Remove").fetchSemanticsNodes().let { assertEquals(2, it.size) }
            clickLast("Remove")

            assertEquals(listOf("watch-session"), removed)
        }

    @Test
    fun `removing a watch warns that it erases what it has not sent`() =
        runComposeUiTest {
            setContent { Section(LinkedDevicesUiState.Listed(listOf(watch))) }

            onNode(hasText("Remove") and hasClickAction()).performClick()

            onNodeWithText(
                "Remove \"Pixel Watch 3\" from your account? It stops syncing and erases its LogDate data, " +
                    "including any moments it hasn’t sent yet.",
            ).assertExists()
        }

    @Test
    fun `the paired watch appears once when the page shows it as its watch`() =
        runComposeUiTest {
            setContent { Section(LinkedDevicesUiState.Listed(listOf(watch.copy(pairedWatch = true))), hidePairedWatch = true) }

            assertTrue(onAllNodesWithTag(LinkedDevicesTags.SECTION).fetchSemanticsNodes().isEmpty())
        }

    @Test
    fun `the paired watch is listed when the page shows no watch card`() =
        runComposeUiTest {
            setContent { Section(LinkedDevicesUiState.Listed(listOf(watch.copy(pairedWatch = true))), hidePairedWatch = false) }

            onNodeWithText("Pixel Watch 3").assertExists()
        }

    @Test
    fun `a failed load says why and can be tried again`() =
        runComposeUiTest {
            var retries = 0
            setContent { Section(LinkedDevicesUiState.Failed(LinkedDevicesFailure.Refused(503)), onRetry = { retries++ }) }

            onNodeWithText("The LogDate server couldn’t list linked devices (HTTP 503).").assertExists()
            onNodeWithText("Try again").performClick()

            assertEquals(1, retries)
        }

    @Test
    fun `a watch that still has moments to send says so and asks again before retrying`() =
        runComposeUiTest {
            val removed = mutableListOf<String>()
            val notice = LinkedDeviceNotice.RemoveFailed(watch.id, "Pixel Watch 3", LinkedDevicesFailure.WatchRemovalStopped("sync"))
            setContent { Section(LinkedDevicesUiState.Listed(listOf(watch), notice = notice), onRemove = { removed += it }) }

            onNodeWithText("Pixel Watch 3 still has moments to send. Bring it near your phone and try again.").assertExists()
            onNodeWithText("Try again").performClick()
            assertTrue(removed.isEmpty())
            onNodeWithText("Remove Device").assertExists()
            clickLast("Remove")

            assertEquals(listOf("watch-session"), removed)
        }

    @Test
    fun `nothing is shown while linking is unavailable or nothing is linked`() =
        runComposeUiTest {
            setContent {
                Section(LinkedDevicesUiState.Unavailable)
                Section(LinkedDevicesUiState.Listed(emptyList()))
            }

            assertTrue(onAllNodesWithTag(LinkedDevicesTags.SECTION).fetchSemanticsNodes().isEmpty())
        }

    private fun SemanticsNodeInteractionsProvider.clickLast(text: String) {
        val nodes = onAllNodesWithText(text)
        nodes[nodes.fetchSemanticsNodes().size - 1].performClick()
    }

    @Composable
    private fun Section(
        state: LinkedDevicesUiState,
        hidePairedWatch: Boolean = false,
        onRemove: (String) -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        LogDateTheme {
            LinkedDevicesSection(
                state = state,
                hidePairedWatch = hidePairedWatch,
                onRemove = onRemove,
                onRetry = onRetry,
                onDismissNotice = {},
            )
        }
    }
}
