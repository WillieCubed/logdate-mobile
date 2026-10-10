package app.logdate.feature.journals.ui.merge

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.ServerProtocolFeature
import kotlinx.coroutines.flow.combine

internal fun journalMergeAvailability(
    flags: FeatureFlagStore,
    config: LogDateConfigRepository,
) = combine(flags.observe(FeatureFlag.JOURNAL_MERGE), config.backendUrl, config.serverDescriptor) { enabled, backendUrl, descriptor ->
    enabled && serverSupportsJournalMerge(backendUrl, descriptor)
}

internal fun serverSupportsJournalMerge(
    backendUrl: String,
    descriptor: ServerDescriptor?,
): Boolean =
    descriptor != null &&
        descriptor.serverOrigin.trimEnd('/') == backendUrl.trimEnd('/') &&
        descriptor.hasProtocolFeature(ServerProtocolFeature.JOURNAL_MERGE_V1)
