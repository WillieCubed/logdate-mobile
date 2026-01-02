package app.logdate.client.intelligence.narrative

import app.logdate.client.intelligence.fakes.FakeGenerativeAICache
import app.logdate.client.intelligence.fakes.FakeGenerativeAIChatClient
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.media.IndexedMedia
import app.logdate.shared.model.Person
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class WeekNarrativeSynthesizerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeCache = FakeGenerativeAICache()
    private val fakeAIClient = FakeGenerativeAIChatClient()

    private val synthesizer = WeekNarrativeSynthesizer(
        generativeAICache = fakeCache,
        genAIClient = fakeAIClient,
        ioDispatcher = testDispatcher
    )

    private fun setup() {
        fakeCache.clear()
        fakeAIClient.clear()
    }

    @Test
    fun synthesize_withVacationWeek_generatesCorrectNarrative() = runTest(testDispatcher) {
        setup()
        val weekId = "2024-W30"

        val textEntries = listOf(
            JournalNote.Text(
                uid = Uuid.random(),
                creationTimestamp = Instant.parse("2024-07-22T10:00:00Z"),
                lastUpdated = Instant.parse("2024-07-22T10:00:00Z"),
                content = "Finally found the hidden beach at Point Reyes! It was everything I hoped for."
            ),
            JournalNote.Text(
                uid = Uuid.random(),
                creationTimestamp = Instant.parse("2024-07-23T19:00:00Z"),
                lastUpdated = Instant.parse("2024-07-23T19:00:00Z"),
                content = "Taco truck tour night 1. This might become a tradition."
            )
        )

        val media = listOf(
            IndexedMedia.Image(
                uid = Uuid.random(),
                uri = "file:///beach-sunset.jpg",
                timestamp = Instant.parse("2024-07-22T18:30:00Z"),
                caption = "Sunset at the hidden beach"
            )
        )

        val vacationNarrativeResponse = """
        {
          "themes": ["vacation", "exploration", "culinary adventure"],
          "emotionalTone": "excited and refreshed",
          "storyBeats": [
            {
              "moment": "Finally found the hidden beach at Point Reyes",
              "context": "Long-anticipated goal you'd been searching for",
              "emotionalWeight": "triumphant",
              "evidenceIds": ["${textEntries[0].uid}"]
            },
            {
              "moment": "Taco truck tour became the evening ritual",
              "context": "Spontaneous tradition that defined the trip",
              "emotionalWeight": "joyful",
              "evidenceIds": ["${textEntries[1].uid}"]
            }
          ],
          "overallNarrative": "You explored the California coast, finally finding that hidden beach you'd been searching for. Evenings were dedicated to the taco truck tour you'd been planning."
        }
        """.trimIndent()

        fakeAIClient.defaultResponse = vacationNarrativeResponse

        val result = synthesizer.synthesize(
            weekId = weekId,
            textEntries = textEntries,
            media = media,
            people = emptyList(),
            useCached = false
        )

        assertNotNull(result)
        assertEquals(3, result.themes.size)
        assertTrue(result.themes.contains("vacation"))
        assertTrue(result.themes.contains("exploration"))
        assertTrue(result.themes.contains("culinary adventure"))
        assertEquals("excited and refreshed", result.emotionalTone)
        assertEquals(2, result.storyBeats.size)
        assertEquals("Finally found the hidden beach at Point Reyes", result.storyBeats[0].moment)
        assertEquals("triumphant", result.storyBeats[0].emotionalWeight)
    }

    @Test
    fun synthesize_withToughWeek_generatesCorrectNarrative() = runTest(testDispatcher) {
        setup()
        val weekId = "2024-W31"

        val textEntries = listOf(
            JournalNote.Text(
                uid = Uuid.random(),
                creationTimestamp = Instant.parse("2024-07-29T22:00:00Z"),
                lastUpdated = Instant.parse("2024-07-29T22:00:00Z"),
                content = "Sarah being out of town is harder than I expected. Missing her a lot."
            ),
            JournalNote.Text(
                uid = Uuid.random(),
                creationTimestamp = Instant.parse("2024-07-30T23:30:00Z"),
                lastUpdated = Instant.parse("2024-07-30T23:30:00Z"),
                content = "Another late night at the office. This deadline is killing me."
            ),
            JournalNote.Text(
                uid = Uuid.random(),
                creationTimestamp = Instant.parse("2024-07-31T01:00:00Z"),
                lastUpdated = Instant.parse("2024-07-31T01:00:00Z"),
                content = "WE DID IT! Raid team finally cleared Mythic! Gaming friends are the best."
            )
        )

        val people = listOf(Person(name = "Sarah"))

        val toughWeekResponse = """
        {
          "themes": ["relationship strain", "work pressure", "gaming as therapy"],
          "emotionalTone": "stressful but resilient",
          "storyBeats": [
            {
              "moment": "Sarah being out of town hit harder than expected",
              "context": "Unexpected loneliness highlighting relationship importance",
              "emotionalWeight": "melancholy",
              "evidenceIds": ["${textEntries[0].uid}"]
            },
            {
              "moment": "Project deadline had you working late every night",
              "context": "Work stress compounding personal challenges",
              "emotionalWeight": "exhausted",
              "evidenceIds": ["${textEntries[1].uid}"]
            },
            {
              "moment": "Raid team finally cleared Mythic",
              "context": "Gaming friends providing support and victory during tough time",
              "emotionalWeight": "grateful",
              "evidenceIds": ["${textEntries[2].uid}"]
            }
          ],
          "overallNarrative": "This week was rough - Sarah being out of town hit harder than expected, and that project deadline had you working late every night. At least the raid team finally cleared Mythic - sometimes gaming friends are the best therapy."
        }
        """.trimIndent()

        fakeAIClient.defaultResponse = toughWeekResponse

        val result = synthesizer.synthesize(
            weekId = weekId,
            textEntries = textEntries,
            media = emptyList(),
            people = people,
            useCached = false
        )

        assertNotNull(result)
        assertEquals(3, result.themes.size)
        assertTrue(result.themes.contains("relationship strain"))
        assertEquals("stressful but resilient", result.emotionalTone)
        assertEquals(3, result.storyBeats.size)
        assertEquals("melancholy", result.storyBeats[0].emotionalWeight)
        assertEquals("grateful", result.storyBeats[2].emotionalWeight)
    }

    @Test
    fun synthesize_withCachedResponse_usesCachedNarrative() = runTest(testDispatcher) {
        setup()
        val weekId = "2024-W32"

        val cachedResponse = """
        {
          "themes": ["relaxation"],
          "emotionalTone": "peaceful",
          "storyBeats": [
            {
              "moment": "A quiet week",
              "context": "Taking it easy",
              "emotionalWeight": "calm",
              "evidenceIds": []
            }
          ],
          "overallNarrative": "A peaceful week of rest."
        }
        """.trimIndent()

        fakeCache.setEntry(weekId, cachedResponse)

        val result = synthesizer.synthesize(
            weekId = weekId,
            textEntries = emptyList(),
            media = emptyList(),
            people = emptyList(),
            useCached = true
        )

        assertNotNull(result)
        assertEquals(listOf("relaxation"), result.themes)
        assertEquals("peaceful", result.emotionalTone)

        // Verify AI client was not called (cached response used)
        assertEquals(0, fakeAIClient.submissions.size)
    }

    @Test
    fun synthesize_withNoContent_handlesGracefully() = runTest(testDispatcher) {
        setup()
        val weekId = "2024-W33"

        val emptyWeekResponse = """
        {
          "themes": ["quiet"],
          "emotionalTone": "unremarkable",
          "storyBeats": [],
          "overallNarrative": "Not much happened this week."
        }
        """.trimIndent()

        fakeAIClient.defaultResponse = emptyWeekResponse

        val result = synthesizer.synthesize(
            weekId = weekId,
            textEntries = emptyList(),
            media = emptyList(),
            people = emptyList(),
            useCached = false
        )

        assertNotNull(result)
        assertEquals(listOf("quiet"), result.themes)
        assertTrue(result.storyBeats.isEmpty())
    }

    @Test
    fun synthesize_withNullAIResponse_returnsNull() = runTest(testDispatcher) {
        setup()
        val weekId = "2024-W34"

        fakeAIClient.defaultResponse = null

        val result = synthesizer.synthesize(
            weekId = weekId,
            textEntries = listOf(
                JournalNote.Text(
                    uid = Uuid.random(),
                    creationTimestamp = Instant.parse("2024-08-19T10:00:00Z"),
                    lastUpdated = Instant.parse("2024-08-19T10:00:00Z"),
                    content = "Test entry"
                )
            ),
            media = emptyList(),
            people = emptyList(),
            useCached = false
        )

        assertNull(result)
    }

    @Test
    fun synthesize_withInvalidJSON_returnsNull() = runTest(testDispatcher) {
        setup()
        val weekId = "2024-W35"

        fakeAIClient.defaultResponse = "This is not valid JSON"

        val result = synthesizer.synthesize(
            weekId = weekId,
            textEntries = listOf(
                JournalNote.Text(
                    uid = Uuid.random(),
                    creationTimestamp = Instant.parse("2024-08-26T10:00:00Z"),
                    lastUpdated = Instant.parse("2024-08-26T10:00:00Z"),
                    content = "Test entry"
                )
            ),
            media = emptyList(),
            people = emptyList(),
            useCached = false
        )

        assertNull(result)
    }

    @Test
    fun synthesize_includesPeopleInContentSummary() = runTest(testDispatcher) {
        setup()
        val weekId = "2024-W36"

        val people = listOf(
            Person(name = "Alice"),
            Person(name = "Bob"),
            Person(name = "Charlie")
        )

        val narrativeResponse = """
        {
          "themes": ["social"],
          "emotionalTone": "connected",
          "storyBeats": [],
          "overallNarrative": "A social week with friends."
        }
        """.trimIndent()

        fakeAIClient.defaultResponse = narrativeResponse

        synthesizer.synthesize(
            weekId = weekId,
            textEntries = emptyList(),
            media = emptyList(),
            people = people,
            useCached = false
        )

        val lastSubmission = fakeAIClient.getLastSubmission()
        assertNotNull(lastSubmission)

        val userMessage = lastSubmission.find { it.role == "user" }
        assertNotNull(userMessage)
        assertTrue(userMessage.content.contains("Alice"))
        assertTrue(userMessage.content.contains("Bob"))
        assertTrue(userMessage.content.contains("Charlie"))
        assertTrue(userMessage.content.contains("PEOPLE MENTIONED"))
    }

    @Test
    fun synthesize_includesMediaInContentSummary() = runTest(testDispatcher) {
        setup()
        val weekId = "2024-W37"

        val media = listOf(
            IndexedMedia.Image(
                uid = Uuid.random(),
                uri = "file:///photo1.jpg",
                timestamp = Instant.parse("2024-09-09T14:00:00Z"),
                caption = "Beautiful sunset"
            ),
            IndexedMedia.Video(
                uid = Uuid.random(),
                uri = "file:///video1.mp4",
                timestamp = Instant.parse("2024-09-10T16:00:00Z"),
                caption = "Cooking adventure",
                duration = 120.seconds
            )
        )

        val narrativeResponse = """
        {
          "themes": ["photography", "cooking"],
          "emotionalTone": "creative",
          "storyBeats": [],
          "overallNarrative": "A creative week capturing moments."
        }
        """.trimIndent()

        fakeAIClient.defaultResponse = narrativeResponse

        synthesizer.synthesize(
            weekId = weekId,
            textEntries = emptyList(),
            media = media,
            useCached = false
        )

        val lastSubmission = fakeAIClient.getLastSubmission()
        assertNotNull(lastSubmission)

        val userMessage = lastSubmission.find { it.role == "user" }
        assertNotNull(userMessage)
        assertTrue(userMessage.content.contains("Photo"))
        assertTrue(userMessage.content.contains("Beautiful sunset"))
        assertTrue(userMessage.content.contains("Video"))
        assertTrue(userMessage.content.contains("Cooking adventure"))
        assertTrue(userMessage.content.contains("MEDIA"))
    }
}
