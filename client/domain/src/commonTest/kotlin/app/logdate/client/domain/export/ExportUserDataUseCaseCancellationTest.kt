package app.logdate.client.domain.export

import app.logdate.client.device.AppInfo
import app.logdate.client.domain.export.support.RoundTripJournalNotesRepository
import app.logdate.client.domain.export.support.RoundTripJournalRepository
import app.logdate.client.domain.export.support.RoundTripLocationHistoryRepository
import app.logdate.client.domain.export.support.RoundTripProfileRepository
import app.logdate.client.domain.export.support.RoundTripUserPlacesRepository
import app.logdate.client.domain.export.support.StubAppInfoProvider
import app.logdate.client.domain.export.support.StubDeviceIdProvider
import app.logdate.client.domain.export.support.StubUserStateRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.places.UserPlacesRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Place
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ExportUserDataUseCaseCancellationTest {
    @Test
    fun cancellingTheCollectorStopsTheExportInsteadOfReadingTheRemainingSources() =
        runTest {
            val draftsRequested = CompletableDeferred<Unit>()
            val places = PlaceReadCounter(RoundTripUserPlacesRepository())
            val useCase =
                ExportUserDataUseCase(
                    journalRepository = HangingDraftsJournalRepository(RoundTripJournalRepository(), draftsRequested),
                    journalNotesRepository = RoundTripJournalNotesRepository(),
                    profileRepository = RoundTripProfileRepository(),
                    userPlacesRepository = places,
                    locationHistoryRepository = RoundTripLocationHistoryRepository(),
                    userStateRepository = StubUserStateRepository(),
                    deviceIdProvider = StubDeviceIdProvider(Uuid.random()),
                    appInfoProvider = StubAppInfoProvider(AppInfo(versionName = "1.0", versionCode = 1, packageName = "test")),
                )
            val emissions = mutableListOf<ExportProgress>()

            val collector = launch { useCase.exportUserData().collect { emissions += it } }
            draftsRequested.await()
            collector.cancelAndJoin()

            assertEquals(0, places.reads, "a cancelled export must not continue reading data sources")
            assertTrue(emissions.none { it is ExportProgress.Failed }, "cancellation is not a failure: $emissions")
        }

    private class HangingDraftsJournalRepository(
        delegate: JournalRepository,
        private val draftsRequested: CompletableDeferred<Unit>,
    ) : JournalRepository by delegate {
        override suspend fun getAllDrafts(): List<EditorDraft> {
            draftsRequested.complete(Unit)
            awaitCancellation()
        }
    }

    private class PlaceReadCounter(
        private val delegate: UserPlacesRepository,
    ) : UserPlacesRepository by delegate {
        var reads = 0
            private set

        override suspend fun getAllPlaces(): List<Place> {
            reads++
            return delegate.getAllPlaces()
        }
    }
}
