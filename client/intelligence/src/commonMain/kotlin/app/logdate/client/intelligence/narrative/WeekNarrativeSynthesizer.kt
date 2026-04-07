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
import app.logdate.client.intelligence.generativeai.GenerativeAIResponseFormat
import app.logdate.client.intelligence.structured.JsonStructuredOutputParser
import app.logdate.client.intelligence.structured.StructuredOutputResult
import app.logdate.client.intelligence.unavailableReason
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.media.IndexedMedia
import app.logdate.shared.model.HighlightedQuote
import app.logdate.shared.model.Person
import app.logdate.shared.model.ReflectionPrompt
import app.logdate.shared.model.StoryBeat
import app.logdate.shared.model.WeekNarrative
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
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
    private val dataUsagePolicy: DataUsagePolicy,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
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
5. Reflection prompts (2-3 noticings; see the Reflection Prompts section below)
6. Highlighted quotes (2-4 verbatim lines; see the Highlighted Quotes section below)

Guidelines:
- Speak in second person, past tense ("You explored...", "You felt...")
- Be AUTHENTIC and SPECIFIC - use actual details from entries (people names, places, events)
- Focus on MEANING and CONTEXT, not just events
- Identify emotional weight and connections between moments
- Tell what the week was ABOUT, not just what happened

For story beats, identify moments that matter and WHY they matter.

=== Reflection Prompts ===

Reflection prompts are NOT generic wellness questions. Do NOT produce things like:
  - "What are you grateful for this week?"
  - "How have you grown?"
  - "What did you learn?"
  - "Take a moment to reflect on your week."

Those are exactly what every other journaling app already ships and they make the
experience feel canned. Refuse them.

Instead, invent 2–3 noticings that ONLY this user's week could have produced. Each
prompt has two parts:

  - "observation": a short, factual line drawn from the actual entries — a count, a
     pattern, a recurrence, a contrast, a specific moment. Past tense, second person.
     Examples: "Sarah came up in five entries this week, the last on Friday at the
     bookshop." / "You spent four nights writing past midnight." / "The rain showed
     up three times in your entries — Tuesday, Wednesday, Saturday."

  - "invitation": one open-ended question that the observation naturally leads into.
     Specific. Not a wellness platitude. Examples: "What was different about
     Wednesday's storm?" / "Was that on purpose, or did the week run away from you?"
     / "Which one of those mornings is the one you actually want to remember?"

Rules:
  - Never use the words "grateful", "growth", "lessons", "intention", "manifest".
  - Never start an invitation with "How" alone — use "What", "Which", "When", "Why
    do you think", or a direct second-person question.
  - The observation must reference real content from the week. If you can't ground it
    in a specific person, place, count, or quote from the entries, don't write the
    prompt.
  - If the week is too thin to invent a noticing, return an empty reflectionPrompts
    array. Don't pad with generic questions.

=== Highlighted Quotes ===

Pick 2–4 sentences from the actual journal entries that the rewind should hand back
to the user as a "you wrote this and it landed" moment. These are NOT summaries, they
are NOT paraphrases, they are NOT story beats. They are verbatim lines copied straight
out of the entries the user typed. Spotify Wrapped works because it shows you your own
listening — this is the journaling equivalent.

Each quote has three parts:

  - "text": the exact sentence as the user wrote it. Character-for-character. Do not
     edit. Do not "improve". Do not stitch sentences together. Do not fix typos. Do not
     trim. If the original ends mid-thought, the quote ends mid-thought. If you find
     yourself wanting to clean it up, pick a different sentence instead.

  - "whyItHits": one short line, second person, explaining why the rewind is surfacing
     THIS line out of the entry. Examples: "The first time you said it out loud."
     / "Your way of admitting it was harder than you'd planned to admit." / "The line
     that turned a vent into a decision."

  - "sourceEntryId": the ID of the entry the quote came from, exactly as it appears in
     the entry's "(ID: ...)" header. Required so the UI can deep-link back.

Rules:
  - text must be a substring of an actual entry. If you cannot find the line verbatim
    in the input, do not include the quote.
  - Pick lines that are SHARP, not lines that are pretty. The line where the user said
    something true to themselves. The line that contradicts what they were trying to
    say. The line that surprised them while they were writing it.
  - Do not pick lines that are ALREADY the obvious thesis of the entry. Pick the
    sentence buried in the middle that the user might not realize they wrote.
  - If the week is too thin or no line stands out, return an empty highlightedQuotes
    array. Better to surface nothing than to surface something flat.

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
  "overallNarrative": "You explored the California coast, finally finding that hidden beach you'd been searching for. Evenings were dedicated to the taco truck tour you'd been planning. Sometimes the best trips are the ones you've dreamed about.",
  "reflectionPrompts": [
    {
      "observation": "Five different beaches showed up across your entries this week.",
      "invitation": "Which one is the one you'd drag a friend back to first?"
    },
    {
      "observation": "You wrote about the taco truck three nights in a row before the tour even had a name.",
      "invitation": "What made night one feel like the start of something instead of just dinner?"
    }
  ],
  "highlightedQuotes": [
    {
      "text": "I forgot how much I missed driving with the windows down and no plan.",
      "whyItHits": "Your first admission this trip that the freedom was the point.",
      "sourceEntryId": "entry-coast-drive"
    }
  ]
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
  "overallNarrative": "This week was rough - Sarah being out of town hit harder than expected, and that project deadline had you working late every night. At least the raid team finally cleared Mythic - sometimes gaming friends are the best therapy.",
  "reflectionPrompts": [
    {
      "observation": "You mentioned Sarah in four entries this week, twice while she was traveling and twice the day she got back.",
      "invitation": "Was the relief about her being home, or about not having to write the lonely entries anymore?"
    },
    {
      "observation": "Three of your late nights ended with raid screenshots before they ended with sleep.",
      "invitation": "When the deadline lifts, do those nights become a habit you keep or a thing you only needed for now?"
    }
  ],
  "highlightedQuotes": [
    {
      "text": "I am not okay this week and I am tired of pretending the deadline is the reason.",
      "whyItHits": "The line where you stopped blaming the project.",
      "sourceEntryId": "entry-late-work"
    }
  ]
}

