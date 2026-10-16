package app.logdate.wear.v2.today

import app.cash.turbine.test
import app.logdate.client.domain.streak.CampfireState
import app.logdate.client.domain.streak.FirePhase
import app.logdate.client.domain.streak.FireSize
import app.logdate.client.domain.streak.ObserveCampfireUseCase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.SyncPausedReason
import app.logdate.client.sync.SyncStatus
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.ui.streak.CampfirePhase
import app.logdate.ui.streak.CampfirePresentation
import app.logdate.ui.streak.CampfireSize
import app.logdate.wear.capture.Feeling
import app.logdate.wear.sync.v2.link.WatchLinkRecord
import app.logdate.wear.sync.v2.link.WatchLinkStore
import app.logdate.wear.v2.phone.HandoffResult
import app.logdate.wear.v2.phone.PhoneHandoff
import app.logdate.wear.v2.phone.PhoneLink
import app.logdate.wear.v2.phone.PhoneLinkRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    private val zone = TimeZone.of("Europe/Berlin")
    private val saturday = window(LocalDate(2026, 10, 3))
    private val sunday = window(LocalDate(2026, 10, 4))
    private val dayClock = mockk<DayClock>()
    private val notesRepository = mockk<JournalNotesRepository>()
    private val observeCampfire = mockk<ObserveCampfireUseCase>()
    private val phoneLinks = mockk<PhoneLinkRepository>()
    private val watchLinks = mockk<WatchLinkStore>()
    private val phoneHandoff = mockk<PhoneHandoff>()
    private val syncStatus =
        MutableStateFlow(SyncStatus(isEnabled = true, lastSyncTime = null, pendingUploads = 0, isSyncing = false, hasErrors = false))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { dayClock.observe() } returns flowOf(saturday)
        every { observeCampfire.invoke() } returns flowOf(null)
        every { phoneLinks.observe() } returns flowOf(PhoneLink.NotConnected)
        every { watchLinks.observe() } returns flowOf(null)
        notesFor(saturday)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the day's notes become today's summary`() =
        runTest {
            notesFor(
                saturday,
                text("Lunch by the water", at(saturday, hour = 12)),
                JournalNote.Audio(
                    mediaRef = "audio/walk.m4a",
                    durationMs = 252_000,
                    creationTimestamp = at(saturday, 14),
                    lastUpdated = at(saturday, 14),
                ),
                text("#mood:good Feeling good", at(saturday, hour = 15)),
            )

            viewModel().uiState.test {
                val today = expectMostRecentItem() as TodayUiState.Ready
                assertEquals(LocalDate(2026, 10, 3), today.date)
                assertEquals(2, today.summary.momentCount)
                assertEquals(252.seconds, today.summary.voiceDuration)
                assertEquals(Feeling.GOOD, today.feeling)
            }
        }

    @Test
    fun `notes from another day are left out`() =
        runTest {
            notesFor(saturday, text("Friday night", saturday.start - 1.hours), text("Breakfast", at(saturday, hour = 8)))

            viewModel().uiState.test {
                assertEquals(1, (expectMostRecentItem() as TodayUiState.Ready).summary.momentCount)
            }
        }

    @Test
    fun `the summary moves to the next day at midnight`() =
        runTest {
            every { dayClock.observe() } returns flowOf(saturday, sunday)
            notesFor(saturday, text("Saturday", at(saturday, hour = 10)))
            notesFor(sunday)

            viewModel().uiState.test {
                val today = expectMostRecentItem() as TodayUiState.Ready
                assertEquals(LocalDate(2026, 10, 4), today.date)
                assertEquals(0, today.summary.momentCount)
            }
        }

    @Test
    fun `the campfire follows the shared campfire`() =
        runTest {
            every { observeCampfire.invoke() } returns
                flowOf(
                    CampfireState(
                        phase = FirePhase.BURNING,
                        loggedToday = true,
                        runDays = 3,
                        size = FireSize.SMALL,
                        longestRunDays = 5,
                        totalDaysJournaled = 9,
                        isRekindled = false,
                    ),
                )

            viewModel().uiState.test {
                assertEquals(
                    CampfirePresentation(
                        phase = CampfirePhase.BURNING,
                        loggedToday = true,
                        runDays = 3,
                        size = CampfireSize.SMALL,
                        longestRunDays = 5,
                        totalDaysJournaled = 9,
                        isRekindled = false,
                    ),
                    (expectMostRecentItem() as TodayUiState.Ready).campfire,
                )
            }
        }

    @Test
    fun `no campfire is shown while streaks are off`() =
        runTest {
            viewModel().uiState.test {
                assertNull((expectMostRecentItem() as TodayUiState.Ready).campfire)
            }
        }

    @Test
    fun `an Android phone without LogDate is offered the app`() =
        runTest {
            every { phoneLinks.observe() } returns flowOf(PhoneLink.AppMissing(nodeId = "phone-1"))

            viewModel().uiState.test {
                assertEquals(PhoneCard.GetApp(nodeId = "phone-1"), (expectMostRecentItem() as TodayUiState.Ready).phoneCard)
            }
        }

    @Test
    fun `an unlinked watch offers setup on its connected phone`() =
        runTest {
            every { phoneLinks.observe() } returns flowOf(PhoneLink.Connected(nodeId = "phone-1", phoneName = "Pixel 9"))

            viewModel().uiState.test {
                assertEquals(PhoneCard.SetUp(nodeId = "phone-1"), (expectMostRecentItem() as TodayUiState.Ready).phoneCard)
            }
        }

    @Test
    fun `a linked watch does not ask for setup again`() =
        runTest {
            every { phoneLinks.observe() } returns flowOf(PhoneLink.Connected(nodeId = "phone-1", phoneName = "Pixel 9"))
            every { watchLinks.observe() } returns
                flowOf(WatchLinkRecord(mode = LinkMode.CLOUD, phoneNode = "phone-1", linkedAt = 1L, accountId = "account-1"))

            viewModel().uiState.test {
                assertEquals(PhoneCard.None, (expectMostRecentItem() as TodayUiState.Ready).phoneCard)
            }
        }

    @Test
    fun `a linked watch whose sync needs a device link asks for setup again`() =
        runTest {
            every { phoneLinks.observe() } returns flowOf(PhoneLink.Connected(nodeId = "phone-1", phoneName = "Pixel 9"))
            every { watchLinks.observe() } returns
                flowOf(WatchLinkRecord(mode = LinkMode.CLOUD, phoneNode = "phone-1", linkedAt = 1L, accountId = "account-1"))

            viewModel().uiState.test {
                assertEquals(PhoneCard.None, (expectMostRecentItem() as TodayUiState.Ready).phoneCard)
                syncStatus.value = syncStatus.value.copy(pausedReason = SyncPausedReason.NEEDS_DEVICE_LINK)
                assertEquals(PhoneCard.SetUp("phone-1"), (awaitItem() as TodayUiState.Ready).phoneCard)
            }
        }

    @Test
    fun `setup card leaves Today when phone linking finishes`() =
        runTest {
            val link = MutableStateFlow<WatchLinkRecord?>(null)
            every { phoneLinks.observe() } returns flowOf(PhoneLink.Connected(nodeId = "phone-1", phoneName = "Pixel 9"))
            every { watchLinks.observe() } returns link

            viewModel().uiState.test {
                assertEquals(PhoneCard.SetUp("phone-1"), (expectMostRecentItem() as TodayUiState.Ready).phoneCard)
                link.value = WatchLinkRecord(mode = LinkMode.CLOUD, phoneNode = "phone-1", linkedAt = 1L, accountId = "account-1")
                assertEquals(PhoneCard.None, (awaitItem() as TodayUiState.Ready).phoneCard)
            }
        }

    @Test
    fun `setup opens the Watch settings on the connected phone`() =
        runTest {
            every { phoneLinks.observe() } returns flowOf(PhoneLink.Connected(nodeId = "phone-1", phoneName = "Pixel 9"))
            coEvery { phoneHandoff.open("studio.hypertext.logdate://settings/watch", "phone-1") } returns HandoffResult.SENT
            val viewModel = viewModel()

            viewModel.uiState.test {
                expectMostRecentItem()
                viewModel.setUpPhone()

                assertEquals(HandoffState.SENT, viewModel.handoff.value)
                coVerify(exactly = 1) { phoneHandoff.open("studio.hypertext.logdate://settings/watch", "phone-1") }
            }
        }

    @Test
    fun `getting the app opens its listing on that phone`() =
        runTest {
            every { phoneLinks.observe() } returns flowOf(PhoneLink.AppMissing(nodeId = "phone-1"))
            coEvery { phoneHandoff.openAppListing("phone-1") } returns HandoffResult.SENT
            val viewModel = viewModel()

            viewModel.uiState.test {
                expectMostRecentItem()
                viewModel.getPhoneApp()

                assertEquals(HandoffState.SENT, viewModel.handoff.value)
                coVerify(exactly = 1) { phoneHandoff.openAppListing("phone-1") }
            }
        }

    @Test
    fun `a refused handoff is reported until dismissed`() =
        runTest {
            every { phoneLinks.observe() } returns flowOf(PhoneLink.AppMissing(nodeId = "phone-1"))
            coEvery { phoneHandoff.openAppListing("phone-1") } returns HandoffResult.FAILED
            val viewModel = viewModel()

            viewModel.uiState.test {
                expectMostRecentItem()
                viewModel.getPhoneApp()
                assertEquals(HandoffState.FAILED, viewModel.handoff.value)

                viewModel.dismissHandoff()
                assertEquals(HandoffState.IDLE, viewModel.handoff.value)
            }
        }

    @Test
    fun `a failure reading notes shows the failed state`() =
        runTest {
            every { notesRepository.observeNotesInRange(any(), any()) } returns flow { throw IOException("database closed") }

            viewModel().uiState.test {
                assertEquals(TodayUiState.Failed, expectMostRecentItem())
            }
        }

    private fun viewModel() =
        TodayViewModel(
            dayClock = dayClock,
            notesRepository = notesRepository,
            observeCampfire = observeCampfire,
            phoneLinks = phoneLinks,
            watchLinks = watchLinks,
            phoneHandoff = phoneHandoff,
            syncStatus = syncStatus,
        )

    private fun notesFor(
        day: TodayWindow,
        vararg notes: JournalNote,
    ) {
        every { notesRepository.observeNotesInRange(day.start, day.end) } returns flowOf(notes.toList())
    }

    private fun window(date: LocalDate) =
        TodayWindow(
            date = date,
            timeZone = zone,
            start = date.atStartOfDayIn(zone),
            end = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone),
        )

    private fun at(
        day: TodayWindow,
        hour: Int,
    ): Instant = LocalDateTime(day.date, LocalTime(hour, 0)).toInstant(zone)

    private fun text(
        content: String,
        at: Instant,
    ) = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = content)
}
