package app.logdate.client.domain.reminders

import app.logdate.client.domain.recommendation.AmbientPromptCandidate
import app.logdate.client.domain.recommendation.AmbientPromptFamily
import app.logdate.client.domain.recommendation.AmbientPromptPayload
import app.logdate.client.domain.recommendation.FakeKeyValueStorage
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.PresenceResponse
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.Presence
import app.logdate.client.sync.peer.state.PresenceState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PhoneReminderDeliveryTest {
    private val storage = FakeKeyValueStorage()
    private val budget = DeviceReminderBudget(storage)
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private val clock =
        object : Clock {
            override fun now() = state.clock.now
        }
    private val history = ReminderPostedHistory(storage, clock)
    private val context = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private var state =
        PhoneReminderSnapshot(
            ReminderClock(now, TimeZone.UTC),
            ReminderSignals(enabled = true, writtenToday = false, workoutEndedAt = now),
            "signed-out",
        )
    private var current = true
    private var grant = true
    private var worn = PresenceState.ON_WRIST
    private var response: ReminderDeliveryStatus? = ReminderDeliveryStatus.POSTED
    private var afterPresence: () -> Unit = {}
    private var afterDelivery: () -> Unit = {}
    private var phoneSucceeds = true
    private var snapshotReads = 0
    private var claimCalls = 0
    private var presenceCalls = 0
    private var deliveryCalls = 0
    private val supplementalPosts = mutableListOf<AmbientPromptCandidate>()
    private val postedHistory = mutableListOf<AmbientPromptCandidate>()
    private val phonePosts = mutableListOf<Pair<ReminderTrigger, Int>>()

    private fun delivery() =
        PhoneReminderDelivery(
            budget,
            history,
            CoordinatedReminderClaims(
                ReminderDayClaims(storage, PeerRole.PHONE),
                PeerRole.PHONE,
                ReminderClaimPeer {
                    claimCalls++
                    grant
                },
                isCurrent = { current && it == context },
                enabled = { state.signals.enabled },
                clock = clock,
                timeZone = { TimeZone.UTC },
            ),
            WireWatchPresence({ _, _, _ ->
                presenceCalls++
                val bytes =
                    WearJson.encodeToBytes(
                        PresenceResponse.serializer(),
                        PresenceResponse(
                            context = context,
                            presence = Presence(worn, state.clock.now.toEpochMilliseconds()),
                        ),
                    )
                afterPresence()
                bytes
            }, { current && it == context }),
            WireReminderDeliveryPeer { _, _, bytes ->
                deliveryCalls++
                val request = (WearJson.decode(bytes, 1, ReminderDeliveryRequest.serializer()) as Decoded.Supported).value
                afterDelivery()
                response?.let {
                    WearJson.encodeToBytes(
                        ReminderDeliveryResponse.serializer(),
                        ReminderDeliveryResponse(
                            claim = request.claim,
                            status = it,
                        ),
                    )
                }
            },
            snapshot = {
                snapshotReads++
                state
            },
            isCurrent = { current && it == context },
            postPhone = { trigger, marks, day ->
                assertEquals(LocalDate(2026, 10, 12), day)
                phonePosts += trigger to marks
                phoneSucceeds
            },
            clock = clock,
            postSupplemental = { candidate, _, _ ->
                supplementalPosts.add(candidate)
                phoneSucceeds
            },
            supplementalPosted = { postedHistory.add(it) },
            preflight = {
                PhoneReminderPreflight(
                    { PhoneReminderEnvironment(state.audience, state.signals.enabled, false, TimeZone.UTC, state.clock.dayStartHour) },
                    budget,
                    history,
                    clock,
                ).check()
            },
        )

    private fun linked(
        supported: Boolean = true,
        supplements: Boolean = false,
    ) {
        state =
            state.copy(
                watch =
                    ReminderWatch(
                        context,
                        reachable = true,
                        supportsReminders = supported,
                        supportsSupplementalReminders = supplements,
                    ),
            )
    }

    @Test
    fun standalonePhoneUsesTheDurableBudgetAndRecordsItsPost() =
        runTest {
            assertEquals(PhoneReminderOutcome.PHONE_POSTED, delivery().run())
            assertEquals(listOf(ReminderTrigger.WORKOUT_ENDED to 0), phonePosts)
            assertEquals(now, history.latest())
            assertEquals(0, claimCalls + presenceCalls + deliveryCalls)
            assertFalse(budget.available(LocalDate(2026, 10, 12)))
        }

    @Test
    fun wornCapableWatchGetsOneClaimedReminderAndNoPhoneAlert() =
        runTest {
            linked()
            state = state.copy(signals = state.signals.copy(openMarks = 3))
            assertEquals(PhoneReminderOutcome.WATCH_POSTED, delivery().run())
            assertEquals(1, claimCalls)
            assertEquals(1, presenceCalls)
            assertEquals(1, deliveryCalls)
            assertEquals(emptyList(), phonePosts)
            assertEquals(now, history.latest())
        }

    @Test
    fun unknownPresenceUsesThePhoneAfterClaimingTheDay() =
        runTest {
            linked()
            worn = PresenceState.UNKNOWN
            assertEquals(PhoneReminderOutcome.PHONE_POSTED, delivery().run())
            assertEquals(1, claimCalls)
            assertEquals(0, deliveryCalls)
        }

    @Test
    fun unsupportedWatchStartsNoNewProtocolRequests() =
        runTest {
            linked(supported = false)
            assertEquals(PhoneReminderOutcome.PHONE_POSTED, delivery().run())
            assertEquals(0, claimCalls + presenceCalls + deliveryCalls)
        }

    @Test
    fun uncertainWatchOutcomeNeverFallsBackToPhoneOrInventsAPostedTime() =
        runTest {
            linked()
            response = null
            assertEquals(PhoneReminderOutcome.UNCERTAIN, delivery().run())
            assertEquals(emptyList(), phonePosts)
            assertNull(history.latest())
            assertFalse(budget.available(LocalDate(2026, 10, 12)))
        }

    @Test
    fun explicitWatchRefusalAllowsOneCheckedPhoneFallback() =
        runTest {
            linked()
            response = ReminderDeliveryStatus.NOT_POSTED
            assertEquals(PhoneReminderOutcome.PHONE_POSTED, delivery().run())
            assertEquals(1, phonePosts.size)
            assertEquals(now, history.latest())
        }

    @Test
    fun handledClaimDoesNotAlertThePhoneAgain() =
        runTest {
            linked()
            response = ReminderDeliveryStatus.ALREADY_HANDLED
            assertEquals(PhoneReminderOutcome.HELD, delivery().run())
            assertEquals(emptyList(), phonePosts)
        }

    @Test
    fun revokedLinkCannotFallBackAfterAWatchRefusal() =
        runTest {
            linked()
            response = ReminderDeliveryStatus.NOT_POSTED
            afterDelivery = { current = false }
            assertEquals(PhoneReminderOutcome.HELD, delivery().run())
            assertEquals(emptyList(), phonePosts)
        }

    @Test
    fun optOutWhileSamplingCannotDeliver() =
        runTest {
            linked()
            afterPresence = { state = state.copy(signals = state.signals.copy(enabled = false)) }
            assertEquals(PhoneReminderOutcome.HELD, delivery().run())
            assertEquals(0, deliveryCalls)
            assertEquals(emptyList(), phonePosts)
        }

    @Test
    fun changedAudienceCannotUseAnEarlierClaim() =
        runTest {
            linked()
            afterPresence = { state = state.copy(audience = "other-account") }
            assertEquals(PhoneReminderOutcome.HELD, delivery().run())
            assertEquals(0, deliveryCalls)
            assertEquals(emptyList(), phonePosts)
        }

    @Test
    fun staleOnWristReadingUsesThePhoneInstead() =
        runTest {
            linked()
            afterPresence = { state = state.copy(clock = state.clock.copy(now = now + 3.seconds)) }
            assertEquals(PhoneReminderOutcome.PHONE_POSTED, delivery().run())
            assertEquals(0, deliveryCalls)
        }

    @Test
    fun dayRolloverWhileSamplingCannotUseYesterdaysClaim() =
        runTest {
            linked()
            afterPresence = {
                val tomorrow = Instant.parse("2026-10-13T12:00:00Z")
                state =
                    state.copy(
                        clock = state.clock.copy(now = tomorrow),
                        signals = state.signals.copy(workoutEndedAt = tomorrow),
                    )
            }
            assertEquals(PhoneReminderOutcome.HELD, delivery().run())
            assertEquals(0, deliveryCalls)
            assertEquals(emptyList(), phonePosts)
        }

    @Test
    fun existingPeerHistorySuppressesAnotherReminderWithoutConsumingBudget() =
        runTest {
            history.adoptPeer("watch", now.toEpochMilliseconds())
            assertEquals(PhoneReminderOutcome.SKIPPED, delivery().run())
            assertTrue(budget.available(LocalDate(2026, 10, 12)))
            assertEquals(emptyList(), phonePosts)
        }

    @Test
    fun notifierRefusalDoesNotInventAPostOrAuthorizeAnotherAttempt() =
        runTest {
            phoneSucceeds = false
            assertEquals(PhoneReminderOutcome.HELD, delivery().run())
            assertNull(history.latest())
            assertEquals(PhoneReminderOutcome.HELD, delivery().run())
            assertEquals(1, phonePosts.size)
        }

    @Test
    fun simultaneousStandaloneTriggersAndReopeningCannotAlertTwice() =
        runTest {
            val engine = delivery()
            val results = (1..10).map { async { engine.run() } }.awaitAll()
            assertEquals(1, results.count { it == PhoneReminderOutcome.PHONE_POSTED })
            assertEquals(PhoneReminderOutcome.SKIPPED, delivery().run())
            assertEquals(1, phonePosts.size)
        }

    @Test
    fun peerClaimRefusalDoesNotPostLocally() =
        runTest {
            linked()
            grant = false
            assertEquals(PhoneReminderOutcome.HELD, delivery().run())
            assertEquals(0, presenceCalls)
            assertEquals(emptyList(), phonePosts)
        }

    private fun withDraft() {
        state =
            state.copy(
                signals = ReminderSignals(enabled = true, writtenToday = true),
                supplemental =
                    AmbientPromptCandidate(
                        AmbientPromptFamily.DRAFT_RESCUE,
                        100,
                        "draft",
                        AmbientPromptPayload.DraftRescue(Uuid.random()),
                    ),
            )
    }

    @Test fun draftAndMomentDeliveryShareTheSameDailyBudget() =
        runTest {
            withDraft()
            val candidate = state.supplemental!!
            assertEquals(PhoneReminderOutcome.PHONE_POSTED, delivery().run())
            assertEquals(listOf(candidate), supplementalPosts)
            assertEquals(listOf(candidate), postedHistory)
            assertEquals(now, history.latest())
            state = state.copy(signals = ReminderSignals(true, false, workoutEndedAt = now), supplemental = null)
            assertEquals(PhoneReminderOutcome.SKIPPED, delivery().run())
            assertTrue(phonePosts.isEmpty())
        }

    @Test fun draftUsesWatchDeliveryWhenWornAndStillRecordsItsDedupeHistory() =
        runTest {
            withDraft()
            linked(supplements = true)
            assertEquals(PhoneReminderOutcome.WATCH_POSTED, delivery().run())
            assertEquals(1, deliveryCalls)
            assertEquals(listOf(state.supplemental!!), postedHistory)
            assertTrue(supplementalPosts.isEmpty())
        }

    @Test fun resolvedDraftDuringPresenceCheckDoesNotPostOrConsumeBudget() =
        runTest {
            withDraft()
            linked()
            afterPresence = { state = state.copy(supplemental = null) }
            assertEquals(PhoneReminderOutcome.HELD, delivery().run())
            assertTrue(supplementalPosts.isEmpty())
            assertTrue(budget.available(LocalDate(2026, 10, 12)))
        }

    @Test fun sharedOptOutSuppressesDraftsEvenWhenACandidateExists() =
        runTest {
            withDraft()
            state = state.copy(signals = state.signals.copy(enabled = false))
            assertEquals(PhoneReminderOutcome.SKIPPED, delivery().run())
            assertTrue(supplementalPosts.isEmpty())
            assertTrue(budget.available(LocalDate(2026, 10, 12)))
        }

    @Test fun olderWatchCoordinatesTheBudgetButReceivesNoUnsupportedDraftTrigger() =
        runTest {
            withDraft()
            linked()
            assertEquals(PhoneReminderOutcome.PHONE_POSTED, delivery().run())
            assertEquals(1, claimCalls)
            assertEquals(0, deliveryCalls)
            assertEquals(listOf(state.supplemental!!), supplementalPosts)
        }

    @Test fun exhaustedBudgetPreventsPrivateSnapshotReads() =
        runTest {
            budget.consume(LocalDate(2026, 10, 12))
            assertEquals(PhoneReminderOutcome.HELD, delivery().run())
            assertEquals(0, snapshotReads)
        }

    @Test fun peerPostedHistoryPreventsPrivateSnapshotReads() =
        runTest {
            history.adoptPeer("watch", now.toEpochMilliseconds())
            assertEquals(PhoneReminderOutcome.SKIPPED, delivery().run())
            assertEquals(0, snapshotReads)
        }

    @Test fun sharedOptOutPreventsPrivateSnapshotReads() =
        runTest {
            state = state.copy(signals = state.signals.copy(enabled = false))
            assertEquals(PhoneReminderOutcome.SKIPPED, delivery().run())
            assertEquals(0, snapshotReads)
        }
}
