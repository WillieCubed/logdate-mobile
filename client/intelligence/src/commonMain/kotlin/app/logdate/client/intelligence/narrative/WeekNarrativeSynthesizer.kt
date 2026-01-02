package app.logdate.client.intelligence.narrative

import app.logdate.client.intelligence.AIError
import app.logdate.client.intelligence.AIResult
import app.logdate.client.intelligence.cache.AICachePolicy
import app.logdate.client.intelligence.cache.GenerativeAICache
import app.logdate.client.intelligence.cache.GenerativeAICacheContentType
import app.logdate.client.intelligence.cache.GenerativeAICacheRequest
import app.logdate.client.intelligence.generativeai.GenerativeAIChatClient
import app.logdate.client.intelligence.generativeai.GenerativeAIChatMessage
import app.logdate.client.intelligence.generativeai.GenerativeAIRequest
import app.logdate.client.intelligence.unavailableReason
import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.media.IndexedMedia
import app.logdate.shared.model.Person
import app.logdate.shared.model.StoryBeat
import app.logdate.shared.model.WeekNarrative
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Synthesizes a narrative understanding of a week's journal content using AI.
 *
 * This is the core of narrative intelligence - it understands what the week was ABOUT,
 * not just what content exists. It identifies themes, emotional tone, and key story beats
 * that define the week's narrative.
 *
 * Example output for a vacation week:
 * - Themes: ["vacation", "exploration", "culinary adventure"]
 * - Tone: "excited and refreshed"
 * - Beats: "Found hidden beach", "Taco truck tour"
 * - Narrative: "You explored the coast, found that beach, tried every taco truck"
 */
