package app.logdate.client.domain.assistants

import app.logdate.client.repository.assistants.AssistantCapability
import app.logdate.client.repository.assistants.AssistantGrant
import app.logdate.client.repository.assistants.AssistantSourceKind
import app.logdate.client.repository.assistants.AssistantSourceMetadata

class AssistantSourceEligibility {
    /** Apply this predicate before scoring, snippets, limits, counts or cursors are computed. */
    fun allows(
        grant: AssistantGrant,
        source: AssistantSourceMetadata,
    ): Boolean {
        if (AssistantCapability.READ_MEMORIES !in grant.capabilities || source.shared) return false
        if (source.id in grant.contentScope.excludedEntryIds) return false
        if (source.kind != AssistantSourceKind.TEXT && source.kind != AssistantSourceKind.TRANSCRIPT) return false
        return if (source.journalIds.isEmpty()) {
            grant.contentScope.includeUnfiled
        } else {
            source.journalIds.all { it in grant.contentScope.journalIds }
        }
    }
}
