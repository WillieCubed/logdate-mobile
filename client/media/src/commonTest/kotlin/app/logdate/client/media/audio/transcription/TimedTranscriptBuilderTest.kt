package app.logdate.client.media.audio.transcription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TimedTranscriptBuilderTest {
    @Test
    fun buildUtteranceGroupsSentencePieceTokensIntoWords() {
        val utterance =
            assertNotNull(
                TimedTranscriptBuilder.buildUtterance(
                    text = "hello world",
                    utteranceStartMs = 1200,
                    utteranceConsumedMs = 900,
                    tokens = listOf("▁hello", "▁wor", "ld"),
                    timestampsSeconds = listOf(0.0f, 0.42f, 0.61f),
                ),
            )

        assertEquals("hello world", utterance.text)
        assertEquals(2, utterance.words.size)
        assertEquals("hello", utterance.words[0].text)
        assertEquals("world", utterance.words[1].text)
        assertEquals(1200, utterance.startMs)
        assertEquals(1620, utterance.words[1].startMs)
    }

    @Test
    fun buildUtteranceSkipsPunctuationTokensInSearchableWordList() {
        val utterance =
            assertNotNull(
                TimedTranscriptBuilder.buildUtterance(
                    text = "hello, world!",
                    utteranceStartMs = 0,
                    utteranceConsumedMs = 1200,
                    tokens = listOf("▁hello", ",", "▁world", "!"),
                    timestampsSeconds = listOf(0.0f, 0.30f, 0.55f, 0.92f),
                ),
            )

        assertEquals(listOf("hello", "world"), utterance.words.map { it.normalizedText })
        assertEquals("hello, world!", utterance.text)
    }
}
