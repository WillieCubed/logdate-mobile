# Reliable Realtime Transcription Design

## Goal

On-device transcription must provide realtime partial text without allowing an accepted session, refinement pass, persistence write, or recovery job to fail silently.

## Product contract

- Recording remains available if transcription fails. Audio capture is the primary user asset and must not be discarded because speech recognition is unhealthy.
- Live partial text reaches the editor directly from the in-process recognizer. Database and WorkManager operations never sit in the audio-read or partial-result hot path.
- Every live start returns a typed acknowledgement. A rejected start emits the same typed failure through the result stream.
- Every accepted live session reaches a terminal `Success` or `Error`. Cancellation is explicit and cannot leave an in-progress result replayed.
- Refinement is optional. If it cannot run or cannot improve an utterance, the last streaming transcript is re-emitted with `isRefining = false`.
- A transcript that cannot yet be attached to its note is retried. Exhausted persistence retries produce a visible typed error while retaining the audio for later recovery.
- File transcription is available on Android so a saved recording can be retried without network access.

## Architecture

`TranscriptionService` remains the platform boundary and its replaying result flow remains the realtime UI feed. `startLiveTranscription` returns a typed `TranscriptionStartResult` instead of a Boolean. The Android recording manager awaits only this acknowledgement; recognition continues on the engine-owned coroutine and streams partial results as before.

The Sherpa engine owns one supervised live-session job. Initialization, audio reads, VAD, decode, stop finalization, and refinement translate exceptions into typed results. Repeated failed audio reads terminate the session instead of spinning. Cleanup is idempotent and terminal emission happens before resources are released.

The repository remains the durable source for saved-note transcription status. WorkManager is recovery orchestration only: local work has no network constraint, enqueue and cancel operations are awaited, requests are tagged consistently, and the worker reports success only after the terminal database write succeeds. Stale in-progress rows are requeued instead of being treated as healthy forever.

## Error handling and observability

Failures use stable domain reasons for model initialization, audio capture, no speech, persistence, scheduling, and unexpected faults. Napier records the throwable once at the boundary where it becomes a terminal failure. Logs include the note ID or session stage but never audio samples or transcript contents.

The editor continues displaying recording controls and any transcript already produced. A terminal transcription error replaces the indefinite progress state and exposes the existing retry action.

## Verification

- Unit tests cover typed start acknowledgement, missing install-time feature behavior, persistence retry exhaustion, stale-row recovery, worker write failures, and refinement terminalization helpers.
- Android host tests verify WorkManager requests are offline-capable and tagged.
- Speech-recognition instrumentation verifies model/JNI loading and microphone start/stop on an emulator.
- The full repository check and minified release bundle verification remain required release gates.

## Non-goals

- No cloud transcription dependency.
- No database write per partial token.
- No transcript or raw-audio content in diagnostics.
- No physical-device installation or testing by agents.
