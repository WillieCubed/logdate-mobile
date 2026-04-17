package app.logdate.feature.core.people.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.repository.knowledge.ContactImportSummary
import app.logdate.client.repository.knowledge.DeviceContact
import app.logdate.client.repository.knowledge.DeviceContactsReader
import app.logdate.client.repository.knowledge.InferredPeopleRepository
import app.logdate.client.repository.knowledge.PeopleContactsAccessMode
import app.logdate.client.repository.knowledge.PeopleContactsRepository
import app.logdate.client.repository.knowledge.PeopleRepository
import app.logdate.shared.model.InferredPersonCluster
import app.logdate.shared.model.InferredPersonEvidence
import app.logdate.shared.model.Person
import app.logdate.shared.model.PersonOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class PeopleSettingsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var preferences: LogdatePreferencesDataSource

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        preferences = LogdatePreferencesDataSource(InMemoryPreferencesDataStore())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun emits_default_enabled_state_on_fresh_install() =
        runTest(testDispatcher) {
            val peopleStore = FakePeopleStore()
            val viewModel = newViewModel(peopleStore, FakeDeviceContactsReader())
            val collectJob = startCollecting(viewModel.uiState)

            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isPeopleEnabled)

            tearDownViewModel(viewModel, collectJob)
        }

    @Test
    fun importAllContacts_is_blocked_while_people_is_disabled() =
        runTest(testDispatcher) {
            val peopleStore = FakePeopleStore()
            val viewModel =
                newViewModel(
                    peopleStore = peopleStore,
                    deviceContactsReader =
                        FakeDeviceContactsReader(
                            fullContacts = listOf(DeviceContact(lookupKey = "ava", displayName = "Ava")),
                        ),
                )
            val collectJob = startCollecting(viewModel.uiState)

            viewModel.setPeopleEnabled(false)
            advanceUntilIdle()
            viewModel.importAllContacts()
            advanceUntilIdle()

            assertEquals(0, peopleStore.importCalls)
            assertIs<PeopleSettingsNotice.PeopleDisabled>(viewModel.uiState.value.notice)

            tearDownViewModel(viewModel, collectJob)
        }

    @Test
    fun enabling_people_allows_full_contact_import_and_persists_access_mode() =
        runTest(testDispatcher) {
            val peopleStore = FakePeopleStore()
            val viewModel =
                newViewModel(
                    peopleStore = peopleStore,
                    deviceContactsReader =
                        FakeDeviceContactsReader(
                            fullContacts =
                                listOf(
                                    DeviceContact(lookupKey = "ava", displayName = "Ava"),
                                    DeviceContact(lookupKey = "sam", displayName = "Sam"),
                                ),
                        ),
                )
            val collectJob = startCollecting(viewModel.uiState)

            viewModel.setPeopleEnabled(true)
            advanceUntilIdle()
            viewModel.importAllContacts()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isPeopleEnabled)
            assertEquals(2, viewModel.uiState.value.totalPeopleCount)
            assertEquals(PeopleContactsAccessMode.FULL.name, preferences.observePeopleContactsAccessMode().first())
            assertIs<PeopleSettingsNotice.ContactsImported>(viewModel.uiState.value.notice)
            assertTrue(peopleStore.refreshCalls > 0)

            tearDownViewModel(viewModel, collectJob)
        }

    @Test
    fun selected_contact_import_updates_selected_access_mode() =
        runTest(testDispatcher) {
            val peopleStore = FakePeopleStore()
            val viewModel =
                newViewModel(
                    peopleStore = peopleStore,
                    deviceContactsReader =
                        FakeDeviceContactsReader(
                            selectedContactsBySession =
                                mapOf(
                                    "session://contacts" to
                                        listOf(DeviceContact(lookupKey = "ava", displayName = "Ava")),
                                ),
                            supportsSelectedContactsPicker = true,
                        ),
                )
            val collectJob = startCollecting(viewModel.uiState)

            viewModel.setPeopleEnabled(true)
            advanceUntilIdle()
            viewModel.importSelectedContacts("session://contacts")
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.totalPeopleCount)
            assertEquals(PeopleContactsAccessMode.SELECTED.name, preferences.observePeopleContactsAccessMode().first())
            assertTrue(peopleStore.refreshCalls > 0)

            tearDownViewModel(viewModel, collectJob)
        }

    private fun newViewModel(
        peopleStore: FakePeopleStore,
        deviceContactsReader: FakeDeviceContactsReader,
    ): PeopleSettingsViewModel =
        PeopleSettingsViewModel(
            preferencesDataSource = preferences,
            peopleRepository = peopleStore,
            peopleContactsRepository = peopleStore,
            deviceContactsReader = deviceContactsReader,
            inferredPeopleRepository = peopleStore,
        )

    private fun TestScope.startCollecting(stateFlow: StateFlow<*>): Job = stateFlow.onEach { }.launchIn(this)

    private suspend fun tearDownViewModel(
        viewModel: PeopleSettingsViewModel,
        collectJob: Job,
    ) {
        collectJob.cancelAndJoin()
        val scopeJob = viewModel.viewModelScope.coroutineContext[Job]
        scopeJob?.children?.toList()?.forEach { child -> child.cancelAndJoin() }
    }
}

