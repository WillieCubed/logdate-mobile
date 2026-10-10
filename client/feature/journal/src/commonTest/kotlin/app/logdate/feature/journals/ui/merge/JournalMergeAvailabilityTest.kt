package app.logdate.feature.journals.ui.merge

import app.logdate.shared.model.DeploymentKind
import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.ServerProtocolFeature
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JournalMergeAvailabilityTest {
    private val supported =
        ServerDescriptor(
            "https://journal.example",
            "https://journal.example/api",
            deploymentKind = DeploymentKind.SELF_HOSTED,
            displayName = "My journal",
            protocolFeatures = listOf(ServerProtocolFeature.JOURNAL_MERGE_V1),
        )

    @Test
    fun `current server must explicitly advertise journal merge support`() {
        assertFalse(serverSupportsJournalMerge("https://journal.example", null))
        assertFalse(serverSupportsJournalMerge("https://journal.example", supported.copy(protocolFeatures = emptyList())))
        assertFalse(serverSupportsJournalMerge("https://other.example", supported))
        assertTrue(serverSupportsJournalMerge("https://journal.example/", supported))
    }
}
