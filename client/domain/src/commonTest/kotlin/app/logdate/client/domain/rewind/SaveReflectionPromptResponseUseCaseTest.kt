package app.logdate.client.domain.rewind

import app.logdate.client.repository.rewind.ReflectionPromptResponseRepository
import app.logdate.shared.model.ReflectionPrompt
import app.logdate.shared.model.ReflectionPromptKey
import app.logdate.shared.model.ReflectionPromptResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/**
 * Unit tests for [SaveReflectionPromptResponseUseCase].
 *
 * Verifies the processing of user responses to reflection prompts, ensuring
 * that meaningful content is saved while empty or whitespace-only responses
 * result in the deletion of existing records.
 */
class SaveReflectionPromptResponseUseCaseTest {
    private lateinit var fakeRepository: FakeReflectionPromptResponseRepository
    private lateinit var useCase: SaveReflectionPromptResponseUseCase

    @BeforeTest
    fun setUp() {
        fakeRepository = FakeReflectionPromptResponseRepository()
        useCase = SaveReflectionPromptResponseUseCase(repository = fakeRepository)
    }

    @Test
    fun `non-blank text saves through repository`() =
        runTest {
            val rewindId = Uuid.random()
            val prompt =
                ReflectionPrompt(
                    observation = "Sarah came up four times this week.",
                    invitation = "What's the line you almost wrote about her but didn't?",
                )

            useCase(rewindId, prompt, "I think I'm afraid to admit it mattered.")

            assertEquals(1, fakeRepository.saveCalls.size)
            val (savedRewindId, savedPrompt, savedText) = fakeRepository.saveCalls.single()
            assertEquals(rewindId, savedRewindId)
            assertEquals(prompt, savedPrompt)
            assertEquals("I think I'm afraid to admit it mattered.", savedText)
            assertEquals(0, fakeRepository.deleteCalls.size)
        }

    @Test
    fun `whitespace gets trimmed before save`() =
        runTest {
            val rewindId = Uuid.random()
            val prompt = ReflectionPrompt(observation = "obs", invitation = "inv")

            useCase(rewindId, prompt, "   actual reply   ")

            val saved = fakeRepository.saveCalls.single()
            assertEquals("actual reply", saved.third)
        }

    @Test
    fun `empty input routes through delete instead of save`() =
        runTest {
            val rewindId = Uuid.random()
            val prompt = ReflectionPrompt(observation = "obs", invitation = "inv")

            useCase(rewindId, prompt, "")

            assertEquals(0, fakeRepository.saveCalls.size)
            assertEquals(1, fakeRepository.deleteCalls.size)
            val (deletedRewindId, deletedPrompt) = fakeRepository.deleteCalls.single()
            assertEquals(rewindId, deletedRewindId)
            assertEquals(prompt, deletedPrompt)
        }

    @Test
    fun `whitespace-only input also routes through delete`() =
        runTest {
            val rewindId = Uuid.random()
            val prompt = ReflectionPrompt(observation = "obs", invitation = "inv")

            useCase(rewindId, prompt, "   \n\t  ")

            assertEquals(0, fakeRepository.saveCalls.size)
            assertEquals(1, fakeRepository.deleteCalls.size)
        }
}

private class FakeReflectionPromptResponseRepository : ReflectionPromptResponseRepository {
    val saveCalls = mutableListOf<Triple<Uuid, ReflectionPrompt, String>>()
    val deleteCalls = mutableListOf<Pair<Uuid, ReflectionPrompt>>()

    override fun observe(rewindId: Uuid): Flow<Map<ReflectionPromptKey, ReflectionPromptResponse>> = flowOf(emptyMap())

    override suspend fun save(
        rewindId: Uuid,
        prompt: ReflectionPrompt,
        responseText: String,
    ) {
        saveCalls += Triple(rewindId, prompt, responseText)
    }

    override suspend fun delete(
        rewindId: Uuid,
        prompt: ReflectionPrompt,
    ) {
        deleteCalls += rewindId to prompt
    }
}
