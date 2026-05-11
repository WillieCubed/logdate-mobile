package app.logdate.client.media.audio.transcription

import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.repository.transcription.TranscriptDocumentStatus
import app.logdate.client.repository.transcription.TranscriptSegment
import app.logdate.client.repository.transcription.TranscriptSource
import app.logdate.client.repository.transcription.TranscriptWord

/**
 * Converts engine-level timed output into the repository's durable transcript document.
 *
 * The mapper gives every utterance a stable positional segment id so later local/cloud refinement
 * passes can replace matching utterances without losing timestamps or search indexability.
 */
fun TimedTranscript.toTranscriptDocument(
    status: TranscriptDocumentStatus,
    source: TranscriptSource,
    language: String = "en-US",
): TranscriptDocument =
    TranscriptDocument(
        status = status,
        language = language,
        segments =
            utterances.mapIndexed { index, utterance ->
                TranscriptSegment(
                    segmentId = "utt-$index",
                    text = utterance.text,
                    startMs = utterance.startMs,
                    endMs = utterance.endMs,
                    words =
                        utterance.words.map { word ->
                            TranscriptWord(
                                text = word.text,
                                normalizedText = word.normalizedText,
                                startMs = word.startMs,
                                endMs = word.endMs,
                            )
                        },
                    source = source,
                    isFinal = status == TranscriptDocumentStatus.FINAL,
                )
            },
    )
