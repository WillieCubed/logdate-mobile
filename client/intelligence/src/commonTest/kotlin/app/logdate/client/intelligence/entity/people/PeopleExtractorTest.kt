package app.logdate.client.intelligence.entity.people

import app.logdate.client.intelligence.AIResult
import app.logdate.client.intelligence.fakes.FakeGenerativeAICache
import app.logdate.client.intelligence.fakes.FakeGenerativeAIChatClient
import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.shared.model.Person
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PeopleExtractorTest {
    
    private val testDispatcher = StandardTestDispatcher()
    private val fakeCache = FakeGenerativeAICache()
    private val fakeAIClient = FakeGenerativeAIChatClient()
    private val fakeNetworkMonitor = TestNetworkAvailabilityMonitor()
    
    private val peopleExtractor = PeopleExtractor(
        generativeAICache = fakeCache,
        generativeAIChatClient = fakeAIClient,
        networkAvailabilityMonitor = fakeNetworkMonitor,
        ioDispatcher = testDispatcher
    )
    
    private fun setup() {
        fakeCache.clear()
        fakeAIClient.clear()
    }

    @Test
    fun extractPeople_withSinglePerson_extractsCorrectly() = runTest(testDispatcher) {
        setup()
        val documentId = "doc-single-person"
        val inputText = "I had lunch with Sarah today. She was telling me about her new job."
        val aiResponse = "Sarah"
        
        fakeAIClient.setResponseFor(inputText, aiResponse)
        
        val result = peopleExtractor.extractPeople(documentId, inputText, useCached = false)
        val people = assertSuccess(result)
        
        assertEquals(1, people.size)
        assertEquals("Sarah", people[0].name)
        
        // Verify AI client was called with correct prompt
        val lastSubmission = fakeAIClient.getLastSubmission()
        assertNotNull(lastSubmission)
        assertEquals(2, lastSubmission.size)
        
        val systemMessage = lastSubmission.find { it.role == "system" }
        assertNotNull(systemMessage)
        assertTrue(systemMessage.content.contains("extracts the names"))
        assertTrue(systemMessage.content.contains("humans mentioned"))
        assertTrue(systemMessage.content.contains("line"))
        
        val userMessage = lastSubmission.find { it.role == "user" }
        assertEquals(inputText, userMessage?.content)
    }

    @Test
    fun extractPeople_withMultiplePeople_extractsAll() = runTest(testDispatcher) {
        setup()
        val documentId = "doc-multiple-people"
        val inputText = "Today I met with John, Sarah, and Michael to discuss the project. Emily couldn't make it."
        val aiResponse = "John\nSarah\nMichael\nEmily"
        
        fakeAIClient.setResponseFor(inputText, aiResponse)
        
        val result = peopleExtractor.extractPeople(documentId, inputText, useCached = false)
        val people = assertSuccess(result)
        
        assertEquals(4, people.size)
        val names = people.map { it.name }
        assertTrue(names.contains("John"))
        assertTrue(names.contains("Sarah"))
        assertTrue(names.contains("Michael"))
        assertTrue(names.contains("Emily"))
    }

    @Test
    fun extractPeople_withCachedResponse_returnsCachedPeople() = runTest(testDispatcher) {
        setup()
        val documentId = "doc-cached"
        val inputText = "I talked to Bob yesterday."
        val cachedResponse = "Bob"
        
        // Pre-populate cache
        fakeCache.setEntry(documentId, cachedResponse)
        
        val result = peopleExtractor.extractPeople(documentId, inputText, useCached = true)
        val people = assertSuccess(result)
        
        assertEquals(1, people.size)
        assertEquals("Bob", people[0].name)
        
        // Verify cache was checked but AI client was not called
        assertTrue(fakeCache.getEntryCalls.contains(documentId))
        assertEquals(0, fakeAIClient.submissions.size)
    }

    @Test
    fun extractPeople_withCachedDisabled_skipsCache() = runTest(testDispatcher) {
        setup()
        val documentId = "doc-skip-cache"
        val inputText = "Alice and I went shopping."
        val cachedResponse = "Bob"
        val newResponse = "Alice"
        
        // Pre-populate cache with different data
        fakeCache.setEntry(documentId, cachedResponse)
        fakeAIClient.setResponseFor(inputText, newResponse)
        
        val result = peopleExtractor.extractPeople(documentId, inputText, useCached = false)
        val people = assertSuccess(result)
        
        assertEquals(1, people.size)
        assertEquals("Alice", people[0].name)
        
        // Verify cache was not checked and AI client was called
        assertEquals(0, fakeCache.getEntryCalls.size)
        assertEquals(1, fakeAIClient.submissions.size)
    }

    @Test
    fun extractPeople_withNoNames_returnsEmptyList() = runTest(testDispatcher) {
        setup()
        val documentId = "doc-no-names"
        val inputText = "I went to the store and bought some groceries. The weather was nice."
        
        // When AI returns empty string, we get one empty Person name due to split behavior
        fakeAIClient.setResponseFor(inputText, "")
        
        val result = peopleExtractor.extractPeople(documentId, inputText, useCached = false)
        val people = assertSuccess(result)
        
        assertEquals(0, people.size)
    }

    @Test
    fun extractPeople_withNullAIResponse_returnsEmptyList() = runTest(testDispatcher) {
        setup()
        val documentId = "doc-null-response"
        val inputText = "Some text"
        
        fakeAIClient.defaultResponse = null
        
        val result = peopleExtractor.extractPeople(documentId, inputText, useCached = false)
        assertTrue(result is AIResult.Error)
        assertEquals(1, fakeAIClient.submissions.size)
    }

    @Test
    fun extractPeople_withWhitespaceInNames_trimsCorrectly() = runTest(testDispatcher) {
        setup()
        val documentId = "doc-whitespace"
        val inputText = "I saw David and Lisa at the park."
        val aiResponse = "  David  \n\n  Lisa  \n"
        
        fakeAIClient.setResponseFor(inputText, aiResponse)
        
        val result = peopleExtractor.extractPeople(documentId, inputText, useCached = false)
        val people = assertSuccess(result)
        
        assertEquals(2, people.size)
        assertEquals("David", people[0].name)
        assertEquals("Lisa", people[1].name)
    }

    @Test
    fun extractPeople_whenOffline_returnsUnavailable() = runTest(testDispatcher) {
        setup()
        fakeNetworkMonitor.setAvailable(false)
        val documentId = "doc-offline"
        val inputText = "I met Alice"

        val result = peopleExtractor.extractPeople(documentId, inputText, useCached = false)

        assertTrue(result is AIResult.Unavailable)
        assertEquals(0, fakeAIClient.submissions.size)
    }

    @Test
    fun extractPeople_withComplexNames_extractsCorrectly() = runTest(testDispatcher) {
        setup()
        val documentId = "doc-complex-names"
        val inputText = "I met Dr. Sarah Johnson and Mr. John Smith at the conference. Mary-Elizabeth was also there."
        val aiResponse = "Dr. Sarah Johnson\nMr. John Smith\nMary-Elizabeth"
        
        fakeAIClient.setResponseFor(inputText, aiResponse)
        
        val result = peopleExtractor.extractPeople(documentId, inputText, useCached = false)
        val people = assertSuccess(result)
        
        assertEquals(3, people.size)
        assertEquals("Dr. Sarah Johnson", people[0].name)
        assertEquals("Mr. John Smith", people[1].name)
        assertEquals("Mary-Elizabeth", people[2].name)
    }

    @Test
    fun extractPeople_withFirstPersonReferences_handlesCorrectly() = runTest(testDispatcher) {
        setup()
        val documentId = "doc-first-person"
        val inputText = "I talked to myself and then called Mom. Dad was busy so he couldn't chat."
        val aiResponse = "Mom\nDad"
        
        fakeAIClient.setResponseFor(inputText, aiResponse)
        
        val result = peopleExtractor.extractPeople(documentId, inputText, useCached = false)
        val people = assertSuccess(result)
        
        assertEquals(2, people.size)
        assertEquals("Mom", people[0].name)
        assertEquals("Dad", people[1].name)
    }

    @Test
    fun extractPeople_withEmptyInput_handlesGracefully() = runTest(testDispatcher) {
        setup()
        val documentId = "doc-empty"
        val inputText = ""
        
        fakeAIClient.setResponseFor(inputText, "")
        
        val result = peopleExtractor.extractPeople(documentId, inputText, useCached = false)
        val people = assertSuccess(result)
        
        assertEquals(0, people.size)
        
        val userMessage = fakeAIClient.getLastUserMessage()
        assertEquals("", userMessage)
    }

    @Test
    fun extractPeople_cachesResultsCorrectly() = runTest(testDispatcher) {
        setup()
        val documentId = "doc-caching"
        val inputText = "I visited Tom and Jerry today."
        val aiResponse = "Tom\nJerry"
        
        fakeAIClient.setResponseFor(inputText, aiResponse)
        
        val result = peopleExtractor.extractPeople(documentId, inputText, useCached = false)
        val people = assertSuccess(result)
        
        assertEquals(2, people.size)
        
        // Verify result was cached
        assertTrue(fakeCache.putEntryCalls.any { 
            it.first == documentId && it.second == aiResponse.trim() 
        })
    }

    @Test
    fun extractPeople_withMixedCaseAndPunctuation_extractsCorrectly() = runTest(testDispatcher) {
        setup()
        val documentId = "doc-mixed"
        val inputText = "SARAH, bob, and Dr. WILSON were discussing the meeting with ms. jane."
        val aiResponse = "SARAH\nbob\nDr. WILSON\nms. jane"
        
        fakeAIClient.setResponseFor(inputText, aiResponse)
        
        val result = peopleExtractor.extractPeople(documentId, inputText, useCached = false)
        val people = assertSuccess(result)
        
        assertEquals(4, people.size)
        assertEquals("SARAH", people[0].name)
        assertEquals("bob", people[1].name)
        assertEquals("Dr. WILSON", people[2].name)
        assertEquals("ms. jane", people[3].name)
    }

    @Test
    fun extractPeople_withLongText_processesCorrectly() = runTest(testDispatcher) {
        setup()
        val documentId = "doc-long"
        val inputText = """
            Today was the annual company retreat. I arrived early and was greeted by Jennifer 
            from HR. The keynote speaker was Mark Thompson, who gave an inspiring talk about 
            innovation. During lunch, I sat with Amanda, Carlos, and Priya. We discussed the 
            upcoming project deadlines. In the afternoon breakout session, I worked with 
            Rebecca and Tom on the marketing strategy. The day ended with drinks, where I 
            caught up with old colleague David Kim who now works in a different department.
        """.trimIndent()
        val aiResponse = "Jennifer\nMark Thompson\nAmanda\nCarlos\nPriya\nRebecca\nTom\nDavid Kim"
        
        fakeAIClient.setResponseFor(inputText, aiResponse)
        
        val result = peopleExtractor.extractPeople(documentId, inputText, useCached = false)
        val people = assertSuccess(result)
        
        assertEquals(8, people.size)
        val names = people.map { it.name }
        assertTrue(names.contains("Jennifer"))
        assertTrue(names.contains("Mark Thompson"))
        assertTrue(names.contains("Amanda"))
        assertTrue(names.contains("Carlos"))
        assertTrue(names.contains("Priya"))
        assertTrue(names.contains("Rebecca"))
        assertTrue(names.contains("Tom"))
        assertTrue(names.contains("David Kim"))
    }

    @Test
    fun extractPeople_systemPromptIsCorrect() = runTest(testDispatcher) {
        setup()
        val documentId = "doc-prompt-test"
        val inputText = "Test text with Alex"
        
        fakeAIClient.defaultResponse = "Alex"
        
        peopleExtractor.extractPeople(documentId, inputText, useCached = false)
        
        val systemMessage = fakeAIClient.getLastSystemMessage()
        assertNotNull(systemMessage)
        
        // Verify key requirements in system prompt
        assertTrue(systemMessage.contains("extracts the names"))
        assertTrue(systemMessage.contains("humans mentioned"))
        assertTrue(systemMessage.contains("literally return"))
        assertTrue(systemMessage.contains("line"))
    }

    private fun assertSuccess(result: AIResult<List<Person>>): List<Person> {
        assertTrue(result is AIResult.Success)
        return result.value
    }

    private class TestNetworkAvailabilityMonitor(
        private var available: Boolean = true
    ) : NetworkAvailabilityMonitor {
        override fun isNetworkAvailable(): Boolean = available

        override fun observeNetwork() = throw UnsupportedOperationException("Not used in tests")

        fun setAvailable(isAvailable: Boolean) {
            available = isAvailable
        }
    }
}