private class FakePeopleStore :
    PeopleRepository,
    PeopleContactsRepository,
    InferredPeopleRepository {
    private val people = MutableStateFlow<List<Person>>(emptyList())
    private val selectedCount = MutableStateFlow(0)
    private val fullCount = MutableStateFlow(0)
    private val clusters = MutableStateFlow<List<InferredPersonCluster>>(emptyList())

    var importCalls: Int = 0
    var refreshCalls: Int = 0

    override suspend fun getPerson(uid: Uuid): Person = people.value.first { it.uid == uid }

    override fun getAllPeople(): Flow<List<Person>> = people

    override suspend fun resolvePersonByName(name: String): Person? = null

    override suspend fun resolvePersonByDescription(description: String): Person? = null

    override suspend fun addPerson(person: Person) = Unit

    override suspend fun updatePerson(person: Person) = Unit

    override suspend fun deletePerson(uid: Uuid) = Unit

    override suspend fun addAliasToPerson(
        personUid: Uuid,
        alias: String,
    ) = Unit

    override suspend fun removeAliasFromPerson(
        personUid: Uuid,
        alias: String,
    ) = Unit

    override fun observeImportedPeopleCount(origin: PersonOrigin): Flow<Int> =
        when (origin) {
            PersonOrigin.CONTACT_SELECTED -> selectedCount
            PersonOrigin.CONTACT_FULL -> fullCount
            PersonOrigin.INFERRED -> MutableStateFlow(0)
            PersonOrigin.MANUAL -> MutableStateFlow(0)
        }

    override suspend fun importContacts(
        contacts: List<DeviceContact>,
        origin: PersonOrigin,
    ): ContactImportSummary {
        importCalls += 1
        val importedPeople =
            contacts.map {
                Person(
                    name = it.displayName,
                    origin = origin,
                )
            }
        people.value = people.value + importedPeople
        when (origin) {
            PersonOrigin.CONTACT_SELECTED -> selectedCount.value += contacts.size
            PersonOrigin.CONTACT_FULL -> fullCount.value += contacts.size
            PersonOrigin.INFERRED -> Unit
            PersonOrigin.MANUAL -> Unit
        }
        return ContactImportSummary(importedCount = contacts.size, updatedCount = 0)
    }

    override fun observeOpenClusters(): Flow<List<InferredPersonCluster>> = clusters

    override fun observeEvidence(clusterId: Uuid): Flow<List<InferredPersonEvidence>> = MutableStateFlow(emptyList())

    override suspend fun refresh() {
        refreshCalls += 1
    }

    override suspend fun confirmClusterAsPerson(clusterId: Uuid) = Unit

    override suspend fun rejectCluster(clusterId: Uuid) = Unit
}

private class FakeDeviceContactsReader(
    private val fullContacts: List<DeviceContact> = emptyList(),
    private val selectedContactsBySession: Map<String, List<DeviceContact>> = emptyMap(),
    private val supportsSelectedContactsPicker: Boolean = false,
) : DeviceContactsReader {
    override fun supportsSelectedContactsPicker(): Boolean = supportsSelectedContactsPicker

    override suspend fun readAllContacts(): List<DeviceContact> = fullContacts

    override suspend fun readSelectedContacts(sessionUri: String): List<DeviceContact> = selectedContactsBySession[sessionUri].orEmpty()
}

private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(mutablePreferencesOf())
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock {
            val updated = transform(state.value)
            state.value = updated
            updated
        }
}