Respond ONLY with valid JSON in this format. No additional text."""

        private const val PROMPT_VERSION = "narrative-v3-quotes"
        private const val SCHEMA_VERSION = "week-narrative-json-v3"
        private const val TEMPLATE_ID = "week-narrative"
        private const val CACHE_TTL_SECONDS = 60L * 60L * 24L * 30L

        private const val RESPONSE_SCHEMA = """
{
  "type": "object",
  "properties": {
    "themes": {
      "type": "array",
      "items": { "type": "string" }
    },
    "emotionalTone": { "type": "string" },
    "storyBeats": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "moment": { "type": "string" },
          "context": { "type": "string" },
          "emotionalWeight": { "type": "string" },
          "evidenceIds": {
            "type": "array",
            "items": { "type": "string" }
          }
        },
        "required": ["moment", "context", "emotionalWeight", "evidenceIds"],
        "additionalProperties": false
      }
    },
    "overallNarrative": { "type": "string" },
    "reflectionPrompts": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "observation": { "type": "string" },
          "invitation": { "type": "string" }
        },
        "required": ["observation", "invitation"],
        "additionalProperties": false
      }
    },
    "highlightedQuotes": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "text": { "type": "string" },
          "whyItHits": { "type": "string" },
          "sourceEntryId": { "type": "string" }
        },
        "required": ["text", "whyItHits", "sourceEntryId"],
        "additionalProperties": false
      }
    }
  },
  "required": ["themes", "emotionalTone", "storyBeats", "overallNarrative", "reflectionPrompts", "highlightedQuotes"],
  "additionalProperties": false
}
"""

        private val json = Json { ignoreUnknownKeys = true }
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
        useCached: Boolean = true,
    ): AIResult<WeekNarrative> =
        withContext(ioDispatcher) {
            val cacheRequest =
                GenerativeAICacheRequest(
                    contentType = GenerativeAICacheContentType.Narrative,
                    inputText = buildContentSummary(textEntries, media, people),
                    providerId = genAIClient.providerId,
                    model = genAIClient.defaultModel,
                    promptVersion = PROMPT_VERSION,
                    schemaVersion = SCHEMA_VERSION,
                    templateId = TEMPLATE_ID,
                    policy = AICachePolicy(ttlSeconds = CACHE_TTL_SECONDS),
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
            val unavailableReason = unavailableReason(networkAvailabilityMonitor, dataUsagePolicy)
            if (unavailableReason != null) {
                return@withContext AIResult.Unavailable(unavailableReason)
            }

            // Build content summary for AI
            val contentSummary = cacheRequest.inputText

            val prompt =
                """
Week's content for analysis:

$contentSummary

Analyze this content and provide the narrative structure in JSON format as specified.
""".trim()

            val response =
                genAIClient.submit(
                    GenerativeAIRequest(
                        messages =
                            listOf(
                                GenerativeAIChatMessage("system", SYSTEM_PROMPT),
                                GenerativeAIChatMessage("user", prompt),
                            ),
                        model = cacheRequest.model,
                        responseFormat =
                            GenerativeAIResponseFormat.JsonSchema(
                                name = "week_narrative",
                                schema = RESPONSE_SCHEMA,
                            ),
                    ),
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
        people: List<Person>,
    ): String {
        val summary =
            buildString {
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
        val parser =
            JsonStructuredOutputParser(
                json = json,
                serializer = NarrativeResponse.serializer(),
                allowEmbeddedJson = true,
            )
        return when (val result = parser.parse(response)) {
            is StructuredOutputResult.Success -> {
                val parsed = result.value
                WeekNarrative(
                    themes = parsed.themes,
                    emotionalTone = parsed.emotionalTone,
                    storyBeats =
                        parsed.storyBeats.map {
                            StoryBeat(
                                moment = it.moment,
                                context = it.context,
                                emotionalWeight = it.emotionalWeight,
                                evidenceIds = it.evidenceIds,
                            )
                        },
                    overallNarrative = parsed.overallNarrative,
                    reflectionPrompts =
                        parsed.reflectionPrompts.map {
                            ReflectionPrompt(observation = it.observation, invitation = it.invitation)
                        },
                    highlightedQuotes =
                        parsed.highlightedQuotes.map {
                            HighlightedQuote(
                                text = it.text,
                                whyItHits = it.whyItHits,
                                sourceEntryId = it.sourceEntryId,
                            )
                        },
                )
            }
            StructuredOutputResult.Empty -> {
                Napier.w("Narrative response was empty")
                null
            }
            StructuredOutputResult.Invalid -> {
                Napier.w("Narrative response did not match schema")
                null
            }
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
        val overallNarrative: String,
        val reflectionPrompts: List<ReflectionPromptResponse> = emptyList(),
        val highlightedQuotes: List<HighlightedQuoteResponse> = emptyList(),
    )

    @Serializable
    private data class StoryBeatResponse(
        val moment: String,
        val context: String,
        val emotionalWeight: String,
        val evidenceIds: List<String>,
    )

    @Serializable
    private data class ReflectionPromptResponse(
        val observation: String,
        val invitation: String,
    )

    @Serializable
    private data class HighlightedQuoteResponse(
        val text: String,
        val whyItHits: String,
        val sourceEntryId: String,
    )
}
