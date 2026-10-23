package app.logdate.server.migration

import app.logdate.shared.model.AccountStorageMode
import app.logdate.shared.model.sync.SpaceMigrationErrorCodes
import app.logdate.shared.model.sync.SpaceMigrationPhase
import app.logdate.shared.model.sync.SpaceMigrationStatus
import app.logdate.shared.model.sync.SpaceMigrationSwitchBackRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * The server's side of moving a journal: one device at a time, nothing switches until writes have
 * been held long enough and the device's proof matches both stores, and switching back works only
 * while the legacy copy is kept.
 */
class SpaceMigrationServiceTest {
    @Test
    fun `a device switches an account once its proof matches both stores`() =
        runTest {
            val fixture = MigrationFixture()
            val account = fixture.legacyAccount()
            fixture.addLegacyEntry(account, "entry-one")
            fixture.addLocationRecord(account, "place-one")
            fixture.putSpaceRecord(account, "sealed-one")
            fixture.putSpaceRecord(account, "sealed-two")
            fixture.service.begin(account, "move-1").done()
            fixture.service.hold(account, "move-1").done()
            fixture.clock.advance(fixture.config.holdGrace)

            val switched = fixture.service.switch(account, fixture.proof(account, "move-1")).done()

            assertEquals(SpaceMigrationPhase.SWITCHED, switched.phase)
            assertEquals(2, switched.switchedAtSeq)
            assertEquals(AccountStorageMode.PERSONAL_SPACE_V1, fixture.mode(account))
            assertEquals(switched.switchedAt!! + 30.days.inWholeMilliseconds, switched.legacyKeptUntil)
            assertTrue(fixture.holds.movedByMigration(account))
            assertFalse(fixture.holds.legacyWritesHeld(account), "The switch ends the hold")
        }

    @Test
    fun `the switch waits until every write that began before the hold has finished`() =
        runTest {
            val fixture = MigrationFixture()
            val account = fixture.legacyAccount()
            fixture.service.begin(account, "move-1").done()

            fixture.service.switch(account, fixture.proof(account, "move-1")).refusedWith(SpaceMigrationErrorCodes.HOLD_NOT_READY)
            val held = fixture.service.hold(account, "move-1").done()
            assertTrue(fixture.holds.legacyWritesHeld(account))
            assertEquals(fixture.clock.now().toEpochMilliseconds() + 2.minutes.inWholeMilliseconds, held.holdReadyAt)
            fixture.clock.advance(1.minutes)
            fixture.service.switch(account, fixture.proof(account, "move-1")).refusedWith(SpaceMigrationErrorCodes.HOLD_NOT_READY)
            fixture.clock.advance(fixture.config.holdLifetime)
            assertFalse(fixture.holds.legacyWritesHeld(account), "A hold the device abandons lapses on its own")
            fixture.service.switch(account, fixture.proof(account, "move-1")).refusedWith(SpaceMigrationErrorCodes.HOLD_NOT_READY)

            assertEquals(AccountStorageMode.LEGACY, fixture.mode(account))
        }

    @Test
    fun `a change to either store after the check keeps the account on legacy storage`() =
        runTest {
            val fixture = MigrationFixture()
            val account = fixture.legacyAccount()
            fixture.addLegacyEntry(account, "entry-one")
            fixture.putSpaceRecord(account, "sealed-one")
            fixture.service.begin(account, "move-1").done()
            fixture.service.hold(account, "move-1").done()
            fixture.clock.advance(fixture.config.holdGrace)

            val checked = fixture.proof(account, "move-1")
            fixture.addLegacyEntry(account, "entry-two")
            fixture.service.switch(account, checked).refusedWith(SpaceMigrationErrorCodes.LEGACY_CHANGED)

            val rechecked = fixture.proof(account, "move-1")
            fixture.putSpaceRecord(account, "sealed-two")
            fixture.service.switch(account, rechecked).refusedWith(SpaceMigrationErrorCodes.SPACE_CHANGED)
            fixture.service
                .switch(
                    account,
                    rechecked.copy(spaceDigest = "0".repeat(64)),
                ).refusedWith(SpaceMigrationErrorCodes.SPACE_CHANGED)
            fixture.service
                .switch(account, fixture.proof(account, "move-1").copy(legacyCounts = emptyMap()))
                .refusedWith(SpaceMigrationErrorCodes.LEGACY_CHANGED)

            assertEquals(AccountStorageMode.LEGACY, fixture.mode(account))
            fixture.service.switch(account, fixture.proof(account, "move-1")).done()
        }

    @Test
    fun `only one device moves an account until the other one goes quiet`() =
        runTest {
            val fixture = MigrationFixture()
            val account = fixture.legacyAccount()
            fixture.service.begin(account, "phone").done()

            fixture.service.begin(account, "laptop").refusedWith(SpaceMigrationErrorCodes.HELD_ELSEWHERE)
            fixture.service.begin(account, "phone").done()
            fixture.service.hold(account, "laptop").refusedWith(SpaceMigrationErrorCodes.NOT_ACTIVE)

            fixture.clock.advance(fixture.config.uploadLease)
            assertEquals(
                "laptop",
                fixture.service
                    .begin(account, "laptop")
                    .done()
                    .migrationId,
            )
            fixture.service.hold(account, "phone").refusedWith(SpaceMigrationErrorCodes.NOT_ACTIVE)
        }