class WeekNarrativeSynthesizer(
    private val generativeAICache: GenerativeAICache,
    private val genAIClient: GenerativeAIChatClient,
    private val networkAvailabilityMonitor: NetworkAvailabilityMonitor,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private companion object {
        private const val SYSTEM_PROMPT = """
You are a narrative intelligence system that understands life stories from journal entries.

Analyze the provided week's content and identify the STORY of the week.

Provide:
1. Main themes (3-5 themes that characterized the week)
2. Emotional tone (how the week felt overall in 2-4 words)
3. Story beats (4-7 key moments that define the narrative)
4. Overall narrative (2-3 sentence story of the week)

Guidelines:
- Speak in second person, past tense ("You explored...", "You felt...")
- Be AUTHENTIC and SPECIFIC - use actual details from entries (people names, places, events)
- Focus on MEANING and CONTEXT, not just events
- Identify emotional weight and connections between moments
- Tell what the week was ABOUT, not just what happened

For story beats, identify moments that matter and WHY they matter.

Example for vacation:
{
  "themes": ["vacation", "exploration", "culinary adventure"],
  "emotionalTone": "excited and refreshed",
  "storyBeats": [
    {
      "moment": "Finally found the hidden beach at Point Reyes",
      "context": "Long-anticipated goal you'd been searching for",
      "emotionalWeight": "triumphant",
      "evidenceIds": ["entry-beach-discovery", "photo-sunset"]
    },
    {
      "moment": "Taco truck tour became the evening ritual",
      "context": "Spontaneous tradition that defined the trip",
      "emotionalWeight": "joyful",
      "evidenceIds": ["entry-tacos", "photo-truck"]
    }
  ],
  "overallNarrative": "You explored the California coast, finally finding that hidden beach you'd been searching for. Evenings were dedicated to the taco truck tour you'd been planning. Sometimes the best trips are the ones you've dreamed about."
}

Example for tough week:
{
  "themes": ["relationship strain", "work pressure", "gaming as therapy"],
  "emotionalTone": "stressful but resilient",
  "storyBeats": [
    {
      "moment": "Sarah being out of town hit harder than expected",
      "context": "Unexpected loneliness highlighting relationship importance",
      "emotionalWeight": "melancholy",
      "evidenceIds": ["entry-miss-sarah"]
    },
    {
      "moment": "Project deadline had you working late every night",
      "context": "Work stress compounding personal challenges",
      "emotionalWeight": "exhausted",
      "evidenceIds": ["entry-late-work", "entry-deadline"]
    },
    {
      "moment": "Raid team finally cleared Mythic",
      "context": "Gaming friends providing support and victory during tough time",
      "emotionalWeight": "grateful",
      "evidenceIds": ["screenshot-raid", "entry-gaming"]
    }
  ],
  "overallNarrative": "This week was rough - Sarah being out of town hit harder than expected, and that project deadline had you working late every night. At least the raid team finally cleared Mythic - sometimes gaming friends are the best therapy."
}

Respond ONLY with valid JSON in this format. No additional text."""

        private const val PROMPT_VERSION = "narrative-v1"
        private const val SCHEMA_VERSION = "week-narrative-v1"
        private const val TEMPLATE_ID = "week-narrative"
        private const val CACHE_TTL_SECONDS = 60L * 60L * 24L * 30L

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }

    /**
     * Synthesizes a narrative from a week's worth of content.
     *
     * @param weekId Unique identifier for log correlation (e.g., "2024-W42")
     * @param textEntries Journal text entries from the week
     * @param media Photos and videos from the week
     * @param people People mentioned across entries (from PeopleExtractor)
     * @param useCached Whether to use cached narrative if available
     * @return WeekNarrative capturing the story, or null if synthesis fails
     */
    suspend fun synthesize(
        weekId: String,
        textEntries: List<JournalNote.Text>,
        media: List<IndexedMedia>,
        people: List<Person> = emptyList(),
        useCached: Boolean = true
    ): AIResult<WeekNarrative> = withContext(ioDispatcher) {
        val cacheRequest = GenerativeAICacheRequest(
            contentType = GenerativeAICacheContentType.Narrative,
            inputText = buildContentSummary(textEntries, media, people),
            providerId = genAIClient.providerId,
            model = genAIClient.defaultModel,
            promptVersion = PROMPT_VERSION,
            schemaVersion = SCHEMA_VERSION,
            templateId = TEMPLATE_ID,
            policy = AICachePolicy(ttlSeconds = CACHE_TTL_SECONDS)
        )
        if (useCached) {
            val cached = generativeAICache.getEntry(cacheRequest)
            if (cached != null) {
                Napier.d("Using cached narrative for $weekId")
                val parsed = parseNarrativeResponse(cached.content)
                if (parsed != null) {
                    return@withContext AIResult.Success(parsed, fromCache = true)
                }
                Napier.w("Cached narrative response was invalid for $weekId")
            }
        }

        Napier.d("Generating narrative for $weekId with ${textEntries.size} entries, ${media.size} media items")
        val unavailableReason = networkAvailabilityMonitor.unavailableReason()
        if (unavailableReason != null) {
            return@withContext AIResult.Unavailable(unavailableReason)
        }

        // Build content summary for AI
        val contentSummary = cacheRequest.inputText

        val prompt = """
Week's content for analysis:

$contentSummary

Analyze this content and provide the narrative structure in JSON format as specified.
""".trim()

        val response = genAIClient.submit(
            GenerativeAIRequest(
                messages = listOf(
                    GenerativeAIChatMessage("system", SYSTEM_PROMPT),
                    GenerativeAIChatMessage("user", prompt)
                ),
                model = cacheRequest.model
            )
        )

        return@withContext when (response) {
            is AIResult.Success -> {
                val content = response.value.content
                Napier.d("Caching narrative for $weekId")
                generativeAICache.putEntry(cacheRequest, content)
                val parsed = parseNarrativeResponse(content)
                if (parsed != null) {
                    AIResult.Success(parsed, fromCache = false)
                } else {
                    AIResult.Error(AIError.InvalidResponse)
                }
            }
            is AIResult.Unavailable -> response
            is AIResult.Error -> {
                Napier.e("Failed to synthesize narrative", throwable = response.throwable)
                response
            }
        }
    }

    /**
     * Builds a textual summary of the week's content for AI analysis.
     */
    private fun buildContentSummary(
        textEntries: List<JournalNote.Text>,
        media: List<IndexedMedia>,
        people: List<Person>
    ): String {
        val summary = buildString {
            appendLine("=== TEXT ENTRIES ===")
            textEntries.forEach { entry ->
                appendLine("\n[${entry.creationTimestamp}] (ID: ${entry.uid})")
                appendLine(entry.content.take(500)) // Limit to avoid token overflow
                if (entry.content.length > 500) appendLine("... [truncated]")
            }

            appendLine("\n=== MEDIA ===")
            media.forEach { item ->
                when (item) {
                    is IndexedMedia.Image -> {
                        appendLine("\n[${item.timestamp}] Photo (ID: ${item.uid})")
                        item.caption?.let { appendLine("Caption: $it") }
                    }
                    is IndexedMedia.Video -> {
                        appendLine("\n[${item.timestamp}] Video (ID: ${item.uid}) - Duration: ${item.duration}")
                        item.caption?.let { appendLine("Caption: $it") }
                    }
                }
            }

            if (people.isNotEmpty()) {
                appendLine("\n=== PEOPLE MENTIONED ===")
                appendLine(people.joinToString(", ") { it.name })
            }

            appendLine("\n=== SUMMARY STATS ===")
            appendLine("Total entries: ${textEntries.size}")
            appendLine("Total media: ${media.size}")
            appendLine("People mentioned: ${people.size}")
        }

        return summary
    }

    /**
     * Parses AI response into WeekNarrative structure.
     */
    private fun parseNarrativeResponse(response: String): WeekNarrative? {
        return try {
            // Extract JSON from response (AI might include extra text)
            val jsonStart = response.indexOf('{')
            val jsonEnd = response.lastIndexOf('}') + 1
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonString = response.substring(jsonStart, jsonEnd)
                val parsed = json.decodeFromString<NarrativeResponse>(jsonString)

                WeekNarrative(
                    themes = parsed.themes,
                    emotionalTone = parsed.emotionalTone,
                    storyBeats = parsed.storyBeats.map {
                        StoryBeat(
                            moment = it.moment,
                            context = it.context,
                            emotionalWeight = it.emotionalWeight,
                            evidenceIds = it.evidenceIds
                        )
                    },
                    overallNarrative = parsed.overallNarrative
                )
            } else {
                Napier.w("Could not find JSON in AI response")
                null
            }
        } catch (e: Exception) {
            Napier.e("Failed to parse narrative response", throwable = e)
            null
        }
    }

    /**
     * Internal data class for JSON parsing.
     */
    @Serializable
    private data class NarrativeResponse(
        val themes: List<String>,
        val emotionalTone: String,
        val storyBeats: List<StoryBeatResponse>,
        val overallNarrative: String
    )

    @Serializable
    private data class StoryBeatResponse(
        val moment: String,
        val context: String,
        val emotionalWeight: String,
        val evidenceIds: List<String>
    )
}
