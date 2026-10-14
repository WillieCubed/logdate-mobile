@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui.watch.v2

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.feature.core.settings.ui.watch.WatchConnectionState
import app.logdate.feature.core.settings.ui.watch.WatchPeerSnapshotSource
import app.logdate.ui.common.MaterialContainer
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.watch_v2_dismiss
import logdate.client.feature.core.generated.resources.watch_v2_ready_card
import logdate.client.feature.core.generated.resources.watch_v2_ready_card_description
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel

/** Absent on unsupported platforms; never shown until the verified setup has completed. */
@Composable
fun WatchReadyOnPhoneCard(onClick: () -> Unit) {
    if (getKoin().getOrNull<WatchSetupCoordinator>() == null) return
    val model: WatchReadyOfferViewModel = koinViewModel()
    val watchName by model.watchName.collectAsState()
    watchName?.let { name ->
        MaterialContainer {
            ListItem(
                headlineContent = { Text(stringResource(Res.string.watch_v2_ready_card, name)) },
                supportingContent = { Text(stringResource(Res.string.watch_v2_ready_card_description)) },
                leadingContent = { Icon(Icons.Default.Watch, null) },
                trailingContent = {
                    IconButton(onClick = model::dismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.watch_v2_dismiss))
                    }
                },
                modifier =
                    Modifier.clickable {
                        model.dismiss()
                        onClick()
                    },
            )
        }
    }
}

class WatchReadyOfferViewModel(
    private val storage: KeyValueStorage,
    flags: FeatureFlagStore,
    setup: WatchSetupCoordinator,
    snapshots: WatchPeerSnapshotSource,
) : ViewModel() {
    val watchName =
        combine(
            flags.observe(FeatureFlag.WATCH_EXPERIENCE_V2),
            setup.connection(),
            snapshots.observe(),
            storage.observeBoolean(KEY),
            storage.observeBoolean("onboarding_watch_handled"),
        ) { enabled, connection, _, dismissed, onboarded ->
            if (enabled &&
                !dismissed &&
                !onboarded &&
                connection is WatchConnectionState.Connected &&
                setup.isReady(connection)
            ) {
                connection.watchName
            } else {
                null
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismiss() {
        viewModelScope.launch {
            try {
                storage.putBoolean(KEY, true)
                check(storage.getBoolean(KEY)) { "Could not save watch setup card dismissal" }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Napier.e("Could not dismiss watch setup card", failure)
            }
        }
    }

    private companion object {
        const val KEY = "watch.readyCard.handled"
    }
}
