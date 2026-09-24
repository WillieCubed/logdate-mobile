package app.logdate.feature.core.settings.account.move

import app.logdate.client.data.account.ServerScopedAccount
import app.logdate.client.datastore.UserSession
import app.logdate.client.networking.PasskeyApiErrorCodes
import app.logdate.client.networking.PasskeyApiException
import app.logdate.client.permissions.PasskeyErrorCodes
import app.logdate.client.permissions.PasskeyException
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.feature.core.settings.ui.CheckedServer
import app.logdate.feature.core.settings.ui.ServerCheck
import app.logdate.feature.core.settings.ui.ServerProblem
import app.logdate.shared.model.DeploymentKind
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.ServerDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MoveServerViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val serverA = "https://cloud.logdate.app"
    private val serverB = "https://journal.example.com"
    private val destination = CheckedServer(serverB, descriptor(serverB), "1.4.0")
    private val survey = MoveSurvey(entries = 12, journals = 2, media = 3, drafts = 1, remoteOnlyMedia = 0)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a usable server leads to a review of what moves`() =
        runTest {
            val viewModel = viewModel()

            viewModel.setAddress("journal.example.com")
            viewModel.checkServer()

            val review = assertIs<MoveServerUiState.Review>(viewModel.state.value)
            assertEquals(serverB, review.destination.origin)
            assertEquals(survey, review.survey)
        }

    @Test
    fun `a server that cannot hold the account says why and stays on the address`() =
        runTest {
            val viewModel = viewModel(check = { ServerCheck.Unusable(ServerProblem.OUT_OF_DATE) })

            viewModel.setAddress("old.example.com")
            viewModel.checkServer()

            val choose = assertIs<MoveServerUiState.ChooseServer>(viewModel.state.value)
            assertEquals(ChooseProblem.Server(ServerProblem.OUT_OF_DATE), choose.problem)
            assertEquals("old.example.com", choose.address)
        }

    @Test
    fun `the server already in use is not offered as a destination`() =
        runTest {
            val viewModel = viewModel(check = { ServerCheck.Usable(CheckedServer(serverA, descriptor(serverA), null)) })

            viewModel.setAddress(serverA)
            viewModel.checkServer()

            assertEquals(ChooseProblem.SameAsCurrent, assertIs<MoveServerUiState.ChooseServer>(viewModel.state.value).problem)
        }

    @Test
    fun `without a sign-in there an account is created under the current username`() =
        runTest {
            val move = FakeMove()
            val viewModel = viewModel(move = move)
            reachReview(viewModel)

            viewModel.continueToAccount()

            val create = assertIs<MoveServerUiState.CreateAccount>(viewModel.state.value)
            assertEquals("alice", create.username)

            viewModel.createAccount("alice")

            assertEquals(listOf("alice"), move.createdUsernames)
            assertIs<MoveServerUiState.Uploading>(viewModel.state.value)
        }

    @Test
    fun `an earlier sign-in on the new server is reused without a passkey prompt`() =
        runTest {
            val move = FakeMove(hasWorkingSignIn = true)
            val viewModel = viewModel(move = move)
            reachReview(viewModel)

            viewModel.continueToAccount()

            assertTrue(move.createdUsernames.isEmpty())
            assertIs<MoveServerUiState.Uploading>(viewModel.state.value)
        }

    @Test
    fun `a taken username asks for another`() =
        runTest {
            val move = FakeMove(createResult = Result.failure(PasskeyApiException(PasskeyApiErrorCodes.USERNAME_TAKEN, "Taken")))
            val viewModel = viewModel(move = move)
            reachReview(viewModel)
            viewModel.continueToAccount()

            viewModel.createAccount("alice")

            assertEquals(AccountProblem.USERNAME_TAKEN, assertIs<MoveServerUiState.CreateAccount>(viewModel.state.value).problem)
        }

    @Test
    fun `an account this device already has there signs in instead`() =
        runTest {
            val move =
                FakeMove(
                    createResult = Result.failure(PasskeyApiException(PasskeyApiErrorCodes.CANONICAL_OWNER_ID_TAKEN, "Exists")),
                )
            val viewModel = viewModel(move = move)
            reachReview(viewModel)
            viewModel.continueToAccount()

            viewModel.createAccount("alice")

            assertEquals(1, move.signIns)
            assertIs<MoveServerUiState.Uploading>(viewModel.state.value)
        }

    @Test
    fun `cancelling the passkey prompt stays put without an error`() =
        runTest {
            val move = FakeMove(createResult = Result.failure(PasskeyException("Cancelled", PasskeyErrorCodes.USER_CANCELLED)))
            val viewModel = viewModel(move = move)
            reachReview(viewModel)
            viewModel.continueToAccount()

            viewModel.createAccount("alice")

            val create = assertIs<MoveServerUiState.CreateAccount>(viewModel.state.value)
            assertEquals(null, create.problem)
        }

    @Test
    fun `a failed switch returns to the review and says so`() =
        runTest {
            val move = FakeMove(hasWorkingSignIn = true, commitResult = Result.failure(IllegalStateException("Timed out")))
            val viewModel = viewModel(move = move)
            reachReview(viewModel)

            viewModel.continueToAccount()

            assertEquals(MoveProblem.SWITCH_FAILED, assertIs<MoveServerUiState.Review>(viewModel.state.value).problem)
        }

    @Test
    fun `once everything is uploaded the old account can be deleted`() =
        runTest {
            val move = FakeMove(hasWorkingSignIn = true)
            val viewModel = viewModel(move = move)
            reachReview(viewModel)
            viewModel.continueToAccount()

            move.progress.value = MoveProgress(remaining = 5, failed = 0, isSyncing = true, syncedSinceSwitch = false)
            move.progress.value = MoveProgress(remaining = 0, failed = 0, isSyncing = false, syncedSinceSwitch = true)

            assertEquals(Cleanup.Offered, assertIs<MoveServerUiState.Finished>(viewModel.state.value).cleanup)

            viewModel.deleteSource()

            assertEquals(MoveServerUiState.Closed, viewModel.state.value)
            assertEquals(1, move.sourceDeletions)
        }

    @Test
    fun `photos that live only on the old server block deleting it`() =
        runTest {
            val move = FakeMove(hasWorkingSignIn = true)
            val viewModel = viewModel(move = move, survey = survey.copy(remoteOnlyMedia = 2))
            reachReview(viewModel)
            viewModel.continueToAccount()

            move.progress.value = MoveProgress(remaining = 0, failed = 0, isSyncing = false, syncedSinceSwitch = true)

            assertEquals(
                Cleanup.Blocked(CleanupBlock.MEDIA_ONLY_ON_OLD_SERVER),
                assertIs<MoveServerUiState.Finished>(viewModel.state.value).cleanup,
            )
        }

    @Test
    fun `uploads the new server refused block deleting the old one`() =
        runTest {
            val move = FakeMove(hasWorkingSignIn = true)
            val viewModel = viewModel(move = move)
            reachReview(viewModel)
            viewModel.continueToAccount()

            move.progress.value = MoveProgress(remaining = 0, failed = 3, isSyncing = false, syncedSinceSwitch = true)

            assertEquals(Cleanup.Blocked(CleanupBlock.UPLOADS_FAILED), assertIs<MoveServerUiState.Finished>(viewModel.state.value).cleanup)
        }

    @Test
    fun `a move interrupted mid-switch picks up where it stopped`() =
        runTest {
            val record = record(ServerMoveRecord.Phase.SWITCHING)
            val move = FakeMove(inProgress = record)

            val viewModel = viewModel(move = move)

            assertEquals(listOf(record), move.resumed)
            assertIs<MoveServerUiState.Uploading>(viewModel.state.value)
        }

    @Test
    fun `opening the screen while a move is being picked up resumes it once`() =
        runTest {
            val record = record(ServerMoveRecord.Phase.SWITCHING)
            val gate = CompletableDeferred<Unit>()
            val move = FakeMove(inProgress = record, resumeGate = gate)
            val viewModel = viewModel(move = move)

            viewModel.start()
            gate.complete(Unit)

            assertEquals(listOf(record), move.resumed)
            assertIs<MoveServerUiState.Uploading>(viewModel.state.value)
        }

    @Test
    fun `keeping the old account ends the move`() =
        runTest {
            val move = FakeMove(inProgress = record(ServerMoveRecord.Phase.UPLOADING))
            val viewModel = viewModel(move = move)
            move.progress.value = MoveProgress(remaining = 0, failed = 0, isSyncing = false, syncedSinceSwitch = true)

            viewModel.keepSource()

            assertEquals(MoveServerUiState.Closed, viewModel.state.value)
            assertEquals(1, move.finished)
        }

    private suspend fun reachReview(viewModel: MoveServerViewModel) {
        viewModel.setAddress("journal.example.com")
        viewModel.checkServer()
    }

    private fun viewModel(
        move: FakeMove = FakeMove(),
        survey: MoveSurvey = this.survey,
        check: suspend (String) -> ServerCheck = { ServerCheck.Usable(destination) },
    ): MoveServerViewModel {
        move.surveyResult = survey
        return MoveServerViewModel(
            move = move,
            checkServer = check,
            currentAccount = { LogDateAccount(username = "alice", displayName = "Alice Chen") },
        )
    }

    private fun record(phase: ServerMoveRecord.Phase) =
        ServerMoveRecord(
            from = MoveEndpoint(serverA, descriptor(serverA)),
            to = MoveEndpoint(serverB, descriptor(serverB)),
            phase = phase,
        )

    private fun descriptor(origin: String) =
        ServerDescriptor(
            serverOrigin = origin,
            apiBaseUrl = "$origin/api/v1",
            deploymentKind = DeploymentKind.SELF_HOSTED,
            displayName = origin.substringAfter("://"),
        )

    private inner class FakeMove(
        private val hasWorkingSignIn: Boolean = false,
        private val createResult: Result<Unit> = Result.success(Unit),
        private val commitResult: Result<ServerMoveRecord>? = null,
        private val inProgress: ServerMoveRecord? = null,
        private val resumeGate: CompletableDeferred<Unit>? = null,
    ) : ServerMove {
        var surveyResult = survey
        val createdUsernames = mutableListOf<String>()
        var signIns = 0
        var sourceDeletions = 0
        var finished = 0
        val resumed = mutableListOf<ServerMoveRecord>()
        val progress = MutableStateFlow(MoveProgress(remaining = 0, failed = 0, isSyncing = true, syncedSinceSwitch = false))

        private val account =
            object : ServerScopedAccount {
                override val origin: String = serverB
                override val repository: PasskeyAccountRepository get() = error("unused")

                override fun session(): UserSession? = null

                override fun close() = Unit
            }

        override fun source(): MoveEndpoint = MoveEndpoint(serverA, descriptor(serverA))

        override suspend fun survey(): MoveSurvey = surveyResult

        override suspend fun inProgress(): ServerMoveRecord? = inProgress

        override suspend fun openDestination(server: CheckedServer): ServerScopedAccount = account

        override suspend fun hasWorkingSignIn(account: ServerScopedAccount): Boolean = hasWorkingSignIn

        override suspend fun createAccount(
            account: ServerScopedAccount,
            username: String,
            displayName: String,
        ): Result<Unit> {
            createdUsernames += username
            return createResult
        }

        override suspend fun signIn(account: ServerScopedAccount): Result<Unit> {
            signIns++
            return Result.success(Unit)
        }

        override suspend fun commit(
            destination: CheckedServer,
            account: ServerScopedAccount,
            survey: MoveSurvey,
        ): Result<ServerMoveRecord> =
            commitResult ?: Result.success(record(ServerMoveRecord.Phase.UPLOADING).copy(remoteOnlyMedia = survey.remoteOnlyMedia))

        override suspend fun resume(record: ServerMoveRecord): Result<ServerMoveRecord> {
            resumed += record
            resumeGate?.await()
            return Result.success(record.copy(phase = ServerMoveRecord.Phase.UPLOADING))
        }

        override fun progress(record: ServerMoveRecord): Flow<MoveProgress> = progress

        override suspend fun deleteSource(record: ServerMoveRecord): SourceDeletion {
            sourceDeletions++
            return SourceDeletion.DELETED
        }

        override suspend fun signInToSourceAndDelete(record: ServerMoveRecord): SourceDeletion = SourceDeletion.DELETED

        override suspend fun finish() {
            finished++
        }
    }
}
