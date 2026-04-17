package app.logdate.feature.core.people.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.repository.knowledge.DeviceContactsReader
import app.logdate.client.repository.knowledge.InferredPeopleRepository
import app.logdate.client.repository.knowledge.PeopleContactsAccessMode
import app.logdate.client.repository.knowledge.PeopleContactsRepository
import app.logdate.client.repository.knowledge.PeopleRepository
import app.logdate.shared.model.PersonOrigin
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PeopleSettingsViewModel(
    private val preferencesDataSource: LogdatePreferencesDataSource,
    private val peopleRepository: PeopleRepository,
    private val peopleContactsRepository: PeopleContactsRepository,
    private val deviceContactsReader: DeviceContactsReader,
    private val inferredPeopleRepository: InferredPeopleRepository,
) : ViewModel() {
    data class UiState(
        val isPeopleEnabled: Boolean = false,
        val supportsSelectedContactsPicker: Boolean = false,
        val totalPeopleCount: Int = 0,
        val pendingReviewCount: Int = 0,
        val notice: PeopleSettingsNotice? = null,
        val isImporting: Boolean = false,
    )

    private val notice = MutableStateFlow<PeopleSettingsNotice?>(null)
    private val importing = MutableStateFlow(false)

    val uiState: StateFlow<UiState> =
        combine(
            preferencesDataSource.observePeopleEnabled(),
            peopleRepository.getAllPeople().map { it.size },
            inferredPeopleRepository.observeOpenClusters().map { it.size },
        ) { isEnabled, totalCount, pendingReviewCount ->
            UiState(
                isPeopleEnabled = isEnabled,
                supportsSelectedContactsPicker = deviceContactsReader.supportsSelectedContactsPicker(),
                totalPeopleCount = totalCount,
                pendingReviewCount = pendingReviewCount,
            )
        }.combine(notice) { state, notice ->
            state.copy(notice = notice)
        }.combine(importing) { state, isImporting ->
            state.copy(isImporting = isImporting)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState(),
        )

    fun refreshPeople() {
        viewModelScope.launch {
            if (!uiState.value.isPeopleEnabled || importing.value) {
                return@launch
            }
            runCatching {
                inferredPeopleRepository.refresh()
            }.onFailure { error ->
                Napier.e("Failed to refresh inferred people", error)
            }
        }
    }

    fun setPeopleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                preferencesDataSource.setPeopleEnabled(enabled)
                if (enabled) {
                    inferredPeopleRepository.refresh()
                }
            }.onFailure { error ->
                Napier.e("Failed to update People feature flag", error)
                notice.value = PeopleSettingsNotice.SettingsUpdateFailed
            }
        }
    }

    fun importAllContacts() {
        importContacts(
            origin = PersonOrigin.CONTACT_FULL,
            accessMode = PeopleContactsAccessMode.FULL,
            readContacts = deviceContactsReader::readAllContacts,
            failureNotice = PeopleSettingsNotice.ImportAllContactsFailed,
            logMessage = "Failed to import full contacts",
        )
    }

    fun importSelectedContacts(sessionUri: String) {
        importContacts(
            origin = PersonOrigin.CONTACT_SELECTED,
            accessMode = PeopleContactsAccessMode.SELECTED,
            readContacts = { deviceContactsReader.readSelectedContacts(sessionUri) },
            failureNotice = PeopleSettingsNotice.ImportSelectedContactsFailed,
            logMessage = "Failed to import selected contacts",
        )
    }

    fun dismissMessage() {
        notice.value = null
    }

    private fun importContacts(
        origin: PersonOrigin,
        accessMode: PeopleContactsAccessMode,
        readContacts: suspend () -> List<app.logdate.client.repository.knowledge.DeviceContact>,
        failureNotice: PeopleSettingsNotice,
        logMessage: String,
    ) {
        viewModelScope.launch {
            if (importing.value) {
                return@launch
            }

            if (!uiState.value.isPeopleEnabled) {
                notice.value = PeopleSettingsNotice.PeopleDisabled
                return@launch
            }

            importing.value = true
            runCatching {
                val contacts = readContacts()
                val summary =
                    peopleContactsRepository.importContacts(
                        contacts = contacts,
                        origin = origin,
                    )
                preferencesDataSource.setPeopleContactsAccessMode(accessMode.name)
                inferredPeopleRepository.refresh()
                summary
            }.onSuccess { summary ->
                notice.value = summary.toNotice()
            }.onFailure { error ->
                Napier.e(logMessage, error)
                notice.value = failureNotice
            }
            importing.value = false
        }
    }
}