    @Test
    fun `an account already in its personal space can't begin a move`() =
        runTest {
            val fixture = MigrationFixture()
            val account = fixture.legacyAccount()
            fixture.moveWithoutMigration(account)

            fixture.service.begin(account, "move-1").refusedWith(SpaceMigrationErrorCodes.WRONG_STORAGE_MODE)
            fixture.service.begin(fixture.legacyAccount(did = null), "move-1").refusedWith(SpaceMigrationErrorCodes.NO_IDENTITY)
            fixture.service.begin(fixture.legacyAccount(), "not an id").refusedWith(SpaceMigrationErrorCodes.NOT_ACTIVE)
        }

    @Test
    fun `switching back needs a hold and a download of everything written`() =
        runTest {
            val fixture = MigrationFixture()
            val account = fixture.switchedAccount()
            val seq = assertNotNull(fixture.service.status(account).switchedAtSeq)

            fixture.service
                .switchBack(account, SpaceMigrationSwitchBackRequest("move-1", seq))
                .refusedWith(SpaceMigrationErrorCodes.HOLD_NOT_READY)
            fixture.service.hold(account, "move-1").done()
            assertNotNull(fixture.holds.spaceWriteRefusal(account, AccountStorageMode.PERSONAL_SPACE_V1))
            fixture.clock.advance(fixture.config.holdGrace)
            fixture.putSpaceRecord(account, "written-after-the-switch")
            fixture.service
                .switchBack(account, SpaceMigrationSwitchBackRequest("move-1", seq))
                .refusedWith(SpaceMigrationErrorCodes.SPACE_CHANGED)

            val reverted =
                fixture.service.switchBack(account, SpaceMigrationSwitchBackRequest("move-1", seq + 1)).done()

            assertEquals(SpaceMigrationPhase.REVERTED, reverted.phase)
            assertEquals(AccountStorageMode.LEGACY, fixture.mode(account))
            assertEquals(SpaceWriteRefusal.StorageChanged, fixture.holds.spaceWriteRefusal(account, AccountStorageMode.LEGACY))
            assertFalse(fixture.holds.movedByMigration(account))
            assertEquals(
                "move-2",
                fixture.service
                    .begin(account, "move-2")
                    .done()
                    .migrationId,
                "A switched-back journal can move again",
            )
        }

    @Test
    fun `switching back stops working once the legacy copy is no longer kept`() =
        runTest {
            val fixture = MigrationFixture()
            val account = fixture.switchedAccount()
            fixture.clock.advance(30.days)
            fixture.service.hold(account, "move-1").done()
            fixture.clock.advance(fixture.config.holdGrace)

            fixture.service
                .switchBack(account, SpaceMigrationSwitchBackRequest("move-1", 1))
                .refusedWith(SpaceMigrationErrorCodes.WINDOW_CLOSED)
            assertEquals(AccountStorageMode.PERSONAL_SPACE_V1, fixture.mode(account))
        }

    @Test
    fun `a switch from a stale copy of the move changes nothing`() =
        runTest {
            val fixture = MigrationFixture()
            val account = fixture.legacyAccount()
            fixture.service.begin(account, "move-1").done()
            val stale = assertNotNull(fixture.store.get(account))
            fixture.service.hold(account, "move-1").done()
            val switched = stale.next(fixture.clock.now()) { copy(phase = SpaceMigrationPhase.SWITCHED) }

            assertFalse(fixture.store.replaceAndSwitch(stale, switched, AccountStorageMode.LEGACY, AccountStorageMode.PERSONAL_SPACE_V1))
            assertEquals(AccountStorageMode.LEGACY, fixture.mode(account))
            assertEquals(SpaceMigrationPhase.UPLOADING, fixture.store.get(account)?.phase)
        }
}

/** An account a device has moved into its personal space with move `move-1`. */
internal suspend fun MigrationFixture.switchedAccount(account: java.util.UUID? = null): java.util.UUID {
    val account = account ?: legacyAccount()
    addLegacyEntry(account, "entry-one")
    putSpaceRecord(account, "sealed-one")
    service.begin(account, "move-1").done()
    service.hold(account, "move-1").done()
    clock.advance(config.holdGrace)
    service.switch(account, proof(account, "move-1")).done()
    return account
}

internal fun <T> SpaceMigrationResult<T>.done(): T = assertIs<SpaceMigrationResult.Done<T>>(this, "Refused: $this").value

internal fun SpaceMigrationResult<SpaceMigrationStatus>.refusedWith(code: String) {
    assertEquals(code, assertIs<SpaceMigrationResult.Refused>(this, "Expected a refusal, got $this").code)
}
