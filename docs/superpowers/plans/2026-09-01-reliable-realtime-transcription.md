# Reliable Realtime Transcription Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make realtime on-device transcription terminal, observable, persistable, and recoverable without adding latency to partial results.

**Architecture:** Keep live PCM capture and partial recognition in process. Add typed start acknowledgement and terminalization at the engine boundary, then harden the asynchronous persistence and WorkManager recovery paths so they cannot report success while dropping an error.

**Tech Stack:** Kotlin Multiplatform, Kotlin coroutines and flows, Android AudioRecord, Sherpa-ONNX, Room, WorkManager, Koin, kotlin.test.

**Spec:** `docs/superpowers/specs/2026-09-01-reliable-realtime-transcription-design.md`

## Global Constraints

- Live partial transcription must not wait for Room or WorkManager.
- Recording continues when transcription fails.
- Every accepted session emits a terminal success or error.
- On-device recovery must work without a network connection.
- Diagnostics must not contain audio or transcript content.
- Android runtime verification uses an emulator or Gradle Managed Device only.

---

### Task 1: Typed live-session acknowledgement

**Files:**
- Modify: `client/media/src/commonMain/kotlin/app/logdate/client/media/audio/transcription/TranscriptionService.kt`
- Modify: every `TranscriptionService` implementation and fake
- Test: `client/media/src/androidHostTest/kotlin/app/logdate/client/media/audio/transcription/InstallTimeTranscriptionServiceTest.kt`

**Interfaces:**
- Produces: `sealed interface TranscriptionStartResult` with `Started`, `AlreadyRunning`, and `Failed(reason)`.
- Produces: `suspend fun startLiveTranscription(): TranscriptionStartResult`.

- [ ] Write tests asserting missing features return `Failed(NotAvailable)` and installed delegates return `Started`.
- [ ] Run the Android host test and confirm it fails against the Boolean contract.
- [ ] Change the service contract and all implementations/fakes to return typed acknowledgement.
- [ ] Run media common and Android host tests.

### Task 2: Terminalize the realtime Sherpa engine

**Files:**
- Modify: `client/feature/speechrecognition/src/main/kotlin/app/logdate/feature/speech/recognition/SherpaOnnxTranscriptionService.kt`
- Create: `client/media/src/commonMain/kotlin/app/logdate/client/media/audio/transcription/TranscriptionTerminalizer.kt`
- Test: `client/media/src/commonTest/kotlin/app/logdate/client/media/audio/transcription/TranscriptionTerminalizerTest.kt`

**Interfaces:**
- Produces: a small session terminalizer that accepts progress and exactly one terminal result.
- Consumes: `TranscriptionResult` and `TranscriptionFailure`.

- [ ] Write failing tests proving duplicate terminal events are rejected and a refinement fallback clears `isRefining`.
- [ ] Run the focused common test and confirm the expected failures.
- [ ] Add the terminalizer and use it around initialization, audio reads, stop finalization, cancellation, and refinement.
- [ ] Treat repeated non-positive AudioRecord reads as `AudioError` and missing/failed models as `NotAvailable`.
- [ ] Implement Android file transcription using `AudioDecoder` and the local recognizers.
- [ ] Run focused media tests and compile the speech feature.

### Task 3: Recording and persistence handoff

**Files:**
- Modify: `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/AndroidAudioRecordingManager.kt`
- Test: `client/media/src/androidHostTest/kotlin/app/logdate/client/media/audio/AndroidAudioRecordingManagerTest.kt`
- Modify: `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/audio/AudioViewModel.kt`
- Test: `client/feature/editor/src/commonTest/kotlin/app/logdate/feature/editor/ui/audio/AudioViewModelTest.kt`

**Interfaces:**
- Consumes: `TranscriptionStartResult`.
- Produces: immediate realtime result forwarding plus bounded asynchronous persistence retry.

- [ ] Write tests proving recording stays active when transcription start fails and the UI receives the typed error.
- [ ] Write tests proving a failed repository write is retried and exhaustion emits `PersistenceError`.
- [ ] Run the focused tests and confirm they fail for the missing behavior.
- [ ] Await live start acknowledgement, make binding failure observable, check every repository result, and retry attachment without blocking partial results.
- [ ] Run media and editor tests.

### Task 4: Durable recovery scheduling and worker truthfulness

**Files:**
- Modify: `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/transcription/AndroidTranscriptionManager.kt`
- Modify: `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/transcription/TranscriptionWorker.kt`
- Create: `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/transcription/TranscriptionWorkRunner.kt`
- Test: `client/media/src/androidHostTest/kotlin/app/logdate/client/media/audio/transcription/TranscriptionWorkRunnerTest.kt`
- Test: `client/media/src/androidHostTest/kotlin/app/logdate/client/media/audio/transcription/AndroidTranscriptionManagerTest.kt`

**Interfaces:**
- Produces: offline-capable, tagged unique work whose enqueue/cancel operation is awaited.
- Produces: a runner that reports success only after the terminal repository write succeeds.

- [ ] Write failing tests for request constraints/tagging and repository-update failure outcomes.
- [ ] Run focused Android host tests and confirm expected failures.
- [ ] Remove the network constraint, add the shared tag, await WorkManager operations, and delegate worker execution to the testable runner.
- [ ] Persist `FAILED` on terminal exceptions and return retry when a required status write fails.
- [ ] Run focused Android host tests.

### Task 5: Repository stale-state and enqueue handling

**Files:**
- Modify: `client/data/src/commonMain/kotlin/app/logdate/client/data/transcription/OfflineFirstTranscriptionRepository.kt`
- Create: `client/data/src/commonTest/kotlin/app/logdate/client/data/transcription/OfflineFirstTranscriptionRepositoryTest.kt`

**Interfaces:**
- Consumes: `TranscriptionManager.enqueueTranscription` result.
- Produces: failed scheduling becomes durable `FAILED`; stale `IN_PROGRESS` is requeued.

- [ ] Write failing tests for enqueue rejection, update row-count failure, and stale in-progress recovery.
- [ ] Run the focused data test and confirm expected failures.
- [ ] Check enqueue and DAO update results, distinguish absent notes from DAO exceptions, and requeue stale active rows.
- [ ] Run focused data tests.

### Task 6: End-to-end verification

**Files:**
- Modify only files needed to address verification findings.

**Interfaces:**
- Consumes: all preceding behavior.
- Produces: release evidence for the user-visible path.

- [ ] Run focused module tests for media, data, editor, and speech recognition.
- [ ] Run Kotlin lint and the repository `./run check` gate.
- [ ] Build the minified release bundle and run the bundle verifier.
- [ ] Verify no physical Android device is targeted, then run the existing speech-recognition microphone smoke test on an emulator or Gradle Managed Device.
- [ ] Inspect the final diff for unrelated changes and report any remaining external blocker precisely.
